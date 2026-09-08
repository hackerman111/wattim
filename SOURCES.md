# Sources and synthesis notes

This bundle is original guidance synthesized from patterns observed in established Android/Kotlin repositories and current Android documentation. It is not a copy of any repository's instructions.

## Repositories reviewed

### Android / Now in Android

https://github.com/android/nowinandroid/blob/main/AGENTS.md
https://github.com/android/nowinandroid/blob/main/docs/ModularizationLearningJourney.md

Useful patterns adopted:

- Compose + UDF + Flow state holders;
- feature/core modularization and explicit dependency direction;
- design-system separation;
- Macrobenchmark, Baseline Profile, Compose compiler metrics;
- build/test commands belong in agent guidance.

### Thunderbird for Android

https://github.com/thunderbird/thunderbird-android/blob/main/AGENTS.md

Useful patterns adopted:

- explicit module/API/internal dependency boundaries;
- architecture docs/ADRs before structural changes;
- design-system enforcement for UI consistency;
- small focused changes;
- behavior tests for bug fixes;
- prefer fakes over large mock graphs.

Scale-specific API/internal module splitting is not forced by this bundle for small applications.

### DuckDuckGo Android

https://github.com/duckduckgo/Android/blob/develop/AGENTS.md

Useful patterns adopted:

- do not duplicate mutable SDK/tool versions in AGENTS files;
- large-repo API/implementation boundaries;
- architecture constraints should be machine-enforced when possible;
- comments explain why rather than narrating code;
- cross-cutting/public API changes require deliberate design review.

The bundle does not copy DuckDuckGo-specific DI/module rules.

### Nextcloud Android

https://github.com/nextcloud/android/blob/master/AGENTS.md

Useful patterns adopted:

- practical ~300-line source-file review trigger;
- separate models/states instead of god files;
- fail-fast control flow;
- avoid boolean flags for conceptual state;
- Material 3, light/dark theme consistency;
- no hardcoded strings/colors/dimensions.

### Compose Android Template

https://github.com/ashtanko/compose-android-template/blob/main/AGENTS.md

Useful pattern adopted strongly:

- compact root AGENTS file plus progressively loaded `.agents/reference/*` and task workflows;
- load only task-relevant context;
- build logic/convention reuse;
- explicit performance/security/testing references.

### JetBrains Kotlin

https://github.com/JetBrains/kotlin/blob/master/AGENTS.md

Useful pattern:

- root agent file can be a compact router to canonical detailed guidelines rather than duplicating everything.

## Android documentation reviewed

- Architecture / SSOT / UDF: https://developer.android.com/topic/architecture
- UI layer: https://developer.android.com/topic/architecture/ui-layer
- Compose performance: https://developer.android.com/develop/ui/compose/performance
- Compose performance best practices: https://developer.android.com/develop/ui/compose/performance/bestpractices
- Compose stability: https://developer.android.com/develop/ui/compose/performance/stability
- Compose accessibility / 48dp touch target: https://developer.android.com/codelabs/jetpack-compose-accessibility

## Deliberate departures

This bundle intentionally does not mandate:

- Hilt/Koin/Dagger/Metro;
- MVI as a named framework;
- Clean Architecture everywhere;
- api/impl Gradle split for every feature;
- a domain layer for trivial pass-through logic;
- a wrapper around every Material component;
- a 300-line hard build failure.

Those patterns can improve large repositories but can also create needless indirection in a smaller project. The enforced invariants are ownership, dependency direction, explicit state, structured concurrency, measured performance, event-driven background behavior, and design-system consistency.
