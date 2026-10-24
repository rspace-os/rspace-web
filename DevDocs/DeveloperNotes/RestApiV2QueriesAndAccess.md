# REST API v2 queries and access

## Compile and combine query predicates

`RsqlCollectionQuery.Subquery` carries nested subqueries in the scope where their correlated
aliases are visible. Apply a compiled predicate with `Predicate.applyTo(query)`, or
`Predicate.applyTo(nestedBuilder, rootQuery)` inside an existing subquery. Both forms register
nested subqueries and bind parameters on the root query.

Combine compiled predicates with `Predicate.and(...)` or `Predicate.or(...)`. They ignore null
predicates and reject conflicting parameter or subquery bindings. Use one
`RsqlCollectionQuery.newCompilation()` session when compiling several predicates for the same
operation; its allocator keeps generated names distinct across nested scopes. Independent
compilations need distinct prefixes if combined. Keep sessions local to an operation, never on a
reusable DAO.

## Signed global IDs

A global ID in a relationship filter or write is a prefix and an unversioned ID. The ID can be
negative, as seeded and baseline rows are (for example `US-3`). A versioned ID such as `IN12v2` is
rejected.
