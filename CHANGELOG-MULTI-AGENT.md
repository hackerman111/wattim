# Multi-agent bundle changes

## Added

- `.agents/reference/multi-agent.md`: delegation, file ownership, workspace, integration, and verification protocol.
- `.agents/skills/multi-agent-task.md`: compact orchestration workflow.
- `.agents/agents/android-orchestrator.md`: primary Flash coordinator.
- `.agents/agents/codebase-explorer.md`: read-only repository mapper.
- `.agents/agents/lifecycle-concurrency-investigator.md`: read-only Android race/lifecycle investigator.
- `.agents/agents/android-implementer.md`: bounded production-code writer.
- `.agents/agents/test-engineer.md`: test writer/verifier.
- `.agents/agents/architecture-reviewer.md`: read-only architecture reviewer.
- `.agents/agents/code-reviewer.md`: read-only post-change reviewer.
- `.agents/agents/build-verifier.md`: read-only Gradle/build verifier.
- `.agents/agents/performance-battery-auditor.md`: read-only performance/battery investigator.

## Changed

- Root `AGENTS.md` now defines primary-agent ownership, safe parallelism, write-conflict rules, and final integration verification.
- `.agents/README.md` routes multi-agent tasks to the new protocol and workflow.
- `README.md` documents Antigravity multi-agent usage.
- `INSTALL-WATTIM.md` includes a recommended Wattim delegation sequence.
- `.agents/reference/review-checklist.md` includes multi-agent integration checks.
- `SOURCES.md` includes the Antigravity subagent/model documentation used for the agent definitions.

## Current model policy

Every supplied custom agent uses `model: flash`. No Claude-specific role or dependency is included in this version.
