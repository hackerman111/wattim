# Data-local agent rules

- This subtree owns persistence/data-source behavior only.
- Keep business presentation and Compose out of data modules.
- Repositories expose coherent immutable data and mutation operations.
- Use SQL for filtering/counting/aggregation; avoid full-table materialization for counters.
- Add/verify indexes based on real query patterns.
- Transactions must cover persistent mutations that callers require to be atomic.
- Observable queries with time predicates need an explicit temporal invalidation strategy.
- Migrations must preserve production user data unless destructive behavior is explicitly required.
- Do not create multiple mutable caches for the same source-of-truth records.
