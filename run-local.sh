#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

COMPOSE=(docker compose -f compose.yaml)
DEBUG=false
if (($# == 1)) && [[ "$1" == "--debug" ]]; then
    COMPOSE+=(-f compose.debug.yaml)
    DEBUG=true
elif (($# > 0)); then
    printf 'Usage: %s [--debug]\n' "$0" >&2
    exit 2
fi

if ! command -v docker >/dev/null 2>&1; then
    printf 'Docker is required but was not found in PATH.\n' >&2
    exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
    printf 'Docker Compose v2 is required. Install or enable the Docker Compose plugin.\n' >&2
    exit 1
fi

if [[ ! -f .env ]]; then
    printf 'Missing .env. Copy .env.example to .env, change the local passwords, and run this script again.\n' >&2
    exit 1
fi

printf 'Validating Docker Compose configuration...\n'
# The synthetic local seed is applied to the persistent volume once, and Flyway then
# requires it to stay resolvable, so keep the local profile on every run.
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-local}"

"${COMPOSE[@]}" config --quiet

printf 'Building the backend and web images and starting the full local stack...\n'
"${COMPOSE[@]}" up -d --build --wait

"${COMPOSE[@]}" ps
APP_URL="$("${COMPOSE[@]}" port app 8080)"
WEB_URL="$("${COMPOSE[@]}" port web 8080)"
PROMETHEUS_URL="$("${COMPOSE[@]}" port prometheus 9090)"
GRAFANA_URL="$("${COMPOSE[@]}" port grafana 3000)"
MAILPIT_URL="$("${COMPOSE[@]}" port mailpit 8025)"
printf '\nLocal services are ready:\n'
printf '  Web app:            http://%s\n' "$WEB_URL"
printf '  Application health: http://%s/actuator/health\n' "$APP_URL"
printf '  Swagger UI:         http://%s/swagger-ui/index.html\n' "$APP_URL"
printf '  Prometheus:         http://%s\n' "$PROMETHEUS_URL"
printf '  Grafana:            http://%s\n' "$GRAFANA_URL"
printf '  Mailpit inbox:      http://%s\n' "$MAILPIT_URL"
printf '  Authentication:     bootstrap/register/login at http://%s/api/v1/auth (see README)\n' "$APP_URL"
if [[ "$DEBUG" == true ]]; then
    printf '  Java debugger:      127.0.0.1:5005 (attach from IntelliJ or VS Code)\n'
    printf '  Debugging grants full JVM access; use synthetic data only.\n'
fi
printf '\nView logs with: docker compose logs --no-color -f\n'
printf 'Stop services (preserving PostgreSQL data) with: docker compose down\n'
