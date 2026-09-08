# Testing strategy

## Test behavior, not implementation trivia

A useful test locks a contract, invariant, state transition, parsing rule, persistence behavior, or user-visible outcome.

Avoid tests whose only purpose is to mirror private method calls.

## Preferred test pyramid

### Pure JVM tests

Use for:

- reducers/state machines;
- RuleEngine/policy;
- schedule/time logic;
- mapping/parsing;
- repository logic with fake data sources;
- coroutine cancellation/races with virtual time.

### Robolectric/local Android tests

Use only when Android APIs are required but a real device is not.

### Instrumented tests

Use for:

- AccessibilityService/window behavior;
- permissions;
- Room migration/device integration where needed;
- actual Activity/Compose integration;
- audio/system-service behavior;
- OEM/API-specific platform contracts.

## Fakes vs mocks

Prefer a small fake implementing the contract when it makes state and behavior explicit.

Use mocks for narrow interaction boundaries, not as a substitute for understanding state.

## Coroutine tests

Use `runTest`, `TestDispatcher`, and virtual time.

Required race patterns for concurrent state code:

- old job finishes after newer event;
- timer and user action fire at same logical time;
- owner destroyed while work in flight;
- duplicate event;
- process/repository initialized after event arrives.

## Flow tests

For nontrivial streams verify:

- initial value;
- distinct/update semantics;
- cancellation;
- no stale emissions after switching keys;
- lifecycle/sharing expectations where relevant.

## Visual tests

If screenshot/golden infrastructure exists, cover high-value states:

- light/dark;
- large font;
- loading/error/empty;
- compact/wide;
- critical dialogs/overlays.

Do not add brittle screenshots for every trivial component.

## Regression rule

For a bug fix:

1. reproduce the failure in a test or deterministic event fixture when practical;
2. verify the test fails before the fix;
3. implement the fix;
4. verify it passes;
5. add adjacent edge cases if the bug represents a class of races/temporal errors.

## Device matrix

Platform-sensitive apps should identify a minimal API/device matrix rather than relying on one emulator.

Document which tests were not run and why.
