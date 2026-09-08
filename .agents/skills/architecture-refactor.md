# Architecture-refactor workflow

1. Write the current ownership/dependency graph before moving files.
2. List concrete problems: races, cycles, god object, untestable platform coupling, repeated I/O, build coupling.
3. Define target invariants and dependency direction.
4. Extract one boundary at a time while preserving behavior.
5. Add contract/regression tests before deleting old paths.
6. Keep one canonical implementation during migration; avoid long-lived parallel paths unless migration requires them.
7. Do not simultaneously rename every symbol/package unless necessary.
8. Re-measure build/runtime behavior if performance is part of the rationale.
9. Delete dead adapters/workarounds after all callers move.
10. Update nested AGENTS/ADR/docs if the architecture contract changed.
