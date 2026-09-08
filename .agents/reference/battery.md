# Battery and background-efficiency rules

## Primary rule

Battery optimization starts by reducing wakeups and total background work, not by making a polling loop 20% faster.

## Event driven first

Prefer:

```text
system/user event -> bounded work -> idle
```

over:

```text
while(true) { delay(...); poll() }
```

## Polling

Do not add periodic polling unless the platform provides no suitable event source and the product requires freshness at that cadence.

If polling is unavoidable:

- document why events cannot be used;
- use the slowest acceptable cadence;
- stop when data cannot be consumed;
- batch work;
- back off on failures;
- measure wakeups and CPU.

## WorkManager

Use for deferrable, persistent, guaranteed work with constraints.

Do not use WorkManager for second/minute-scale interactive timers or in-process UI scheduling.

## Alarms

Exact alarms are exceptional. Use only when exact wall-clock execution is user-visible and genuinely required.

Do not use AlarmManager as a replacement for an in-process scheduler while the owning process/component is active.

## Wake locks

Avoid explicit wake locks. If unavoidable, acquire for the shortest scope and release in `finally`/structured resource ownership.

## Sensors/location/network

- subscribe only at needed precision/frequency;
- unregister promptly;
- batch when supported;
- prefer passive/system callbacks;
- avoid frequent network sync for unchanged data.

## Accessibility/event-heavy services

Reduce callbacks before processing them:

- narrow event types;
- narrow package subscriptions when semantics allow;
- use platform coalescing/notification timeout only when measured latency remains acceptable;
- dynamically widen subscriptions only while a session requires observing exit/transition events.

Avoid parsing node trees for events that can be resolved from window/package metadata.

## UI animation

Stop animation when not visible or screen is off. An overlay hidden behind lock screen should not keep 60 FPS work alive.

## Foreground services

A foreground service is not a battery optimization. Keep its work idle when no work is needed.

## Measurement

Use as applicable:

- `dumpsys batterystats`;
- Perfetto/System Trace;
- wakeup/alarm/job inspection;
- CPU time during controlled idle/navigation scenario.

Do not claim percentage battery savings without controlled measurement.
