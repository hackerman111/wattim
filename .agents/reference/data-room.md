# Data, Room, DataStore, and persistence

## Repository boundaries

A repository/store owns a coherent data concept and is the mutation gateway for that concept.

Do not create a single repository that becomes the application service locator for unrelated tables/settings/statistics.

## Room query design

Use SQL for work SQL is good at:

- filtering;
- counting;
- aggregation;
- grouping;
- ordering;
- joining indexed relations.

Avoid:

```text
Flow<List<EntireHistory>> -> ViewModel.count/filter/groupBy
```

for simple counters.

## Indexes

Create indexes based on actual queries, especially combinations such as:

- `(packageName, timestamp)`;
- foreign-key columns;
- status + timestamp;
- lookup identifiers.

Verify query plans for high-volume paths instead of adding indexes blindly. Extra indexes increase write cost.

## Observable time-dependent queries

Room re-runs an observable query when observed tables are invalidated, not because wall clock advanced.

A query such as:

```sql
WHERE expiresAt > :now
```

with `now` captured once will become stale.

Even replacing `:now` with SQLite `now` does not create a timer that re-executes the Flow.

Use one of:

- an explicit temporal scheduler and re-query/re-evaluate at boundaries;
- combine persistent data with a clock/tick only where UI truly needs periodic display refresh;
- persist an explicit state transition when the semantics require it.

Do not introduce a global minute poller for convenience.

## Transactions

Use a transaction when multiple persistent mutations must be observed atomically.

Do not assume two independent suspend DAO calls are atomic because they happen in one coroutine.

## Migrations

- Never use destructive migration for production user data unless product requirements explicitly allow data loss.
- Add migration tests for schema changes.
- Preserve semantic defaults for existing rows.

## DataStore/settings

Use typed keys/models. Avoid generic string storage for structured state when parsing mistakes can change behavior.

## Hot state

If a latency-sensitive policy needs a snapshot, maintain an immutable in-memory projection owned by one store. Do not create multiple independently mutable caches for the same records.

## Cleanup

Expired rows may be cleaned lazily or at meaningful events. Cleanup is not the same as correctness: policy must not depend on a periodic cleanup job running on time.
