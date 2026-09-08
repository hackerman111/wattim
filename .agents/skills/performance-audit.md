# Performance-audit workflow

1. Define one or more user-visible critical paths.
2. Record baseline with release/benchmark-like build and device/API details.
3. Trace where time is spent: I/O/IPC, database, allocation, algorithm, synchronization, Compose, startup initialization.
4. Generate hypotheses grouped by root cause rather than many textual variants.
5. Test the highest-impact/lowest-risk hypothesis first.
6. Change one major variable per measurement where possible.
7. Prefer less work over faster work.
8. Re-run correctness tests after optimization.
9. Report before/after p50/p95 or representative samples and limitations.
10. Do not merge a micro-optimization whose result is within noise unless it improves code for another reason.
