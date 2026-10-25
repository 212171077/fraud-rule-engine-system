# FraudRuleEngineWeb

Angular frontend for the Fraud Rule Engine monorepo. Run the commands below
from `fraud-rule-engine-web/`. Shared infrastructure, API contracts, and setup
instructions are in the [root README](../README.md).

This project was generated using [Angular CLI](https://github.com/angular/angular-cli) version 20.3.2.

## Docker

From the repository root, configure `.env` as described in the root README and
run `./run-local.sh`. The script builds the backend and this frontend and prints
the web URL: `http://127.0.0.1:4200` by default (`WEB_PORT` in `.env`).
Caddy serves the production bundle, supports client-side route reloads, and
proxies `/api` requests to the backend without changing API authentication.
Actuator is not exposed through the web container.

Build the web image independently from the repository root:

```sh
docker build --file fraud-rule-engine-web/Dockerfile --tag fraud-rule-engine-web:local .
```

The production-like Compose stack serves the frontend and `/api` through
`https://api.localhost:8443` using the existing local TLS edge. This frontend provides the responsive sign-in and assessment workspace shell.
Phase 2 adds the evaluation summary, filterable/paginated search, and evaluation
detail journey. Phase 3 adds effective rule configuration inspection and audit
history, with permission-gated, confirmed admin changes. Phase 4 adds pending
registration, own-profile/password changes, and paginated administrator account
management with confirmed profile, role, and status changes.

## Authentication mode

The browser loads `/app-config.json` before routing. The bundled
`public/app-config.json` defaults to `{"authMode":"disabled"}`. Both assessment
Compose stacks explicitly mount `docker/web/local-app-config.json` read-only,
selecting `{"authMode":"local"}` to match their local-auth backend. No secret
belongs in this public configuration file.

For another deployment, provide the configuration file at `/srv/app-config.json`
in the web container. Leave local credentials disabled when the backend uses
external JWT identity. External browser sign-in is not implemented; a disabled
deployment displays an explicit explanation rather than offering local
credentials or falling back to them. A missing, unreachable, or invalid config
disables credentials and displays a configuration error. This setting controls
the UI only; it cannot enable backend endpoints or grant permissions.

For local Angular development, explicitly set `authMode` to `local` in
`public/app-config.json` and configure your same-origin `/api` proxy to the
assessment backend. Restore the checked-in disabled default before committing.
Use a Node version supported by Angular 20 (the production image uses Node 22).

## Phase 4 smoke checks

Run `./run-local.sh` from the repository root to rebuild. Provision the first
administrator using the root README's restricted bootstrap procedure; there is
no bootstrap page or default account.

1. From sign-in, request an account with synthetic name, surname, email, and
   password. Expect PENDING/AUDITOR confirmation without a token or automatic
   sign-in. Attempting sign-in before approval must fail generically.
2. As ADMIN, open Users, manage the request, optionally assign a reviewed role,
   then confirm ACTIVE status. Review alone and Cancel must not send a mutation.
   Sign in as the approved account and verify navigation follows `/auth/me`.
3. As AUDITOR/FRAUD_ANALYST, open My profile. No user list or arbitrary account
   editor is available; direct `/users` and `/users/:id` routes are denied.
4. Review and confirm an own-profile edit. Profile updates also revoke tokens:
   expect sign-out after success, then sign in with the current email/password.
   Legacy empty surnames can remain unchanged or be populated.
5. Change your password with the current password and matching new values.
   Review warns of token revocation. Successful confirmation signs you out;
   the old password must no longer work. Use only synthetic test passwords.
6. As ADMIN, create an interactive account (ACTIVE immediately) or a
   credentialless SERVICE identity. SERVICE has no password/sign-in or role
   transitions. Self role/status changes are unavailable. Disabling another
   account must prevent its login and invalidate its existing tokens.
7. Check pagination, duplicate-email conflicts, invalid fields, unavailable
   service, expired sessions, keyboard navigation, and narrow/mobile layouts.
   User tables scroll inside their own region rather than widening the page.
8. In a separately configured disabled deployment, verify that login,
   registration, profile/password and user-management controls are unavailable.
   A missing/invalid config must show an operator-facing configuration error.

Automated tests cover request shapes, password limits, confirmation boundaries,
profile ownership, route guards, configuration failures, and revocation-related
sign-out. They are not a live API-backed browser smoke test.

## Development server

To start a local development server, run:

```bash
ng serve
```

Once the server is running, open your browser and navigate to `http://localhost:4200/`. The application will automatically reload whenever you modify any of the source files.

## Code scaffolding

Angular CLI includes powerful code scaffolding tools. To generate a new component, run:

```bash
ng generate component component-name
```

For a complete list of available schematics (such as `components`, `directives`, or `pipes`), run:

```bash
ng generate --help
```

## Building

To build the project run:

```bash
ng build
```

This will compile your project and store the build artifacts in the `dist/` directory. By default, the production build optimizes your application for performance and speed.

## Running unit tests

To execute unit tests with the [Karma](https://karma-runner.github.io) test runner, use the following command:

```bash
ng test
```

## Running end-to-end tests

### Deterministic browser and accessibility smoke

From this directory with Node 22:

```sh
npm ci
npx playwright install --with-deps chromium
npm run test:e2e
```

This builds the production bundle and runs Playwright at desktop and mobile
sizes against a loopback static server with synthetic API responses. It covers
sign-in, navigation/permissions, overview, search/detail, escaped evidence,
rule history and confirmed edits, registration, admin account creation/status
confirmation, expired accounts, empty/out-of-range results, network failure,
deep-link reloads, keyboard skip navigation, horizontal reflow and axe checks.
There are no real users, credentials, upstream services or durable writes.
`CHROME_BIN` optionally selects an installed Chromium executable.

To watch the tests in a visible browser window, run from the repository root:

```sh
scripts/run-e2e.sh                              # headed, 300 ms between actions
scripts/run-e2e.sh --project desktop --slow-mo 800
scripts/run-e2e.sh -- --grep "registration"     # extra arguments go to Playwright
scripts/run-e2e.sh -- --debug                   # step through with Playwright Inspector
scripts/run-e2e.sh --headless                   # same suite without a window
scripts/run-e2e.sh --live                       # opt-in live smoke (variables below)
```

The script selects Node 22 through nvm when needed, runs `npm ci` only when
dependencies are missing or the lockfile changed, downloads Chromium if absent,
builds the bundle, and runs Playwright. It needs a graphical display for headed
mode. Headed mode is opt-in through `E2E_HEADED=1` (and `E2E_SLOW_MO_MS`, capped
at 5000), so `npm run test:e2e`, CI, and the test image remain headless.

Alternatively, from the repository root, use the isolated test image without
modifying host dependencies:

```sh
docker build --file fraud-rule-engine-web/Dockerfile.test --tag fraud-rule-engine-web:test .
docker run --rm --security-opt seccomp=unconfined fraud-rule-engine-web:test
```

The image runs Angular unit tests followed by the production-bundle browser
suite. The Chromium test-container setting is not an application deployment
setting. CI also runs Angular and browser/accessibility checks.

### Real container boundary

From the repository root:

```sh
docker build --file fraud-rule-engine-web/Dockerfile --tag fraud-rule-engine-web:phase5 .
bash scripts/test-web-container.sh fraud-rule-engine-web:phase5
```

The script starts only temporary containers on a dedicated test network and
cleans up their exact IDs on exit. It checks real Caddy deep links, the explicit
local config mount, API path/query/bearer forwarding to a synthetic backend,
and Actuator blocking. It does not connect to the existing assessment stack.

### Opt-in live assessment smoke

First run `./run-local.sh` from the root and prepare a synthetic administrator,
effective rules, and recent persisted evaluations. The suite deliberately fails
if evaluation detail or rule history cannot be exercised; an empty database is
not treated as a successful full journey. Supply these environment variables
through your local shell/secret tooling, not tracked files:

- `E2E_BASE_URL`: loopback web origin, normally `http://127.0.0.1:4200`.
- `E2E_ADMIN_EMAIL` and `E2E_ADMIN_PASSWORD`: an existing synthetic administrator.

Then run from this directory:

```sh
npm run test:e2e:live
```

This signs in and reads overview, evaluation/detail, rule history and admin
account details. It does not bootstrap, seed traffic, update rules or alter
accounts. The separate account-lifecycle case is skipped unless you explicitly
set `E2E_ALLOW_WRITES=synthetic-only`. That opt-in creates a uniquely named
`@example.test` account, approves it, verifies sign-in, then disables it and
checks token revocation. The account/audit history is retained, not deleted;
on failure an operator may need to disable the test account manually.

Only loopback hosts are accepted. External browser authentication and remote
environments are not exercised. Trace, screenshot and video capture are off
for these suites; do not enable them with non-synthetic credentials/data.
Live browser smoke has not been executed as part of the Phase 5 implementation.

### Remaining release checks

Automated axe scans are regression checks, not WCAG certification. Before
release, manually check keyboard reading/focus order, screen-reader
announcements, 200% zoom and 320px reflow, all error/confirmation states,
and the live API-backed journeys above. The observed dependency audit reports
nine high-severity findings in development tooling (Angular CLI/build, MCP/Vite
and Karma's dependency chains); these remain a release-review blocker.
No force downgrade or broad dependency migration has been applied.

## Additional Resources

For more information on using the Angular CLI, including detailed command references, visit the [Angular CLI Overview and Command Reference](https://angular.dev/tools/cli) page.
