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

`InventoryApiManagerImpl.increaseVersionOncePerTransaction` enforces this for
all inventory managers, tracking bumped records in a transaction-bound
resource keyed by global identifier. It is unbound on completion (commit or
rollback) and unbound/rebound around suspension, so a nested `REQUIRES_NEW`
transaction gets its own set. Never call `increaseVersion()` on an inventory
record directly; route bumps through this helper.

## Transactions in tests

This is covered in the 'Testing' section of
[CodingStandards.md](GettingStarted/CodingStandards.md).

Note that a `SpringTransactionalTest` runs the whole test in one transaction,
so a test that edits the same inventory record twice sees its version advance
only once (see the section above).
