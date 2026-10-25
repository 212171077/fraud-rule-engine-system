#!/usr/bin/env bash
# Run without arguments for a menu, or choose directly:
# ./scripts/tail-logs.sh app    # Application logs
# ./scripts/tail-logs.sh kafka  # Kafka broker logs
# ./scripts/tail-logs.sh both   # Both services
set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
  printf 'Usage: %s [--prod-like] [app|kafka|both]\n' "$0"
  printf 'Without a service argument, an interactive menu is shown.\n'
  printf 'Shows the last 100 lines, then follows new logs. Press Ctrl+C to stop.\n'
}

COMPOSE=(docker compose -f compose.yaml)
if [[ "${1:-}" == "--prod-like" ]]; then
  COMPOSE=(docker compose -f compose.prod-like.yaml)
  shift
fi

if [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  usage
  exit 0
fi
if (($# > 1)); then
  usage >&2
  exit 2
fi

service="${1:-}"
if [[ -z "$service" ]]; then
  printf 'Select logs to follow:\n  1) Application\n  2) Kafka\n  3) Application and Kafka\n'
  if ! read -r -p 'Choice [1-3]: ' service; then
    printf 'No selection received. Pass app, kafka, or both as an argument.\n' >&2
    exit 2
  fi
fi

case "$service" in
  app|1) SERVICES=(app) ;;
  kafka|2) SERVICES=(kafka) ;;
  both|3) SERVICES=(app kafka) ;;
  *)
    printf 'Invalid selection: choose app, kafka, or both.\n' >&2
    usage >&2
    exit 2
    ;;
esac

if ! command -v docker >/dev/null 2>&1; then
  printf 'Docker is required but was not found in PATH.\n' >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  printf 'Docker Compose v2 is required.\n' >&2
  exit 1
fi

exec "${COMPOSE[@]}" logs --follow --tail 100 --timestamps --no-color "${SERVICES[@]}"
