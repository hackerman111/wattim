# Architecture and module boundaries

## Goal

Make ownership, dependency direction, state flow, and replacement boundaries obvious. Optimize for change isolation and correctness, not for the maximum number of layers or modules.

## Responsibility before file count

A unit should have one coherent reason to change.

Bad examples:

- `MainViewModel` also scans packages, writes Room, schedules alarms, formats UI strings, and owns navigation.
- `AppService` handles platform callbacks, policy evaluation, persistence, UI construction, statistics, and audio.
- `Utils.kt` accumulates unrelated extensions for files, time, strings, Android contexts, and networking.

Prefer responsibility names:

- `ForegroundTracker`
- `InterventionCoordinator`
- `PolicyEvaluator`
- `OverlayHost`
- `AudioGuard`
- `AppLabelResolver`
- `StatisticsRepository`

## God-file guardrails

- >300 production LOC: mandatory decomposition review.
- >5 injected dependencies: inspect whether the class coordinates too many responsibilities.
- >1 platform lifecycle plus >1 data source plus business policy in one class: split unless there is a compelling ownership reason.
- Several independent mutable booleans describing one conceptual state: replace with a state model.
- More than one unrelated public API family in a file: split by concept.

Do not split a cohesive algorithm merely to satisfy a line count.

## Package vs Gradle module

Use packages/files first when boundaries are local and build isolation is not valuable.

Create a Gradle module when at least one is true:

- the boundary has a stable API used by multiple features;
- independent build/test ownership materially reduces coupling;
- platform-independent code can become a Kotlin/JVM module;
- implementation must be hidden behind a contract;
- a large feature can compile/test independently;
- build performance benefits from isolating frequently changed code.

Do not create a module only because a diagram looks cleaner.

## Recommended dependency direction

For medium/large apps:

```text
app (composition root)
  -> feature/*
  -> platform wiring

feature/*
  -> core contracts / domain / design system / data contracts

core/data
  -> core/model
  -> local/remote data sources

core/designsystem
  -> no feature/data dependency

core/model/domain
  -> no Android dependency when practical
```

Rules:

- `core` never depends on `feature` or `app`.
- feature implementation does not reach into another feature implementation.
- app can wire implementations because it is the composition root.
- do not create circular Gradle or logical dependencies.

## API/implementation split

Use `api`/`impl` only when the boundary is real. A small app does not need every feature split into two modules.

When used:

- API contains contracts, navigation keys, stable domain models needed by consumers.
- implementation details remain `internal`.
- consumers depend on API, not implementation.
- composition root binds implementations.

## Domain layer

A domain/use-case layer is optional.

Introduce it when:

- business logic is reused by multiple state holders;
- policy is complex enough to deserve isolated tests;
- orchestration crosses repositories;
- platform-independent logic should be isolated.

Do not wrap every repository function in a one-line use case.

## State ownership

For every important state, answer:

- Who owns it?
- Who may mutate it?
- Who observes it?
- Does it survive process death?
- What is its identity/version/session?
- Which events invalidate it?

If the answers are unclear, redesign before adding more callbacks.

## Data flow

Prefer:

```text
Data source -> Repository/Store -> immutable state -> UI
UI event -> state holder/coordinator -> mutation/effect -> new immutable state
```

Avoid bidirectional object graphs where UI, service, repository, and singleton all update each other.

## Composition root

DI wiring belongs near the application composition root.

Do not pass Android `Context`, database instances, dispatchers, or service objects through domain contracts unless the contract genuinely represents that platform concern.

## Shared code

Before moving code to `core/common` or `shared` ask:

- Is the concept actually shared, or only duplicated twice today?
- Does sharing create a stronger dependency between unrelated features?
- Can the shared API remain stable?

Prefer local duplication over a bad universal abstraction.

## Architecture decision record trigger

Write a short ADR/design note before a change that:

- changes module dependency direction;
- creates a public cross-feature API;
- introduces a persistent storage format;
- adds a long-lived background component;
- changes the primary state-management model;
- introduces a new framework or DI system.
