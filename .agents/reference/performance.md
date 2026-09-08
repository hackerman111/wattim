# Performance engineering

## Order of optimization

Optimize in this order:

1. remove unnecessary work;
2. improve algorithm/data structure complexity;
3. remove unnecessary I/O and IPC;
4. reduce allocations/copies and repeated transformations;
5. reduce synchronization/contention;
6. optimize rendering/recomposition;
7. micro-optimize instructions only after profiling.

## Measure release-like builds

Debug performance is not representative. Use release/benchmark variants for meaningful numbers.

Record before/after metrics and device/API/build variant.

## Critical paths

Define a critical path with clear start/end markers, for example:

```text
foreground event -> policy decision
button tap -> first rendered result
cold start -> first interactive frame
DB mutation -> UI visible state
```

Measure p50/p95 where repeated samples are feasible.

## Android tools

Use as applicable:

- Macrobenchmark;
- Baseline Profiles;
- Perfetto/System Trace;
- Android Studio CPU/Memory profiler;
- Compose compiler metrics/reports;
- JankStats/frame timing;
- benchmark module for isolated hot algorithms.

## Startup

- Avoid eager singleton initialization.
- Delay package scans, database aggregation, network clients, heavy parsers, and analytics until needed.
- Keep `Application.onCreate` minimal.
- Generate/update Baseline Profiles for stable critical user journeys when startup/navigation performance matters.

## IPC

Binder/package-manager/accessibility/system-service calls can dominate tiny local computation. Cache stable metadata and reduce event volume before micro-optimizing Kotlin.

## Compose

When measured recomposition/jank is a problem:

- provide stable keys to lazy lists;
- use immutable/stable UI models;
- use `derivedStateOf` only when it meaningfully reduces invalidations;
- defer rapidly changing state reads to layout/draw where possible;
- use lambda modifiers for frequently changing offsets/sizes;
- avoid expensive sorting/filtering in composition; prepare UI models before render;
- do not allocate formatters/parsers on every frame;
- prefer draw-phase animation for high-frequency visual state.

Do not mark a type `@Stable`/`@Immutable` to silence metrics unless the contract is actually true.

## Memory

- Avoid retaining Activity/View/large bitmap contexts beyond lifetime.
- Bound caches by count/size or lifecycle.
- Stream large data rather than loading it all when possible.
- Avoid duplicating large domain and UI collections when mapping can be incremental or scoped.

## Acceptance

A performance change must state:

- baseline;
- measurement method;
- result;
- variance/limitations;
- whether behavior changed.
