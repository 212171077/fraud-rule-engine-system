---
applyTo: "**/*Test.java,**/*IT.java,**/*ITCase.java"
---

# Testing conventions

- Keep ordinary unit/context tests Docker-free. Name Testcontainers integration classes `*IT` and tag them `integration`.
- From `fraud-rule-engine/`, run `./mvnw -B clean verify` for the default suite and `./mvnw -B -Pintegration clean verify` for integration; never skip a failed or undiscovered CI test.
- Use deterministic synthetic fixtures, fixed clocks where time matters, and explicit boundary cases.
- Verify security both negatively and positively: unapproved sources cannot scrape metrics, the approved monitor source can, and monitor access does not authorize API routes.
- Test database and Kafka behavior with real Testcontainers when adapter semantics are involved; do not replace a failing integration test with a mock.
- Keep assertions meaningful; do not make them weaker to obtain a passing build.
