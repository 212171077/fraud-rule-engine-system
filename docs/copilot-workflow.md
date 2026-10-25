# Controlled Copilot Workflow

Copilot instructions, agents, and skills are advisory context; they do not
enforce correctness or replace tests, code review, security review, or human
approval. See `.github/copilot-instructions.md`, `.github/instructions/`,
`.github/agents/`, and `.github/skills/`.

## Orchestrated pipeline

Start a task with the **orchestrator** agent. It delegates to the specialist
agents below, passes each result to the next agent, and routes review findings
back to **implementation**:

```
analysis-planning -> [user approval gate] -> implementation -> test-engineer
  -> code-review -> (CHANGES_REQUIRED: back to implementation) -> security-review
  -> (CHANGES_REQUIRED: back to implementation) -> final report -> human review
```

- Each agent ends with a defined hand-off block; reviewers end with
  `VERDICT: APPROVED` or `VERDICT: CHANGES_REQUIRED`. HIGH/MEDIUM findings
  (CRITICAL for security) block.
- The orchestrator stops for the user when the plan lists a material decision,
  and after 3 failed rework cycles at a review stage.
- Security review is run for changes touching authentication, authorization,
  configuration, secrets, data handling, logging, metrics, Kafka/DLQ, CI,
  containers, or a new boundary.
- Each agent file also declares `handoffs`, which clients that support them
  show as buttons for stepping through the same sequence manually. Support for
  `agents` and `handoffs` varies by Copilot client; where unsupported, the
  orchestrator or the user passes the hand-off block to the next agent.
- Every agent prints a start line (`<icon> [agent-name] started: ...`) and a
  finish line (`<icon> [agent-name] finished: <outcome>`). The orchestrator also
  prints `Stage n/total: delegating to <agent>` before each delegation and a
  completion line after, so the running agent is always visible.
- Agent approval is an automated check only. Human review remains required.

## Manual steps

For a non-trivial, approved engineering task:

1. Use **Analysis & Planning** to inspect requirements, relevant source/tests,
   assumptions, impacted components, failure cases, and validation steps.
2. Review the plan and obtain human approval for material business, security,
   privacy, destructive, or external-impact decisions.
3. Use **Full Implementation** to make the smallest complete change and update
   related tests, contracts, migrations, metrics, and documentation.
4. Use **Test Engineer** to identify and close meaningful coverage gaps without
   weakening assertions or hiding failures.
5. Run the required local checks and record exact commands and outcomes.
6. Use **Code Review** and, when security-relevant, **Security Review** as
   read-only independent checks; resolve or document findings.
7. Have a human maintainer review and approve the change. Report unresolved
   questions and unrun checks; never claim checks passed unless observed.

Do not use real customer data or present assessment assumptions as approved
policy. Never delegate secrets, repository data, or credentials to unapproved
external systems. Human maintainers retain ownership of architecture,
security, policy, external effects, merging, and release.

This document defines the workflow; recording a demonstrated use of the
repository agent profiles on an approved task remains a Phase 4 exit item.
