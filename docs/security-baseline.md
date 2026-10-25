# Security baseline

## Identity modes

The Compose assessment stacks select application-managed JWT authentication.
No external identity service is provisioned. An external OAuth 2.0 resource-server
compatibility mode remains when local auth is disabled. For that mode, set
`FRAUD_SECURITY_JWT_ISSUER_URI` and `FRAUD_SECURITY_JWT_AUDIENCE`; issuer,
standard JWT time claims, and audience are validated. Signed tokens must carry
a trusted `roles` claim containing one of `ADMIN`, `FRAUD_ANALYST`, `AUDITOR`,
or `SERVICE`. The provider must be configured to issue only reviewed role
claims. When issuer/audience are absent, protected routes fail closed; no
local credential fallback is enabled.

The **assessment-only** application-managed identity mode is selected by
setting `FRAUD_SECURITY_LOCAL_AUTH_ENABLED=true` (both Compose stacks). It replaces the OIDC decoder
for that process and enables local bootstrap/login/user APIs. Configure a
unique issuer and audience, at least 32 random bytes of base64-encoded
`FRAUD_SECURITY_LOCAL_AUTH_SIGNING_KEY_BASE64`, and a one-time random
`FRAUD_SECURITY_LOCAL_AUTH_BOOTSTRAP_TOKEN` of at least 32 characters through
an untracked local `.env` or secret manager. Do not reuse keys across
environments. Bootstrap the first administrator once, then remove the
bootstrap token from the runtime environment. This local HS256/password flow
is not an identity provider, is not intended for deployment, and should not be
combined with external OIDC configuration. Refresh tokens and logout are
omitted; access tokens default to five minutes and can be configured up to
15 minutes. Role/status/profile changes increment a database token version;
each request validates the active user and current token version, immediately
revoking old tokens. Changing the signing key invalidates all issued tokens.

Passwords for interactive local users are normalized only for email lookup
and hashed with BCrypt work factor 12. Passwords must be 12–72 characters and
at most 72 UTF-8 bytes. Login failures are generic and audited with
actor/target UUIDs only when the account is known; unknown email values are not
persisted. Five consecutive bad passwords lock an account for 15 minutes. This
is account lockout, not a distributed or per-source rate limiter. Do not expose
local mode without an approved ingress
rate limit and TLS. Passwords, hashes, access tokens, and bootstrap secrets
are never included in profiles or audit rows.
Users can change their own password after confirming the current password;
the change increments the token version, so they must log in again.

## Role and endpoint policy

Public `POST /api/v1/auth/register` accepts only display name, email, and password.
The first administrator must already have been bootstrapped; otherwise it
returns 409, with no account persisted. Registration shares the bootstrap
database lock, so a concurrent request cannot prevent first-admin creation.
The server assigns `AUDITOR` and `PENDING`. No JWT is returned and pending
accounts cannot log in or access fraud data. Administrators review the user
list, optionally assign a role, and approve through
`PATCH /api/v1/users/{id}/status` with `{"status":"ACTIVE"}`. This is an
explicit assessment approval decision, not email verification or proof of
identity. Duplicate normalized emails return 409 and thus disclose that an
address is registered; use synthetic addresses only. Public registration
requires ingress throttling and abuse controls before any non-local exposure.

| Capability | ADMIN | FRAUD_ANALYST | AUDITOR | SERVICE |
|---|---|---|---|---|
| Interactive login / `/auth/me` | Yes | Yes | Yes | No |
| Create/list users | Yes | No | No | No |
| Read a user profile | Any profile | Own profile | Own profile | No |
| Update display name/email | Any profile | Own profile | Own profile | No |
| Change role or account status | Yes, not self | No | No | No |
| Search/view evaluations and reports | Yes | Yes | Yes, read-only | No by default |
| Read rule configuration/history | Yes | Yes | Yes | No |
| Create/update/deactivate rules | Yes | No | No | No |
| Process events | Not through user API | Not through user API | Not through user API | Non-interactive service use only |

The matrix is enforced centrally in the Spring Security request policy.
Backend object checks limit analyst/auditor user-profile access to their own
UUID; denied non-owner profile lookups return 404. The profile update model
does not include role/status fields. Only administrators can create accounts
or change another user's role/status; self role/status changes are denied.
User lists are administrator-only and paginated (maximum 100). `SERVICE`
identities have no password and cannot use the interactive local token flow;
no broad REST service permission is granted. No tenant/business-scope
isolation is claimed. The Kafka listener has no broker SASL/ACL identity
configured in this assessment; the SERVICE role expresses a least-privilege
requirement but does not prove broker authentication.

The bootstrap endpoint is public only in the sense that it precedes the first
login. It checks a secret supplied out of band, serializes bootstrap with a
database lock, and permanently conflicts once any user exists. Invalid local
credentials receive the same 401 shape regardless of account existence,
status, lockout, or password. Refresh and logout routes are intentionally not
implemented because no reusable refresh token is issued.

Registration, user creation, profile, role, status, successful login, and failed login
actions are persisted in `user_security_audit`. Audit rows contain actor and
target UUIDs where available, action, outcome, and timestamp. Unknown login
attempts store no email or email-derived reference. Rule changes continue to use
the existing immutable configuration audit. Audit rows contain no request
bodies, email addresses, passwords, hashes, or tokens.

This is an assessment policy, not an approved Capitec role model. OIDC role
claim mapping, user provisioning, account recovery, signing-key rotation,
distributed login throttling, and production identity lifecycle remain
deployment/security-owner decisions. No test or local configuration is
security certification.

## HTTP and operations

The API is stateless and bearer-token based: CSRF, form login, HTTP Basic,
session creation, and request caching are disabled. CORS is not enabled; add
only explicit trusted origins if a browser client is approved. Spring
Security's default headers remain enabled, with frame embedding denied.
Request header/form sizes are bounded. Error responses suppress exception
details, stack traces, and binding information.

Health/probes and POST bootstrap, registration, and login are anonymous;
bootstrap additionally requires its out-of-band secret. Prometheus uses a
dedicated filter chain restricted to the configured monitoring source CIDR on
`/actuator/prometheus`; this does not grant access to application routes.
Swagger UI and the springdoc OpenAPI endpoints are off by default
(`fraud.docs.enabled=false`, env `FRAUD_DOCS_ENABLED`) and denied like any other
unmatched route. When the flag is on, `GET` on exactly `/swagger-ui.html`,
`/swagger-ui/**`, `/v3/api-docs`, and `/v3/api-docs/**` is anonymous; this is for
synthetic local use only and must not be enabled in deployment. API routes and
Prometheus rules are unchanged. `DocsEnabledSecurityTests` and
`DocsDisabledSecurityTests` cover both modes; run them with
`./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B -Dtest='Docs*SecurityTests' test`
from the repository root.
Compose separates monitoring and backend networks. Configure the CIDR and
Prometheus address together if the local subnet changes. Deployment ingress
must use TLS, narrow network allowlists, and an approved identity provider.

## Verification

`LocalAuthenticationTest` exercises one-time bootstrap, pending registration,
administrator approval, rejected privilege fields, normalized-email uniqueness, password hashing,
generic failed login, signed-token validation, role restrictions, object
ownership, privilege escalation denial, token revocation after role/status
changes, audit rows, and account lockout. API tests verify admin-only rule
writes and analyst/auditor read access. Testcontainers integration tests are
separate `*IT` tests and run from the repository root with
`./fraud-rule-engine/mvnw -f fraud-rule-engine/pom.xml -B -Pintegration verify`.
CI performs secret scanning, pull-request dependency review, formatting/build
checks, unit and integration tests, and an image build.

The data/privacy owner must approve permitted identifiers, persistence, DLQ,
retention, and deletion before real data is used. CI and these defaults do not
constitute security approval or compliance.
