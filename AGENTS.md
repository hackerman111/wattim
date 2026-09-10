# Android Engineering Agent Guide

This file is the canonical entry point for coding agents working in this repository. It applies to the entire tree unless a nested `AGENTS.md` narrows the rules for a subtree.

## Operating priorities

Optimize in this order:

1. correctness and data/state consistency;
2. lifecycle safety and cancellation;
3. architecture and ownership boundaries;
4. measured user-perceived performance and battery cost;
5. readability and local simplicity;
6. brevity.

Do not trade correctness for fewer lines, architectural separation for fewer files, or measured performance for speculative micro-optimizations.

## Start here

1. Read `.agents/README.md` and load only the references relevant to the task. For complex work that may benefit from delegation, also read `.agents/reference/multi-agent.md`.
2. Inspect the affected module/package, neighboring implementations, tests, Gradle configuration, and current diff before editing.
3. Identify ownership of state, lifecycle, threading, persistence, and UI before changing behavior.
4. Make the smallest coherent change that fixes the actual cause. Do not mix unrelated cleanup into the same change.
5. Add or update tests for behavior changes. For bugs, add a regression test whenever the affected logic can be isolated.
6. Run the narrowest useful checks first, then broaden verification in proportion to the change.
7. Report what changed, what was measured, what checks ran, and what could not be verified. Never claim a check passed if it was not run.


## Multi-agent execution

- The primary agent owns acceptance criteria, decomposition, architectural decisions, integration, final verification, and the final report.
- Delegate only independent investigation, bounded implementation, testing, review, or measurement work. Do not spawn agents merely to increase parallelism.
- Every subagent receives an explicit task packet: goal, scope, write ownership, constraints, verification, and required return information.
- Read-only agents may inspect the same area concurrently. Two active agents must not edit the same file or the same mutable-state owner.
- Parallel writers require disjoint write sets; prefer isolated branches/worktrees when supported. Serialize overlapping edits.
- For ambiguous bugs, investigate in parallel first, decide the invariant/root cause centrally, then assign implementation.
- Worker agents must not silently broaden scope. Cross-boundary dependencies are reported to the primary agent instead of patched opportunistically.
- For meaningful multi-file, lifecycle, concurrency, architecture, or persistence changes, use an independent reviewer or verifier after implementation.
- The primary agent must inspect the integrated diff and rerun important checks on the integrated tree. A worker's report is evidence, not a substitute for integration verification.
- Worker agents should not recursively delegate by default. Keep the orchestration graph shallow and explicit.

Detailed protocol: `.agents/reference/multi-agent.md`
Workflow: `.agents/skills/multi-agent-task.md`

## Non-negotiable architecture rules

- Every mutable state has one clear owner. Expose immutable state; mutate through explicit commands/events/functions.
- Prefer unidirectional data flow. UI renders state and emits events; it does not mutate repositories or platform services directly.
- Do not use a `ViewModel`, `Service`, `Repository`, `Manager`, `Controller`, or singleton as a dumping ground for unrelated responsibilities.
- A production source file over ~300 non-generated lines triggers a mandatory decomposition review. Line count is a warning, not a goal: split by responsibility, ownership, lifecycle, or dependency boundary, not by arbitrary size.
- A class is invalid regardless of line count if it simultaneously owns multiple unrelated concerns such as UI rendering, persistence, platform lifecycle, networking, scheduling, and business policy.
- Prefer explicit state models (`sealed interface`, enums, value objects) over interacting boolean flags.
- Avoid generic `Utils`, `Helpers`, `Common`, or `Manager` containers. Name code after the responsibility it owns.
- Keep platform-independent policy and algorithms free of Android framework dependencies when practical.
- Do not introduce a new Gradle module, DI framework, architectural layer, abstraction, or interface unless it creates a real boundary: independent ownership, compile isolation, replaceable platform dependency, stable API surface, reuse, or testability.
- Do not create a shared abstraction merely because two snippets look similar. Share only a stable concept.
- The app/application module is the composition root. Lower-level modules must not depend on the app module.
- Feature-to-feature dependencies must be explicit and acyclic. Prefer contracts/navigation keys/events over reaching into another feature's implementation.

## Kotlin and coroutine defaults

- Kotlin is the default language for new code.
- Prefer immutable data and constructor injection.
- Never use `GlobalScope` or detached `CoroutineScope(...)` without an explicit owner and cancellation point.
- Inject or abstract dispatchers/clocks when timing or threading is relevant to correctness or tests.
- Do not hardcode `Dispatchers.IO` throughout domain code.
- Use structured concurrency. Child work must belong to an owner whose lifetime is obvious.
- Distinguish stale-result prevention from cancellation: cancellation is useful; identity/version/session checks are required when an old result could race with a new logical session.
- Use atomic StateFlow updates for read-modify-write: `MutableStateFlow.update { ... }`.
- Avoid blocking calls on the main thread.

## Android lifecycle defaults

- A component may only own work that cannot outlive that component unless ownership is deliberately transferred.
- Services, receivers, callbacks, observers, listeners, audio focus, windows, sensors, and registrations require symmetric release paths.
- Model process death, service reconnect, screen off/on, configuration changes, and permission revocation as expected states, not exceptional edge cases.
- Do not use timers, sleeps, or debounce windows as the primary correctness mechanism for lifecycle transitions.

## Data defaults

- Room/DataStore/network are data sources, not UI state owners.
- Repositories expose coherent data and centralize mutations for the data they own.
- Do not pull entire tables into memory to calculate simple counts/aggregates that SQL can compute.
- Add indexes for real query patterns and verify migrations preserve user data.
- Keep I/O out of latency-sensitive callbacks and rendering paths.
- Persist only state that must survive process death. Keep session-only state in memory.

## Compose and UI defaults

- Compose screens consume immutable UI state and emit events.
- Keep `ViewModel` acquisition at route/screen boundaries; reusable composables should not reach into DI or repositories.
- Hoist state to the lowest owner that needs to coordinate it.
- Reusable components must be previewable/testable without starting the whole application.
- Use the project design system for recurring semantic controls, typography, color, shape, spacing, iconography, interaction states, and motion.
- Do not create visually inconsistent one-off controls when an existing semantic component can be extended.
- Direct Material primitives are acceptable for low-level composition when no app-semantic component exists; do not wrap every primitive for abstraction's sake.
- Interactive targets must be at least 48dp in both dimensions unless a platform convention explicitly provides an accessible equivalent target.
- Support light/dark themes, font scaling, edge-to-edge/insets, IME, accessibility semantics, and compact/medium/expanded layouts where applicable.
- Every screen must define loading, empty, error, disabled, and transient states when those states are possible.
- Avoid ornamental UI that weakens hierarchy: arbitrary gradients, excessive pills/cards, random shadows, inconsistent corner radii, decorative borders, emoji as functional icons, or ad-hoc spacing.

## Performance and battery defaults

- Measure before micro-optimizing. First reduce total work, I/O, wakeups, allocations, event volume, and synchronization; then optimize hot code.
- Prefer event-driven work over polling.
- No periodic background loop, wake lock, exact alarm, or foreground service merely for convenience. Each must have a product or platform requirement.
- Use WorkManager for deferrable guaranteed work, not second/minute-scale UI timers.
- Keep startup lazy: do not initialize expensive subsystems before they are needed.
- For Compose, optimize recomposition only when measured: stable models, lazy-list keys, `derivedStateOf` when it reduces invalidations, deferred state reads, and draw-phase updates for high-frequency animation state.
- Critical startup/navigation paths should be covered by Macrobenchmark/Baseline Profile when the project scale justifies it.

## Testing defaults

- Prefer pure JVM tests for reducers, policies, repositories with fakes, parsing, time logic, and coroutine state machines.
- Prefer fakes over deep mock graphs. Mock platform boundaries only when a fake is impractical.
- Use `kotlinx-coroutines-test` and virtual time for timers and races.
- Use Turbine or equivalent for nontrivial Flow assertions when already available.
- Add screenshot/golden tests for meaningful visual states where the project supports them.
- Add instrumentation tests only for behavior that genuinely requires Android framework/device integration.
- Regression tests must cover stale callbacks, process/service lifecycle, temporal boundaries, and multiple rapid events when those are part of the bug class.

## Change discipline

- Preserve unrelated work in a dirty worktree.
- Do not reformat, rename, or modernize unrelated files.
- Do not duplicate version numbers from Gradle/version catalogs in agent documentation. Build files are the source of truth.
- Comments explain why, constraints, invariants, or surprising platform behavior. Do not narrate obvious code or leave task-history residue.
- Do not suppress lint/compiler warnings unless the underlying cause is understood and documented.
- Prefer compile-time/lint enforcement for stable architectural rules instead of relying only on prose.

## Task-specific references

Load only what is needed:

- Architecture/module boundaries: `.agents/reference/architecture.md`
- Kotlin implementation quality: `.agents/reference/kotlin.md`
- State/concurrency/coroutines: `.agents/reference/concurrency-state.md`
- Android lifecycle/platform components: `.agents/reference/android-lifecycle.md`
- Data/Room/DataStore: `.agents/reference/data-room.md`
- Performance: `.agents/reference/performance.md`
- Battery/background work: `.agents/reference/battery.md`
- Compose engineering: `.agents/reference/compose.md`
- UI design engineering: `.agents/reference/ui-design-engineering.md`
- Testing: `.agents/reference/testing.md`
- Security/privacy: `.agents/reference/security-privacy.md`
- Build/release: `.agents/reference/build-release.md`
- Final review checklist: `.agents/reference/review-checklist.md`

Task workflows:

- Multi-agent task: `.agents/skills/multi-agent-task.md`
- Bug fix: `.agents/skills/bug-fix.md`
- New feature: `.agents/skills/add-feature.md`
- Architecture refactor: `.agents/skills/architecture-refactor.md`
- Performance audit: `.agents/skills/performance-audit.md`
- Battery audit: `.agents/skills/battery-audit.md`
- UI review: `.agents/skills/ui-review.md`

Special profile for accessibility/overlay protection apps such as Wattim:

- `.agents/profiles/accessibility-protection.md`
