# Android lifecycle and platform components

## Lifecycle ownership

For every Android resource identify acquire/release symmetry:

| Acquire | Required release |
| --- | --- |
| receiver registration | unregister |
| listener/callback | remove/unregister |
| audio focus | abandon |
| WindowManager view | remove |
| sensor subscription | unregister |
| location updates | remove |
| binder/service connection | disconnect/unbind when owned |
| coroutine scope | cancel |
| wake lock | release |

Cleanup must also run on partial initialization failure.

## Activity/Compose

- Do not store Activity/View/Context with an Activity lifetime in application-scoped singletons.
- Use lifecycle-aware Flow collection.
- Keep navigation side effects explicit and idempotent.
- Handle edge-to-edge and window insets deliberately.

## ViewModel

A ViewModel owns screen/business presentation state, not arbitrary application background work.

Do not inject a ViewModel into services or repositories.

## Service

A service should primarily adapt platform lifecycle/events to a domain coordinator or execute a clearly defined long-lived platform responsibility.

If a service owns policy evaluation, database aggregation, UI construction, timers, audio, package scanning, and statistics, split it.

On destruction/interruption:

- stop accepting new work;
- invalidate session/generation state;
- cancel owned jobs;
- release platform resources;
- stop subordinate foreground service if ownership requires it.

## Foreground service

Use only for user-visible ongoing work or where the Android platform requires it.

- Match foreground service type to actual work and target SDK requirements.
- Do not keep a foreground service alive merely to make process death less likely unless this is an explicit, policy-compliant product requirement.
- Avoid polling inside FGS.
- Keep notification updates cheap and `distinctUntilChanged`.

## Broadcast receivers

Keep `onReceive` short. Transfer longer work to the appropriate owner. Do not start unsupported background work from a receiver.

## Process death

Persist state based on semantics, not convenience.

Persist:

- user configuration;
- durable scheduled/timed grants if they must survive restart;
- database records.

Do not persist:

- current transient overlay phase;
- in-process object identity;
- callbacks/listeners;
- current coroutine jobs.

On reconnect/start, load a coherent initial snapshot before treating missing state as an `Allow`/success decision.

## Screen lock/unlock

For components that render overlays, animate, play/stop audio, use sensors, or run timers, explicitly define behavior for screen off and unlock.

Stop unnecessary animation and active media work when screen is off.

## Permissions

- Ask only for permissions needed for core behavior.
- A permission grant can be revoked; handle it as state.
- Prefer a narrower platform-specific capability over a broad special permission when both satisfy the use case.
- Do not assume a manifest declaration means runtime/platform access will succeed.
