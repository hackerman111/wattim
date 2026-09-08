# Feature-local agent rules

This subtree implements one user-facing feature.

- Keep feature-specific models, state holders, UI, and orchestration local unless another feature has a real contract-level need.
- Do not depend on another feature's implementation.
- UI consumes immutable state and emits typed actions/events.
- Keep `Route`/ViewModel wiring separate from reusable `Screen` composables.
- Do not introduce direct Room/network/system-service access from composables.
- Move shared code out only when the concept is genuinely shared and stable.
- Add tests for feature policy/state transitions and previews/screenshot tests for important visual states.
- Any file over ~300 production LOC requires responsibility review.
