# Testing Strategy

## Toolchain and commands

Run these commands from the monorepo root.

Use JDK 21 and the Maven Wrapper (Maven 3.10.0). The default build runs
JUnit/Spring context/security tests and the pinned Google Java Format check:

```sh
./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B clean verify
```

The integration profile requires Docker and runs all `*IT` tests through
Failsafe; it fails if no matching tests are discovered:

```sh
./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B -Pintegration clean verify
```

Validate local service configuration and build the same image used by CI:

```sh
docker compose --env-file .env.example config --quiet
docker build --tag fraud-rule-engine:ci .
docker run --rm --entrypoint /bin/promtool \
  --volume "$PWD/monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro" \
  prom/prometheus:v3.5.0 check config /etc/prometheus/prometheus.yml
jq empty monitoring/grafana/dashboards/service-reliability.json
```

For Phase 4 observability verification, start Compose and query Prometheus for
`up{job="fraud-rule-engine"}` and a standard JVM or HTTP metric. Check that a
request outside the approved monitoring CIDR receives 403, Prometheus scrapes
from its fixed monitoring address, and that address cannot access protected
application routes.

## Test layers and boundaries

| Layer | Purpose | Dependencies |
|---|---|---|
| Unit/domain tests | Deterministic rule, policy, and validation behavior; no I/O. Add alongside domain work in Phases 5–7. | JUnit 5; Mockito only when a collaborator boundary needs a test double. |
| Context/security tests | Verify Spring wiring, public health, monitor-source-only metrics access, protected API denial, and safe configuration defaults. | H2 and Spring MockMvc; no Docker. |
| Local identity/authz tests | Verify one-time bootstrap, public PENDING registration, rejected privilege fields, normalized-email uniqueness, administrator approval, BCrypt handling, generic failures, lockout, role/ownership policy and immediate token revocation. | H2 and Spring MockMvc; PostgreSQL migration/status/uniqueness and approval path are covered by tagged integration tests. |
| Integration tests (`*IT`) | Verify real PostgreSQL persistence and Kafka consumer behavior, including delivery failures. | Testcontainers and Docker. |
| Contract tests | Verify proposed synthetic event and DLQ schemas/examples; these do not imply producer approval. | Pinned JSON Schema validator; no upstream schema is assumed. |
| API tests | Verify authentication, bounded search/summary queries, safe evaluation projections, Problem Details, rule validation/versioning/audit, and the OpenAPI YAML paths. | Spring MockMvc, H2, and SnakeYAML; no Docker. |
| CI | Require frontend lint/tests/browser smoke, unit/build/format, integration, Compose/image, and secret/dependency jobs through `PR quality gate`. | GitHub Actions, Java 21, Node 22, Docker; required branch check configured by an administrator. |

Keep test data synthetic and deterministic. Avoid time-sensitive assertions
without an injected clock. Integration failures must not be skipped or
reclassified as unit-test success. Add a regression test for each security,
contract, migration, idempotency, or failure-semantics change.

## Frontend release checks

The Angular app has unit/component tests plus Playwright smoke against its
production bundle. From `fraud-rule-engine-web/`, use Node 22:

```sh
npm ci
npm run lint
npm test -- --watch=false --browsers=ChromeHeadless
npx playwright install --with-deps chromium
npm run test:e2e
```

`npm run lint` applies ESLint's recommended TypeScript rules to application,
unit-test, browser-test, and TypeScript configuration files, with zero warnings
allowed. Java formatting is checked by Spotless during Maven `verify`.
Angular production builds also enforce the existing strict TypeScript/template
compiler settings. SonarQube analysis is deferred until a service is provisioned
and approved. See the root README for required-check branch-rule setup;
a green workflow is not itself evidence that merge protection is enabled.

From the root, `Dockerfile.test` provides the same isolated checks:

```sh
docker build -f fraud-rule-engine-web/Dockerfile.test -t fraud-rule-engine-web:test .
docker run --rm --security-opt seccomp=unconfined fraud-rule-engine-web:test
docker build -f fraud-rule-engine-web/Dockerfile -t fraud-rule-engine-web:phase5 .
bash scripts/test-web-container.sh fraud-rule-engine-web:phase5
```

Unit/component coverage includes API request shapes, status-code failures,
session/token scope, permission guards, form/confirmation rules, UTC filters,
request cancellation and out-of-range pages. Desktop/mobile browser checks
use synthetic fixtures, axe, keyboard focus and viewport containment; they do
not replace backend authorization tests or real API integration. The container
smoke uses only temporary containers/network and a synthetic upstream.

The opt-in loopback-only `npm run test:e2e:live` requires pre-provisioned
synthetic data and operator-supplied administrator credentials. By default
it reads the investigation, rule and user journeys. Explicit
`E2E_ALLOW_WRITES=synthetic-only` additionally creates/approves/disables a
unique synthetic account, retaining its audit history. See the web README.
Trace/video/screenshots are disabled; never use production credentials.

Release blockers remain: live API-backed browser smoke and manual
screen-reader/keyboard/zoom review have not been performed, and the observed
dependency audit reports nine high-severity development-tooling findings.
Automated accessibility results are not a claim of WCAG certification.

## Backend limits

Synthetic fraud rules, JPA persistence, a Kafka event consumer, REST APIs, and
assessment-only local authentication are implemented. The focused Kafka
integration tests exercise valid processing,
commit-before-offset-failure redelivery/idempotency, invalid input, retry
exhaustion, and DLQ output against PostgreSQL and Kafka. Calibrated fraud policy remains out of scope. Consumer counters/timers and
Kafka-client metrics are not a throughput benchmark, business reporting
system, calibrated metric, or production-readiness evidence.
