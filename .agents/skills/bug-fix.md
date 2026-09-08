# Bug-fix workflow

1. Reproduce or trace the failing state transition.
2. Identify the owner of the incorrect state and the earliest point where the invariant breaks.
3. Classify the bug: deterministic logic, stale state, race, lifecycle, persistence, temporal boundary, platform/OEM, UI-only.
4. Prefer fixing the invariant/ownership model over adding delay/debounce/boolean patches.
5. Add a regression test or event fixture. For races, include at least one reordered/stale completion scenario.
6. Implement the smallest architectural fix that removes the cause.
7. Remove obsolete workaround code made unnecessary by the fix.
8. Run subsystem tests, then broader checks.
9. State remaining platform-specific uncertainty explicitly.
