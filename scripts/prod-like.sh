#!/usr/bin/env bash
# Production-like local stack: ./scripts/prod-like.sh up|down|status|secrets
set -euo pipefail
cd "$(dirname "$0")/.."

COMPOSE=(docker compose -f compose.prod-like.yaml)
EDGE_PORT="${EDGE_PORT:-8443}"

generate_secrets() {
  umask 077
  mkdir -p secrets
  chmod 700 secrets
  for name in postgres_password jwt_signing_key jwt_bootstrap_token grafana_admin_password; do
    if [ ! -s "secrets/$name" ]; then
      head -c 32 /dev/urandom | base64 | tr -d '\n' > "secrets/$name"
    fi
    # Containers run as different uids; the 700 directory keeps other host users out.
    chmod 644 "secrets/$name"
  done
}

case "${1:-up}" in
  secrets)
    generate_secrets
    echo "Secrets are in ./secrets (git-ignored)."
    ;;
  up)
    generate_secrets
    "${COMPOSE[@]}" up --build --detach --wait
    printf '\nProduction-like stack is running (TLS certificates come from Caddy internal CA; use -k or trust it).\n'
    printf '  Web app:         https://api.localhost:%s\n' "$EDGE_PORT"
    printf '  API:             https://api.localhost:%s\n' "$EDGE_PORT"
    printf '  Authentication:  /api/v1/auth/bootstrap, /register, /login (see README)\n'
    printf '  Grafana:         http://localhost:%s (user admin, password in secrets/grafana_admin_password)\n' "${GRAFANA_PORT:-3000}"
    ;;
  status) "${COMPOSE[@]}" ps ;;
  down) "${COMPOSE[@]}" down ;;
  *) echo "usage: $0 up|down|status|secrets" >&2; exit 2 ;;
esac
