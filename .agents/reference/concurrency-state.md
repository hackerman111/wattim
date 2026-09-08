# Concurrency, Flow, and state ownership

## Structured concurrency

Every coroutine needs an owner and a cancellation boundary.

Allowed examples:

- `viewModelScope` for screen state work;
- `lifecycleScope` for UI lifecycle work when a ViewModel is inappropriate;
- a service-owned `CoroutineScope(SupervisorJob() + dispatcher)` cancelled in service destruction;
- a coordinator-owned scope whose lifetime is explicitly tied to the coordinator.

Avoid:

```kotlin
CoroutineScope(Dispatchers.IO).launch { ... }
GlobalScope.launch { ... }
```

inside arbitrary functions.

## Single writer

If multiple callbacks can mutate one logical state, route them through one owner.

Preferred for complex lifecycle/state machines:

```text
callbacks -> Channel<Event> -> single consumer -> reducer -> effects
```

This is often safer than placing a `Mutex` around a god object.

## Session/version identity

Cancellation alone is insufficient when old work may complete after a new logical session starts.

Use identity/version checks:

```kotlin
val session = currentSession
val result = load()
if (currentSession?.id != session.id) return
apply(result)
```

Prefer events/effects that carry `sessionId` or `revision` so stale work cannot mutate new state.

## MutableStateFlow

For read-modify-write use:

```kotlin
state.update { old -> old.copy(...) }
```

not:

```kotlin
state.value = state.value.copy(...)
```

when concurrent writers are possible.

Expose `StateFlow<T>`, not `MutableStateFlow<T>`.

## Flow design

- Use `mapLatest`/`flatMapLatest` when obsolete work should cancel on new input.
- Use `distinctUntilChanged` before expensive downstream work when equality semantics are meaningful.
- Avoid collecting the same cold expensive Flow independently in many places when a scoped shared state is appropriate.
- Choose `SharingStarted` deliberately; do not keep upstream sensors/database/network work alive forever by accident.
- Avoid creating a Flow for a one-shot operation unless stream semantics add value.

## UI collection

Collect lifecycle-aware in UI. Do not keep collectors active when the UI cannot use their result unless the underlying work is intentionally application-scoped.

## Dispatchers

- Main: UI state and Android APIs requiring main thread.
- IO: blocking I/O that cannot be replaced by suspending APIs.
- Default: CPU work.

Inject dispatchers at boundaries when tests, policy, or deterministic scheduling depend on them.

Do not switch to IO around Room suspend functions merely by habit; Room already handles query execution off main when configured through its coroutine APIs. Measure before adding context hops.

## Time

Separate:

- wall clock/calendar (`Instant`, `Clock`, timezone);
- monotonic elapsed duration (`SystemClock.elapsedRealtime` or abstraction).

Use virtual/fake time in tests. Avoid `delay`-based tests with real sleeping.

## Races checklist

Before finishing concurrent code, test:

- event A starts work, event B supersedes it, A finishes last;
- component is destroyed during work;
- process/service reconnect restores persistent state;
- two writers update different fields simultaneously;
- timeout/expiry fires exactly as user action occurs;
- duplicate callback/event arrives;
- callback arrives after resource release.
