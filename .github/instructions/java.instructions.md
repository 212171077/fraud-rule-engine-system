---
applyTo: "fraud-rule-engine/**/*.java,docker/**/*.java"
---

# Java conventions

- Target Java 21 and existing Spring Boot 3.5.16 conventions; format with the pinned Google Java Format via `./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml spotless:apply` from the repository root when needed.
- Keep classes in their documented hexagonal package and prefer package-private visibility unless public API is required.
- Use constructor injection and final fields for dependencies; avoid field injection and service locators.
- Use domain types and validation at boundaries. Never use `double`/`float` for monetary amounts or expose persistence entities as API models.
- Handle optional/insufficient context separately from valid non-matches; operational failures must not become success-shaped defaults.
- Keep exception messages and logs free of credentials and unnecessary personal/transaction data.
- Add focused tests for branch behavior, boundaries, invalid input, and relevant security conditions.
