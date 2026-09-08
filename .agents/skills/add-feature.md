# Add-feature workflow

1. Define user-visible states and actions before code placement.
2. Decide the owner of durable data, session state, side effects, and navigation.
3. Reuse an existing feature/module boundary when appropriate; do not create a module automatically.
4. Define API contracts only for actual cross-boundary communication.
5. Build domain/state logic as pure Kotlin where practical.
6. Build UI against immutable state and typed events.
7. Add loading/error/empty/disabled states and accessibility behavior.
8. Add tests at the cheapest layer that fully verifies behavior.
9. Measure performance only for paths with plausible cost; do not pre-optimize trivial code.
10. Review whether the feature adds background work, new permission, FGS, alarm, wakeup, or package scan; justify each explicitly.
