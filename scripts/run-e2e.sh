#!/usr/bin/env bash
# Runs the Playwright browser tests for fraud-rule-engine-web.
# By default the browser window is shown and actions are slowed so they can be followed.
set -Eeuo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/run-e2e.sh [options] [-- <extra Playwright arguments>]

Options:
  --headless        Run without opening a browser window.
  --slow-mo MS      Delay between browser actions in headed mode (default 300, max 5000, 0 disables).
  --project NAME    Run one viewport project only: desktop or mobile (synthetic suite only).
  --live            Run the opt-in live smoke against a running loopback stack instead of
                    the synthetic suite. Requires E2E_BASE_URL, E2E_ADMIN_EMAIL and
                    E2E_ADMIN_PASSWORD (see fraud-rule-engine-web/README.md).
  --skip-build      Reuse the existing production bundle (synthetic suite only).
  -h, --help        Show this help.

Examples:
  scripts/run-e2e.sh
  scripts/run-e2e.sh --project desktop --slow-mo 800
  scripts/run-e2e.sh -- --grep "registration"
  scripts/run-e2e.sh -- --debug          # Playwright Inspector, step through each action
  scripts/run-e2e.sh --headless
EOF
}

headed=1
slow_mo=300
project=""
live=0
build=1
extra_args=()

while (($#)); do
  case "$1" in
    --headless) headed=0 ;;
    --slow-mo)
      [[ $# -ge 2 && "$2" =~ ^[0-9]+$ ]] || { echo "--slow-mo requires a non-negative integer" >&2; exit 2; }
      slow_mo="$2"
      shift
      ;;
    --project)
      [[ $# -ge 2 && "$2" =~ ^(desktop|mobile)$ ]] || { echo "--project must be desktop or mobile" >&2; exit 2; }
      project="$2"
      shift
      ;;
    --live) live=1 ;;
    --skip-build) build=0 ;;
    -h | --help) usage; exit 0 ;;
    --) shift; extra_args=("$@"); break ;;
    *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../fraud-rule-engine-web"

node_supported() {
  command -v node >/dev/null 2>&1 &&
    node -e 'const [a,b]=process.versions.node.split(".").map(Number);process.exit(a>22||(a===22&&b>=12)||(a===20&&b>=19)?0:1)'
}

if ! node_supported && [[ -s "${NVM_DIR:-$HOME/.nvm}/nvm.sh" ]]; then
  set +u
  # shellcheck source=/dev/null
  . "${NVM_DIR:-$HOME/.nvm}/nvm.sh"
  nvm use --silent 22 >/dev/null 2>&1 || true
  set -u
fi
if ! node_supported; then
  echo "Node 20.19+ or 22.12+ is required (found: $(node --version 2>/dev/null || echo none)). Install Node 22, e.g. 'nvm install 22'." >&2
  exit 1
fi
echo "Using Node $(node --version)"

if [[ -d node_modules ]] && [[ -n "$(find node_modules ! -user "$(id -u)" -print -quit)" ]]; then
  echo "node_modules contains files not owned by $(id -un) (likely from a container run)." >&2
  echo "Fix ownership, then rerun: sudo chown -R \"$(id -un)\":\"$(id -gn)\" fraud-rule-engine-web/node_modules" >&2
  exit 1
fi
if [[ ! -d node_modules/@playwright/test || package-lock.json -nt node_modules/.package-lock.json ]]; then
  echo "Installing locked frontend dependencies (npm ci)..."
  npm ci --no-audit --no-fund
fi

# Downloads Chromium only when missing. System libraries are not installed here; if the
# browser fails to start, run once: npx playwright install --with-deps chromium
npx playwright install chromium

if ((headed)); then
  if [[ -z "${DISPLAY:-}" && -z "${WAYLAND_DISPLAY:-}" ]]; then
    echo "No graphical display found (DISPLAY/WAYLAND_DISPLAY unset). Use --headless." >&2
    exit 1
  fi
  export E2E_HEADED=1
  export E2E_SLOW_MO_MS="$slow_mo"
else
  unset E2E_HEADED E2E_SLOW_MO_MS
fi

playwright_args=()
if ((live)); then
  playwright_args+=(--config playwright.live.config.ts)
  [[ -z "$project" ]] || { echo "--project applies to the synthetic suite only" >&2; exit 2; }
else
  [[ -z "$project" ]] || playwright_args+=(--project "$project")
  if ((build)); then
    npm run build
  elif [[ ! -f dist/fraud-rule-engine-web/browser/index.html ]]; then
    echo "No production bundle found; rerun without --skip-build." >&2
    exit 1
  fi
fi

exec npx playwright test "${playwright_args[@]}" "${extra_args[@]}"
