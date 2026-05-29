#!/bin/sh
# ============================================================
# Entrypoint: substitutes OPENROUTER_API_KEY into nginx.conf
# at runtime using envsubst — key is never baked into the image.
# ============================================================
set -e

if [ -z "$OPENROUTER_API_KEY" ]; then
  echo "[ERROR] OPENROUTER_API_KEY is not set. Aborting." >&2
  exit 1
fi

# envsubst replaces ${OPENROUTER_API_KEY} in the template.
# We scope it to only that variable so nginx's own $remote_addr etc. are untouched.
envsubst '${OPENROUTER_API_KEY}' \
  < /nginx.conf.template \
  > /etc/nginx/nginx.conf

echo "[INFO] OpenRouter token injected. Starting nginx..."
exec "$@"
