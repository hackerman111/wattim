# Accessibility / intervention / overlay protection profile

Apply this profile to apps such as Wattim that observe foreground application transitions and show intervention/block overlays.

## Core architecture

Use a single-writer event-driven core:

```text
Accessibility callbacks
  -> ForegroundTracker / EventNormalizer
  -> Channel<ProtectionEvent>
  -> InterventionCoordinator / reducer
  -> typed effects
  -> OverlayHost / AudioGuard / persistence / scheduler
```

The AccessibilityService itself is an adapter, not the business-logic owner.

## Session identity

Every protected-app entry has a unique `sessionId`.

All delayed work, overlay callbacks, audio ownership, re-intervention, and grant/revoke effects must be tied to that identity when they can outlive the initiating callback.

Do not use `currentForegroundPackage: String?` as the state machine.

## Foreground detection

- `TYPE_WINDOW_STATE_CHANGED` may be a fast signal.
- Do not treat every `TYPE_WINDOWS_CHANGED.event.packageName` as a confirmed foreground-app transition.
- Use window resync for ambiguous/reconnect/unlock transitions when supported.
- Keyboard/system-window filtering should be based on window/type semantics rather than package-name substring heuristics where possible.

## Adaptive accessibility subscription

Reduce event volume at the source.

Idle:

- subscribe only to enabled target packages and required event types when this still allows detecting target entry.

Active target session:

- temporarily widen package visibility/event subscription enough to detect exit to any package.

After confirmed exit:

- narrow again.

Use `notificationTimeout` only after measuring interception latency.

## Hot path

Target path:

```text
normalized foreground event
-> in-memory immutable policy lookup
-> pure RuleEngine
-> reducer transition
-> overlay effect
```

No Room query, package-label lookup, statistics aggregation, or arbitrary suspend work between event normalization and policy decision.

## Grants

Use distinct types:

- session permit: RAM only, tied to sessionId;
- timed permit: persistent `expiresAt`;
- disabled target: target configuration, not a permanent permit.

Never overload `expiresAt = null` to mean several different concepts.

A stale revoke from an old session must never delete a new permit.

## Temporal boundaries

Use one nearest-boundary scheduler for relevant current-session deadlines:

- re-intervention;
- timed permit expiry;
- global pause expiry;
- schedule start/end;
- hard-block end.

Timer callback emits `TemporalBoundaryReached`; it never calls `OverlayHost.show()` directly.

Every boundary causes fresh policy evaluation.

No minute polling.

## Schedules

- define explicit priority when intervention and hard-block schedules overlap;
- normalize overnight intervals correctly across day-of-week boundary;
- if several hard blocks apply, effective end must represent the complete block interval, not arbitrary list order.

## Overlay

Use one `OverlayHost` per active session and update `OverlayUiState` rather than remove/add the entire window for every phase/dialog transition.

Overlay operations are idempotent by `sessionId`.

On exit, prefer:

```text
ExitPressed -> Exiting -> HOME -> confirmed target exit -> hide overlay/release audio
```

rather than hiding first and briefly exposing/resuming the target app.

Evaluate `TYPE_ACCESSIBILITY_OVERLAY` as the preferred backend before requiring broad application overlay permission, subject to device/API behavior tests.

## Audio

`OverlayHost` does not own package-specific audio hacks.

Use `AudioGuard`:

1. request appropriate transient audio focus;
2. check result;
3. send one media pause when product behavior requires it;
4. hold/release by session identity.

Do not mutate global `STREAM_MUSIC` volume in the normal path.

If Android 10/11 + a specific target app requires a fallback, isolate it behind a bounded, cancellable, package/platform-specific strategy and feature flag.

## Lifecycle

Screen off/service interrupt/destruction must:

- invalidate active session as appropriate;
- cancel scheduler;
- hide overlay;
- release audio/window/listener resources;
- stop animation.

On reconnect/unlock, wait for coherent policy readiness and perform foreground resync.

## Startup readiness

Repository collectors loading asynchronously must not cause an early target event to be evaluated against an empty/default policy.

Use a `Loading -> Ready(snapshot)` barrier and re-evaluate the latest pending foreground target after readiness.

## Performance/battery acceptance

Measure:

- number of accessibility callbacks during non-target navigation before/after adaptive subscription;
- foreground-event -> policy-decision latency;
- event -> overlay attach request latency;
- idle CPU/wakeups;
- screen-off behavior.

No periodic wakeups, exact alarms, or polling loops in idle protection state.

## Regression tests

At minimum:

- session #1 async result finishes during session #2;
- Exit followed by stale same-package event;
- timed emergency expires while user remains in target;
- global pause active when re-intervention deadline fires;
- disable then re-enable target leaves no permanent permit;
- process restart preserves timed permit but not session permit;
- old revoke cannot delete new permit;
- overlapping hard-block/intervention priority;
- overnight schedule boundary;
- screen off during overlay;
- service interrupt then real reopen;
- policy not ready when target event arrives.
