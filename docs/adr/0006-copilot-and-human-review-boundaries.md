# ADR-0006: Copilot assistance and human review boundaries

- **Status:** Proposed — approval pending
- **Date:** 2026-10-17
- **Owners:** Assessment technical lead; repository maintainers
- **Related requirements:** TRS §§17, 21; roadmap P0.2, P4.4–P4.5

## Context

The TRS asks for Copilot instructions, agents, and skills to demonstrate
controlled AI-assisted engineering. Such artifacts are guidance and do not
replace tests, CI, code review, or human authorization. The supported client
and its filename/frontmatter conventions have not been selected or verified.

## Decision

Use Copilot only as an assistive tool under repository guidance. Require
inspect-before-edit, requirements/assumptions separation, focused changes,
test evidence, and honest reporting of unrun/failed checks. AI output is
untrusted until reviewed. A human maintainer owns architecture and policy
decisions, credentials/secrets, security-sensitive changes, external effects,
merge/release, and any action that can affect real data or systems.

Agents may analyze, implement, test, or review within their declared scope;
they cannot approve their own material changes or convert an assumption into
production policy. Treat agent and skill artifacts as repository configuration
and verify their syntax against the selected Copilot client before claiming
they are valid or demonstrated.

## Alternatives considered

- Allow autonomous implementation and merge: rejected because it removes
  review and authorization boundaries required for security and correctness.
- Omit AI configuration: fails the TRS assessment deliverable.
- Treat instructions as enforcement: rejected; only executable checks,
  permissions, and human review provide enforcement.

## Consequences

- Copilot improves repeatability but cannot guarantee policy compliance.
- Required configuration may change with client conventions and needs an
  explicit compatibility check.
- A successful workflow demonstration requires an actual approved task and
  reviewed outcome, not merely files existing.

## Follow-ups

- **Phase 4 / repository maintainer:** select the target Copilot client and
  validate file names/frontmatter and applicable instruction discovery.
- **Phase 12 / assessment owner:** demonstrate one approved human-reviewed
  workflow and record the task, checks, review, and result.
