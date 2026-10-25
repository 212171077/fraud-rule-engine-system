#!/usr/bin/env bash
set -Eeuo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
IMAGE="${1:-fraud-rule-engine-web:phase5}"
NETWORK=""
BACKEND=""
WEB=""
cleanup() {
  [[ -z "$WEB" ]] || docker rm -f "$WEB" >/dev/null
  [[ -z "$BACKEND" ]] || docker rm -f "$BACKEND" >/dev/null
  [[ -z "$NETWORK" ]] || docker network rm "$NETWORK" >/dev/null
}
trap cleanup EXIT

NETWORK="$(docker network create "fraud-web-smoke-$$")"
BACKEND="$(docker run -d --network "$NETWORK" --network-alias app \
  node:22.22.0-alpine@sha256:e4bf2a82ad0a4037d28035ae71529873c069b13eb0455466ae0bc13363826e34 \
  node -e 'require("http").createServer((req,res)=>{res.setHeader("Content-Type","application/json");res.end(JSON.stringify({path:req.url,authorized:req.headers.authorization==="Bearer synthetic-container-smoke"}))}).listen(8080)')"
WEB="$(docker run -d --network "$NETWORK" \
  --volume "$PWD/docker/web/local-app-config.json:/srv/app-config.json:ro" "$IMAGE")"

for attempt in {1..30}; do
  if docker exec "$WEB" wget -q -O /dev/null http://127.0.0.1:8080/; then
    break
  fi
  sleep 1
done

docker exec "$WEB" sh -ec '
  for path in /login /register /overview /evaluations/synthetic-id /rules/THRESHOLD_LIMIT /users/synthetic-id /profile; do
    wget -q -O /tmp/smoke-page "http://127.0.0.1:8080$path"
    grep -q "<app-root" /tmp/smoke-page
  done
  wget -q -O /tmp/smoke-config http://127.0.0.1:8080/app-config.json
  grep -q "\"local\"" /tmp/smoke-config
  wget -q --header="Authorization: Bearer synthetic-container-smoke" \
    -O /tmp/smoke-api "http://127.0.0.1:8080/api/v1/fraud-rules?effectiveAt=synthetic"
  grep -q "\"authorized\":true" /tmp/smoke-api
  grep -q "/api/v1/fraud-rules?effectiveAt=synthetic" /tmp/smoke-api
  for path in /actuator /actuator/health /actuator/prometheus; do
    if wget -q -O /dev/null "http://127.0.0.1:8080$path"; then
      echo "Unexpected Actuator exposure" >&2
      exit 1
    fi
  done
'
echo "Container smoke passed: deep links, explicit config, API path/query/bearer forwarding, Actuator isolation."
