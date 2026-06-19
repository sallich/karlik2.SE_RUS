#!/usr/bin/env bash
# Eval policy-agent only (Ollama / heuristic via Docker).
# Step-agent eval — отдельно (коллега / run-eval.sh).
#
# Prerequisites:
#   docker compose --profile policy-agent --profile llm up -d
#   Ollama на хосте или в compose; в .env: OLLAMA_BASE_URL, OLLAMA_MODEL
#   python3, curl
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi

RUNS="${EVAL_RUNS:-5}"
SEEDS="${EVAL_SEEDS:-41,42,43,44,45}"
MAX_STEPS_POLICY="${EVAL_MAX_STEPS_POLICY:-5000}"
POLICY_PORT="${HOST_PORT_POLICY_AGENT_RUNNER:-8083}"
POLICY_URL="${POLICY_AGENT_URL:-http://localhost:${POLICY_PORT}}"
LOG_DIR="$ROOT/eval/logs"
STAMP="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
LOG_FILE="$LOG_DIR/policy-${STAMP}.jsonl"

mkdir -p "$LOG_DIR"

wait_health() {
  local url="$1"
  local name="$2"
  for _ in $(seq 1 90); do
    if curl -sf "$url/health" >/dev/null 2>&1; then
      echo "OK: $name"
      return 0
    fi
    sleep 2
  done
  echo "ERROR: $name not healthy at $url" >&2
  exit 1
}

wait_health "$POLICY_URL" "policy-agent"

HEALTH_JSON=$(curl -sf "$POLICY_URL/health")
read -r LLM_PROVIDER OLLAMA_MODEL REACHABLE MODEL_OK <<<"$(
  printf '%s' "$HEALTH_JSON" | python3 -c "
import json, sys
d = json.load(sys.stdin)
llm = d.get('llm') or {}
print(
    d.get('llmProvider') or 'ollama',
    d.get('ollamaModel') or '',
    str(llm.get('reachable', False)).lower(),
    str(llm.get('modelAvailable', False)).lower(),
)
"
)"

if [ "$LLM_PROVIDER" = "ollama" ]; then
  if [ "$REACHABLE" != "true" ] || [ "$MODEL_OK" != "true" ]; then
    echo "ERROR: policy-agent Ollama not ready (reachable=$REACHABLE model=$MODEL_OK)" >&2
    printf '%s' "$HEALTH_JSON" | python3 -c "import json,sys; print(json.dumps(json.load(sys.stdin).get('llm'), indent=2))" >&2
    exit 1
  fi
  echo "LLM: ollama model=${OLLAMA_MODEL:-unknown}"
elif [ "$LLM_PROVIDER" = "heuristic" ]; then
  echo "WARN: policy-agent in heuristic mode — для ТЗ нужен ollama (POLICY_LLM_PROVIDER=ollama)" >&2
else
  echo "LLM provider: $LLM_PROVIDER"
fi

IFS=',' read -ra SEED_ARR <<< "$SEEDS"

run_policy() {
  local seed="$1"
  local run="$2"
  local started ended body
  started=$(date +%s%3N)
  body=$(curl -sf -X POST "$POLICY_URL/api/v1/policy-agent/run" \
    -H "Content-Type: application/json" \
    -d "{\"seed\":$seed,\"maxSteps\":$MAX_STEPS_POLICY}")
  ended=$(date +%s%3N)
  printf '%s' "$body" | python3 -c "
import json, sys
body = json.load(sys.stdin)
record = {
    'agent': 'policy-agent',
    'seed': int(sys.argv[1]),
    'run': int(sys.argv[2]),
    'success': body.get('success'),
    'steps': body.get('stepsUsed'),
    'tokens': body.get('policyTokensApprox') or body.get('tokensUsed') or 0,
    'finalHp': body.get('finalHp'),
    'finalPhase': body.get('finalPhase'),
    'durationMs': int(sys.argv[3]),
    'llmProvider': sys.argv[4],
}
print(json.dumps(record, separators=(',', ':')))
" "$seed" "$run" "$((ended - started))" "$LLM_PROVIDER" >>"$LOG_FILE"
  local ok steps tokens
  read -r ok steps tokens <<<"$(
    printf '%s' "$body" | python3 -c "
import json, sys
b = json.load(sys.stdin)
print(b.get('success'), b.get('stepsUsed'), b.get('policyTokensApprox') or b.get('tokensUsed') or 0)
"
  )"
  echo "  policy-agent seed=$seed run=$run success=$ok steps=$steps tokens=$tokens"
}

echo "Policy eval: runs=$RUNS seeds=$SEEDS maxSteps=$MAX_STEPS_POLICY url=$POLICY_URL"
: >"$LOG_FILE"

for seed in "${SEED_ARR[@]}"; do
  seed="$(echo "$seed" | tr -d ' ')"
  for run in $(seq 1 "$RUNS"); do
    run_policy "$seed" "$run"
  done
done

cd "$ROOT"
./gradlew -q :eval:run --args="--from-log $LOG_FILE"
echo "Log: $LOG_FILE"
echo "Report: $ROOT/eval/results.md"
