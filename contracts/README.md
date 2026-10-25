# Assessment contracts

**Owner:** Assessment API/producer contract owner (not yet assigned to an
individual). The upstream producer contract has not been supplied.

`openapi.yaml` describes the implemented versioned evaluation, rule, user,
and authentication endpoints. Public registration creates PENDING/AUDITOR
accounts; only administrator activation permits login and fraud-data access.
See ADR-0007 and `docs/security-baseline.md` for identity assumptions.
`events/` contains the synthetic v1 event and dead-letter schemas. These
assessment contracts are not approved upstream or production contracts.

`OpenApiContractTest` checks required routes and anonymous authentication
operations. Event tests use the pinned JSON Schema validator. A full semantic
OpenAPI compatibility validator remains future work; YAML parsing alone is not
proof of OpenAPI validity or external compatibility.
