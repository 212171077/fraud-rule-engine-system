# ADR-0007: Application-managed JWT and approved registration

- **Status:** Accepted for the synthetic assessment at the owner's request; not production approval
- **Date:** 2026-10-21
- **Supersedes:** The external-identity default for the provided Compose stacks

## Context

The owner requested replacing the external provider with application-managed users and JWT
issuance to demonstrate authentication implementation, and explicitly chose
public registration with no fraud-data access until administrator approval.
The previous provider already issued JWTs; this change moves credential and token lifecycle
responsibility into the application, rather than changing the token format.

## Decision

Reuse the existing BCrypt/password, JWT encoder/decoder, bootstrap, user
administration, lockout, audit, and per-request token-version checks. Both
Compose variants enable local auth; no identity-provider container or realm
import is required. The external-token decoder remains a mutually exclusive
compatibility option, disabled in the provided stacks.

`POST /api/v1/auth/register` accepts name, surname, email and password only.
The server assigns PENDING and the least-privileged interactive role, AUDITOR.
Pending identities receive no tokens and cannot log in. Administrators review
accounts and set ACTIVE using the existing status endpoint, optionally
assigning another role first. Registration requires completed first-admin
bootstrap and shares its database lock, preventing anonymous registrations
from taking the bootstrap slot. Email uniqueness is database-enforced; a
duplicate returns a sanitized 409. V5 extends V4's status constraint without
rewriting existing accounts or decisions. V6 replaces the single `display_name`
with separate `name` and `surname` fields; existing display names are preserved
in `name`, and their `surname` is empty pending user correction.
This request/response shape change is intentional before frontend implementation;
no `displayName` compatibility field is retained in the assessment API.

Controllers depend on a framework-free application use-case port. Password,
JWT, transactional JPA, and account lifecycle implementation reside in the
identity adapter, not framework-dependent application policy.

## Consequences and limits

The application now owns credential protection and approval. There are no
seeded users, shared signing keys, migrated external-provider passwords, refresh tokens,
MFA, email verification, recovery, or production key-rotation service.
Existing external-provider tokens are not accepted by the Compose application.
Old identity containers/volumes are not deleted automatically.
Fraud evaluations and rules are unchanged.

Use independent high-entropy signing/bootstrap secrets per environment and
remove bootstrap access after provisioning. Five-minute HS256 bearer tokens
validate signature, time, issuer, audience, status, role and token version.
HS256 assumes a single trusted signer/verifier; it is not a distributed
identity-provider architecture. TLS and distributed ingress throttling/abuse
controls are required before any exposure beyond loopback. Account lockout
alone does not prevent registration flooding or unknown-account login floods.
Only synthetic profiles/data are approved; retention and production identity
remain owner decisions. This is not security certification or release approval.
