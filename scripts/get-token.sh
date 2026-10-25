#!/usr/bin/env bash
# Prints an application JWT: ./scripts/get-token.sh [admin|analyst|auditor]
set -euo pipefail
cd "$(dirname "$0")/.."
who="${1:-admin}"
case "$who" in
  admin|analyst|auditor) ;;
  *) echo "usage: $0 [admin|analyst|auditor]" >&2; exit 2 ;;
esac
if [[ -f .env ]]; then
  set -a
  . ./.env
  set +a
fi
export DEMO_TOKEN_ROLE="$who"
# Keep credentials out of command-line arguments and logs.
python3 - <<'PY'
import json
import os
import sys
import urllib.error
import urllib.request

role = os.environ["DEMO_TOKEN_ROLE"].upper()
email = os.environ.get(f"DEMO_{role}_EMAIL", "")
password = os.environ.get(f"DEMO_{role}_PASSWORD", "")
if not email or not password:
    sys.exit(f"Set DEMO_{role}_EMAIL and DEMO_{role}_PASSWORD for an existing approved user.")
url = os.environ.get("DEMO_API", f"http://localhost:{os.environ.get('APP_PORT', '8080')}")
request = urllib.request.Request(
    url + "/api/v1/auth/login",
    data=json.dumps({"email": email, "password": password}).encode(),
    headers={"Content-Type": "application/json"},
)
try:
    with urllib.request.urlopen(request, timeout=15) as response:
        print(json.load(response)["accessToken"])
except urllib.error.HTTPError as error:
    sys.exit(f"Login failed (HTTP {error.code}); verify credentials, approval, and lockout status.")
except urllib.error.URLError:
    sys.exit("Authentication service is unavailable; check DEMO_API and the running application.")
PY
