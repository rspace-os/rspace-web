/**
 * Compiles collection queries for Blaze/Hibernate.
 *
 * <p>{@link com.researchspace.dao.query.RsqlCollectionQuery} is the public entry point. Expression
 * traversal routes selectors to relationship or runtime-field storage compilers. Value predicates
 * are delegated through {@code RsqlFieldPredicateCompilerRegistry}; parameters, aliases,
 * subqueries, and complexity counters remain in compilation state. Nested scopes share one name
 * allocator while keeping their subqueries inside the correlated parent. Independent compilations
 * are deterministic; use {@code newCompilation()} when composing several predicates in one query,
 * or use distinct explicit prefixes for independently compiled restrictions. Compose predicates
 * through {@code Predicate.and/or} to reject conflicting bindings, then use {@code applyTo} to
 * install their expression, recursive subqueries, and parameters together.
 *
 * <p>The parser limits caller filters to 10 LIKE-producing comparisons. Compilation separately
 * limits the generated LIKE/NOT LIKE predicates to 50 across direct and nested caller filters: one
 * word-based LIKE comparison can generate several predicates. Trusted access constraints do not
 * consume this caller budget.
 *
 * <p>To add a runtime field type, define its parsing, serialization, and permitted operators in the
 * collection model, then explicitly register its storage comparison semantics. Canonical ISO dates
 * and times reuse scalar comparisons. Numbers stored as text use numeric conversion; choice arrays
 * use membership predicates. Add a new predicate compiler only for different SQL semantics, and
 * exercise the new type through the public query API. Keep SQL/Blaze dependencies in this package,
 * outside the collection model.
 *
 * <p>Native scalar properties use the scalar compiler after selector operator validation. A new
 * storage shape needs an explicit binding/dispatch extension as well as a value compiler; adding a
 * registry entry alone does not introduce new storage support. Runtime presence and correlation,
 * and relationship authorization, remain the storage compilers' responsibility.
 */
package com.researchspace.dao.query;
