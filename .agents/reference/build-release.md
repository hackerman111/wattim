# Build, dependency, and release rules

## Source of truth

Use the checked-in Gradle wrapper.

Read SDK/tool/dependency versions from:

- version catalog;
- build scripts;
- wrapper properties;
- repository build logic.

Do not duplicate mutable version facts into `AGENTS.md`.

## Dependencies

Before adding a library:

- confirm existing project dependency cannot do the job;
- evaluate maintenance, size, transitive dependencies, platform support, and license;
- avoid adding a framework for one helper function.

Centralize versions according to repository conventions.

## Build logic

If multiple modules repeat Android/Kotlin/Compose configuration, prefer convention plugins/build logic rather than copy-pasted Gradle blocks.

Do not introduce complex build logic to deduplicate two trivial lines.

## Static analysis

Use existing formatter/lint/detekt/Android lint rules. Stable architecture constraints should be enforced by custom lint/Gradle checks when violations are costly and detectable mechanically.

Examples:

- no feature -> app dependency;
- no raw design-system forbidden controls;
- no direct dispatcher use in constrained layers;
- no forbidden API in certain modules.

## Release

- release must not use debug signing credentials;
- signing secrets remain outside Git;
- verify target/compile SDK requirements against current store policy when preparing release;
- test release/R8 build, not only debug;
- keep ProGuard/R8 consumer rules with the library/module that owns the reflective/JNI behavior.

## Verification strategy

Run narrow checks first, for example module unit test/lint, then broader repository checks.

Never hide an unverified release path behind a successful debug build.
