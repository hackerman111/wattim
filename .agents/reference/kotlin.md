# Kotlin implementation rules

## General style

- Prefer code that exposes invariants through types.
- Prefer immutable `val` data and small focused functions.
- Use early returns for invalid/precondition cases instead of deeply nested branches.
- Name by domain responsibility, not implementation accident.
- Avoid magic numbers and strings. Use named constants or typed configuration when the value has semantics.
- Comments explain constraints, intent, platform quirks, or why a less-obvious solution exists.

## State modeling

Prefer:

```kotlin
sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Failed(val cause: Failure) : SyncState
}
```

Instead of:

```kotlin
var isRunning: Boolean
var hasError: Boolean
var isPaused: Boolean
```

when combinations have invalid or ambiguous meanings.

Use value classes/value objects for identifiers, durations, versions, and validated concepts when type confusion can cause bugs.

## Nullability

- Do not use `null` to encode multiple business states.
- Prefer sealed types for concepts such as `Permanent`, `Session`, `Timed`, `Unavailable`, `NotLoaded`.
- Avoid `!!` unless a preceding invariant makes failure impossible and the invariant is obvious locally.

## Functions

A function should do one coherent unit of work. Split when it mixes:

- validation and persistence;
- policy and Android side effects;
- formatting and I/O;
- mutation of multiple unrelated owners.

Do not create one-line wrapper functions with no semantic value.

## Collections

- Choose data structures based on access patterns.
- Avoid repeated `firstOrNull`, `filter`, and `groupBy` over large lists inside hot paths when a keyed/indexed representation is appropriate.
- Avoid creating intermediate collections in measured hot loops.
- Keep ordering semantics explicit if result correctness depends on priority.

## Errors

- Model expected failures as typed results where callers need to react.
- Use exceptions for exceptional/platform failures when that matches the API, but clean up partially acquired resources.
- Do not swallow exceptions silently.
- Do not catch `Throwable` unless process-control exceptions are intentionally handled and rethrown appropriately.

## Extensions

Use extension functions for behavior that naturally belongs to the receiver's conceptual API. Avoid global extension dumping grounds.

## Constants

A retry of 300 ms, timeout of 5 s, or page size of 100 is not self-documenting. Name it and state whether it is:

- a platform requirement;
- product requirement;
- measured tuning parameter;
- fallback heuristic.

## Generated/configured values

Do not duplicate SDK, Kotlin, AGP, dependency, or tool versions in prose. Read them from build files/version catalogs.
