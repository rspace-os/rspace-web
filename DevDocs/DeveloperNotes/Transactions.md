# Transactions

This document describes how database transactions are he handled in
RSpace.

## Transaction declarations

Transactions are normally declared using Spring AOP configuration to wrap all
service classes matching the pointcut
```
pointcut="execution(* *..service.**Manager.**(..))" '
```
with a TransactionManager.

When writing a new service interface in `com.researchspace.service`, prefer the
`Manager` suffix so it is covered by this pointcut automatically.

Some services do not fit the `Manager` naming pattern. These may declare their
transaction boundary explicitly with Spring's `@Transactional` annotation.
`TransactionAdviceStartupCheck` runs after application-context refresh and
fails startup if an instantiated `@Transactional` service is missing transaction
advice. This guard is important because beans created too early in the Spring
lifecycle can otherwise miss annotation-driven advisors.

Both mechanisms are set up in `src/main/resources/applicationContext-service.xml`.

A third, rarer mechanism exists: demarcating a transaction **programmatically**
with Spring's `TransactionTemplate`. Reach for it only when a method has to run
partly inside a transaction and partly outside one, which neither declarative
mechanism can express.

`InventoryIdentifierExternalUpdateService` is the worked example (RSDEV-1251,
ADR 0008). It rebuilds a PIDINST payload inside a short read-only transaction,
because the mapping adapter is `Propagation.MANDATORY` and walks lazy
associations, then closes that transaction before making the provider HTTP call,
so an external exchange never pins a pooled JDBC connection. Two constraints
come with the pattern and are easy to lose in a later refactor:

- The class must **not** be a `*Manager` and must **not** carry `@Transactional`.
  Either would wrap the whole method, defeating the point. It follows that
  `TransactionAdviceStartupCheck` does not cover such a class: the check scans
  `@Transactional` beans, so a programmatic boundary is invisible to it and a
  refactor that "tidies" the template into an unadvised annotated method would
  fail silently rather than at startup.
- Set the propagation explicitly. `TransactionTemplate` defaults to `REQUIRED`
  like everything else, so it will silently **join** a caller's transaction, and
  then `setReadOnly` is ignored, the boundary never closes, and an exception
  marks the caller's transaction rollback-only. Use `REQUIRES_NEW` when the
  point of the template is that this work has its own boundary.

Older programmatic uses exist in `AbstractCustomLiquibaseUpdater` and
`BlobMigrationBase`, which run outside the normal request lifecycle.

### Transaction behaviour

Uses Spring defaults which is `Transaction.REQUIRED`. This enables service
methods to call each other without generating new transactions.

## Creating transactions

DAO methods can assume that the transactions are already handled and set
up, and should not manage transactions. A transaction might include many
DAO method calls that should either all succeed or all fail. So within
DAO methods, using `sessionFactory.getCurrentSession()` should always work.
Calling a DAO method direct from a controller will fail.

In controller methods, each service call will run in a complete
transaction, so beware calling many fine-grained service methods to
achieve a unit of work - if one fails, the application can be left in an
inconsistent state - consider writing a new service method that runs the
unit of work in a single transaction.

## Envers revisions and inventory version bumps

Hibernate Envers writes **one revision per audited entity per transaction**,
storing only the entity's final state. An inventory record's user-facing
`version` field must therefore advance at most once per record per
transaction: a second bump would move the version past any revision carrying
it, leaving that version with no resolvable snapshot (RSDEV-1319).

Worked example of the bug. One HTTP request is one transaction, and
`POST /api/v1/subSamples` with `numSubSamples: 3` saves the parent sample
three times inside it:

```text
POST /subSamples { sampleId: 123, numSubSamples: 3 }     <- tx starts
  createNewSubSamplesForSample
    3x addNewApiSubSampleToSample
        -> saveDbSampleUpdate -> increaseVersion()       <- old code: 3 bumps
tx commits -> Envers writes ONE sample revision (final state only)
```

The live sample then said `version: 4` while history held revisions only for
v1 and v4. Versions 2 and 3 were never a committed state, so
`GET /samples/123/revisions/{n}` found nothing for them and a version pin
silently degraded to live data. The bump cannot simply move outside the
transaction either: the version is a column on the audited entity, so a
separate follow-up transaction writes a second revision and leaves the
revision holding the real content change stamped with the *old* version,
corrupting that snapshot instead of gapping it.

The fix: every inventory manager routes bumps through
`InventoryApiManagerImpl.increaseVersionOncePerTransaction`, which delegates
to `TransactionScopedVersionBumpGuard`. The guard remembers which records
already bumped in the current transaction (a transaction-bound set keyed by
global identifier, so `SA5` and `SS5` never collide) and drops the second and
later bumps. The set is removed on completion (commit or rollback) and
detached/reattached around suspension, so a nested `REQUIRES_NEW` transaction
gets its own set and its own bump. Each lifecycle rule is pinned by a plain
unit test in `TransactionScopedVersionBumpGuardTest`.

Never call `increaseVersion()` on an inventory record directly; route bumps
through the helper.

## Transactions in tests

This is covered in the 'Testing' section of
[CodingStandards.md](GettingStarted/CodingStandards.md).

Note that a `SpringTransactionalTest` runs the whole test in one transaction,
so a test that edits the same inventory record twice sees its version advance
only once (see the section above).
