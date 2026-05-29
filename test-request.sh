#!/usr/bin/env bash
# ============================================================
# test-request.sh — smoke test against the local proxy
# Usage (macOS/Linux):  chmod +x test-request.sh && ./test-request.sh
# Usage (Windows/Git Bash):  bash test-request.sh
# ============================================================

MODEL="${DEFAULT_MODEL:-mistralai/mistral-7b-instruct:free}"
PROXY_URL="http://localhost:8081/api/v1/chat/completions"

echo "Testing proxy at: $PROXY_URL"
echo "Model: $MODEL"
echo "---"

curl -s -X POST "$PROXY_URL" \
  -H "Content-Type: application/json" \
  -d "{
    \"model\": \"$MODEL\",
    \"messages\": [
      {\"role\": \"user\", \"content\": \"Say hello in one sentence.\"}
    ]
  }" | python3 -m json.tool 2>/dev/null || cat
