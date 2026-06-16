#!/usr/bin/env bash
# Quick Ollama connectivity check (host or from agent-runner container context).
set -euo pipefail

BASE_URL="${1:-${OLLAMA_BASE_URL:-http://localhost:11434}}"
BASE_URL="${BASE_URL%/}"

echo "Checking Ollama at $BASE_URL ..."

if ! curl -sf "$BASE_URL/api/tags" >/tmp/ollama-tags.json; then
  echo "FAIL: cannot reach $BASE_URL"
  echo ""
  echo "WSL + Docker agent-runner:"
  echo "  1. Run Ollama on Windows (not only in WSL)"
  echo "  2. In .env set: LLM_PROVIDER=ollama"
  echo "     OLLAMA_BASE_URL=http://host.docker.internal:11434"
  echo "  3. docker compose up -d --build agent-runner"
  exit 1
fi

echo "OK: Ollama reachable"
echo "Models:"
python3 - <<'PY' 2>/dev/null || cat /tmp/ollama-tags.json
import json
with open("/tmp/ollama-tags.json") as f:
    data = json.load(f)
for m in data.get("models", []):
    print(" -", m.get("name"))
PY

PRIMARY="${OLLAMA_MODEL:-qwen2.5:1.5b}"
FALLBACK="${OLLAMA_FALLBACK_MODEL:-qwen2.5:0.5b}"
if grep -q "$PRIMARY" /tmp/ollama-tags.json 2>/dev/null; then
  echo "Configured primary $PRIMARY: found"
else
  echo "WARN: primary $PRIMARY not in list — run: ollama pull $PRIMARY"
fi
if grep -q "$FALLBACK" /tmp/ollama-tags.json 2>/dev/null; then
  echo "Configured fallback $FALLBACK: found"
else
  echo "WARN: fallback $FALLBACK not in list — run: ollama pull $FALLBACK"
fi

echo "Agent-runner LLM health (if running):"
AGENT_PORT="${HOST_PORT_AGENT_RUNNER:-18082}"
curl -sf "http://localhost:${AGENT_PORT}/api/v1/agent/llm/health" | python3 -m json.tool 2>/dev/null \
  || echo "(agent-runner not on :${AGENT_PORT})"
echo ""
echo "Live map tracker: http://localhost:${AGENT_PORT}/api/v1/agent/tracker"
