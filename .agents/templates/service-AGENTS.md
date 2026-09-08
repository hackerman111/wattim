# Service/platform-local agent rules

- Android callbacks are adapters; route nontrivial policy/state transitions to a coordinator/state machine.
- No detached coroutine scopes.
- Explicitly own and release listeners, receivers, windows, audio focus, sensors, and timers.
- Keep I/O out of latency-sensitive callbacks; consume a prepared runtime snapshot where needed.
- Use session/version identity for callbacks/timers that can arrive late.
- No periodic polling unless event sources are insufficient and the product requires it.
- Define behavior for process/service restart, screen off/on, permission loss, and component destruction.
- Keep foreground service work idle when there is no work.
