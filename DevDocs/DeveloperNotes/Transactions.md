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

## Transactions in tests

This is covered in the 'Testing' section of
[CodingStandards.md](GettingStarted/CodingStandards.md).
