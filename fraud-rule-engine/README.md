# Fraud Rule Engine backend

Java 21 / Spring Boot service. Source, tests, Maven configuration, and the Maven
Wrapper remain in this directory.

From this directory, build and run unit tests with:

```sh
./mvnw -B clean verify
```

For PostgreSQL and Kafka Testcontainers integration tests (Docker required):

```sh
./mvnw -B -Pintegration clean verify
```

Contracts and monitoring configuration are shared in the parent directory;
contract tests require the complete monorepo checkout.

Run Docker Compose and shared scripts from the repository root. See the
[root README](../README.md) for setup, authentication, local infrastructure,
monitoring, and operational guidance, and the
[architecture documentation](../docs/architecture.md) for service boundaries.
