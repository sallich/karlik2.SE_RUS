#!/usr/bin/env bash
# Eval against running Docker stack (step-agent, policy-agent).
# Prerequisites:
#   docker compose --profile policy-agent --profile llm up -d
set -euo pipefail

RUNS="${EVAL_RUNS:-5}"
SEEDS="${EVAL_SEEDS:-41,42,43,44,45}"
MAX_STEPS_STEP="${EVAL_MAX_STEPS_STEP:-500}"
MAX_STEPS_POLICY="${EVAL_MAX_STEPS_POLICY:-2000}"
STEP_PORT="${HOST_PORT_AGENT_RUNNER:-18082}"
POLICY_PORT="${HOST_PORT_POLICY_AGENT_RUNNER:-8083}"
STEP_URL="${STEP_AGENT_URL:-http://localhost:${STEP_PORT}}"
POLICY_URL="${POLICY_AGENT_URL:-http://localhost:${POLICY_PORT}}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -f "$ROOT/.env" ]; then
  set -a
  # shellcheck disable=SC1091
  source "$ROOT/.env"
  set +a
fi
LOG_DIR="$ROOT/eval/logs"
STAMP="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
LOG_FILE="$LOG_DIR/docker-$STAMP.jsonl"

mkdir -p "$LOG_DIR"

wait_health() {
  local url="$1"
  local name="$2"
  for _ in $(seq 1 60); do
    if curl -sf "$url/health" >/dev/null 2>&1; then
      echo "OK: $name"
      return 0
    fi
    sleep 2
  done
  echo "ERROR: $name not healthy at $url" >&2
  exit 1
}

wait_health "$STEP_URL" "step-agent"
wait_health "$POLICY_URL" "policy-agent"

IFS=',' read -ra SEED_ARR <<< "$SEEDS"

run_agent() {
  local agent_name="$1"
  local endpoint="$2"
  local seed="$3"
  local run="$4"
  local max_steps="$5"
  local started ended body
  started=$(date +%s%3N)
  body=$(curl -sf -X POST "$endpoint" \
    -H "Content-Type: application/json" \
    -d "{\"seed\":$seed,\"maxSteps\":$max_steps}")
  ended=$(date +%s%3N)
  printf '%s' "$body" | python3 -c "
import json, sys
body = json.load(sys.stdin)
record = {
    'agent': sys.argv[1],
    'seed': int(sys.argv[2]),
    'run': int(sys.argv[3]),
    'success': body.get('success'),
    'steps': body.get('stepsUsed'),
    'tokens': body.get('tokensUsed') or body.get('policyTokensApprox') or 0,
    'finalHp': body.get('finalHp'),
    'finalPhase': body.get('finalPhase'),
    'durationMs': int(sys.argv[4]),
    'llmProvider': sys.argv[5],
}
print(json.dumps(record, separators=(',', ':')))
" "$agent_name" "$seed" "$run" "$((ended - started))" "docker" >>"$LOG_FILE"
  local ok steps
  read -r ok steps <<<"$(
    printf '%s' "$body" | python3 -c "import json,sys; b=json.load(sys.stdin); print(b.get('success'), b.get('stepsUsed'))"
  )"
  echo "  $agent_name seed=$seed run=$run success=$ok steps=$steps"
}

echo "Eval: runs=$RUNS seeds=$SEEDS step=$STEP_URL policy=$POLICY_URL"
: >"$LOG_FILE"

for seed in "${SEED_ARR[@]}"; do
  seed="$(echo "$seed" | tr -d ' ')"
  for run in $(seq 1 "$RUNS"); do
    run_agent "step-agent" "$STEP_URL/api/v1/agent/run" "$seed" "$run" "$MAX_STEPS_STEP"
    run_agent "policy-agent" "$POLICY_URL/api/v1/policy-agent/run" "$seed" "$run" "$MAX_STEPS_POLICY"
  done
done

./gradlew -q :eval:run --args="--from-log $LOG_FILE"
echo "Report: $ROOT/eval/results.md"
