# ADR-002: Use envsubst for token injection instead of nginx set variables

**Date:** 2026-05-29
**Status:** Accepted

## Context

The nginx proxy needs the OpenRouter API key injected at runtime without baking it into the image.
Two approaches were attempted:

1. Write a `set $openrouter_api_key "..."` snippet to `/etc/nginx/conf.d/` via entrypoint, include it inside the location block
2. Use `envsubst` to substitute `${OPENROUTER_API_KEY}` directly into `nginx.conf` at startup

## Decision

Use `envsubst` (from the `gettext` Alpine package) in `docker-entrypoint.sh`.

## Reasons

- **Approach 1 failed in practice:** The `include` + `set` pattern inside a location block was unreliable — the variable scoping in nginx caused requests to fall through to `location / { return 404; }` with no error logged.
- **envsubst is explicit:** The template file (`/nginx.conf.template`) has `${OPENROUTER_API_KEY}` as a literal placeholder. The entrypoint substitutes it and writes the final `nginx.conf` before nginx starts. No runtime variable resolution.
- **Scoped substitution:** `envsubst '${OPENROUTER_API_KEY}'` only replaces that one variable — nginx's own `$remote_addr`, `$proxy_add_x_forwarded_for` etc. are left untouched.

## Key implementation detail

The template is stored at `/nginx.conf.template` (image root), NOT inside `/etc/nginx/`.
`/etc/nginx` is a `tmpfs` mount (`read_only: true` container). If the template were inside `/etc/nginx`, the tmpfs would wipe it at container startup before the entrypoint could read it.

## Trade-offs

- The final `nginx.conf` contains the plaintext token in the container's tmpfs memory — acceptable since it's ephemeral and not written to disk or image layers.
