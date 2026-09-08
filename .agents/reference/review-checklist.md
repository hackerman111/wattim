# Final engineering review checklist

## Architecture

- [ ] One owner for each mutable state.
- [ ] No new god class/file.
- [ ] Dependency direction remains acyclic.
- [ ] New abstraction/module has a real boundary.
- [ ] Platform-independent logic is isolated where valuable.

## Correctness/lifecycle

- [ ] Stale async result cannot mutate newer logical state.
- [ ] Acquire/release paths are symmetric, including exceptions.
- [ ] Process death/reconnect behavior is defined.
- [ ] Screen off/on behavior is defined if relevant.
- [ ] Timed state has an explicit expiry/boundary mechanism.

## Concurrency

- [ ] No detached scopes.
- [ ] StateFlow read-modify-write is atomic.
- [ ] Timers and callbacks are session/version aware when required.
- [ ] Tests cover duplicate/late/out-of-order events when relevant.

## Data

- [ ] No unnecessary full-table materialization.
- [ ] Queries/indexes match access patterns.
- [ ] Persistent vs session-only state is intentional.
- [ ] Migration/transaction semantics are correct.

## UI

- [ ] Primary hierarchy is clear.
- [ ] Design-system tokens/components are consistent.
- [ ] 48dp touch targets.
- [ ] Large font, dark theme, insets/IME considered.
- [ ] Loading/error/empty states are complete.
- [ ] No decorative inconsistency or arbitrary styling values.

## Performance/battery

- [ ] No new polling/wakeup without justification.
- [ ] Hot path contains no accidental I/O/IPC.
- [ ] Expensive work is lazy/bounded.
- [ ] Measured claims include baseline and method.
- [ ] Animation stops when not visible.

## Security

- [ ] No secrets or sensitive logs.
- [ ] Permissions/components are minimal.
- [ ] Untrusted inputs validated.

## Verification

- [ ] Relevant tests added/updated.
- [ ] Narrow checks run.
- [ ] Broader checks run in proportion to risk.
- [ ] Diff inspected for unrelated changes.
- [ ] Unverified items reported honestly.
