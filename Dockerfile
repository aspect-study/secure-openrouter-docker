# ============================================================
# Secure OpenRouter Proxy — Dockerfile
# Base: nginx:alpine (minimal attack surface)
# Runs as non-root user "nginx" (uid 101)
# ============================================================
FROM nginx:alpine

# Install curl (healthcheck) and gettext (envsubst for token injection)
RUN apk add --no-cache curl gettext

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy nginx config as a template outside /etc/nginx so tmpfs doesn't wipe it
COPY nginx.conf /nginx.conf.template

# Copy entrypoint script
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# nginx:alpine already runs as uid 101 (nginx user).
# Ensure writable dirs are owned by that user.
RUN chown -R nginx:nginx /var/cache/nginx /var/log/nginx \
    && touch /var/run/nginx.pid \
    && chown nginx:nginx /var/run/nginx.pid \
    && chown nginx:nginx /nginx.conf.template

# Drop to non-root
USER nginx

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -sf http://localhost:8080/health || exit 1

ENTRYPOINT ["/docker-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
