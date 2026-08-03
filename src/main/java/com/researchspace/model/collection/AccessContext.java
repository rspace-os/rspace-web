package com.researchspace.model.collection;

import com.researchspace.model.User;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Per-request input to an access check.
 *
 * <p>{@code user} is null for an anonymous caller: a policy decides whether that is acceptable,
 * which is why the authentication interceptor resolves credentials without rejecting on their
 * absence.
 *
 * <p>{@link #computeOnce} exists so a policy evaluated per row cannot become an N+1. A policy
 * needing group membership or a permission lookup memoizes it here and pays once per request
 * however many rows it inspects. Policies must otherwise be pure functions of their inputs.
 */
public final class AccessContext {

  private final User user;
  private final Operation operation;
  private final String resourceName;
  private final Object targetId;
  private final Map<String, Object> memo = new HashMap<>();

  public enum Operation {
    READ,
    CREATE,
    UPDATE,
    DELETE
  }

  public AccessContext(User user, Operation operation, String resourceName) {
    this(user, operation, resourceName, null);
  }

  public AccessContext(User user, Operation operation, String resourceName, Object targetId) {
    this.user = user;
    this.operation = Objects.requireNonNull(operation, "Operation");
    this.resourceName = Objects.requireNonNull(resourceName, "Resource name");
    this.targetId = targetId;
  }

  /** The authenticated caller, or null when anonymous. */
  public User user() {
    return user;
  }

  public Operation operation() {
    return operation;
  }

  public String resourceName() {
    return resourceName;
  }

  /**
   * The id of the row being read, updated or deleted, or null when there is not exactly one.
   *
   * <p>Null for list, count, create, and the bulk operations. Payload passes {@code id} to its
   * read, update and delete access functions for the same purpose: it is what lets a policy or a
   * field check compare the row being acted on against the caller, as in "a system administrator or
   * the user themselves".
   *
   * <p>Deliberately not the incoming {@code data} or the pre-update {@code doc}, which Payload also
   * supplies. Those belong with the per-row {@code siblingData} design and are not implemented.
   */
  public Object targetId() {
    return targetId;
  }

  /**
   * Whether this request targets exactly the given row. False when the operation is not row-scoped.
   */
  public boolean targets(Object id) {
    return targetId != null && targetId.equals(id);
  }

  public boolean isAuthenticated() {
    return user != null;
  }

  /** Memoizes a derived value for the lifetime of this request. */
  @SuppressWarnings("unchecked")
  public <V> V computeOnce(String key, Supplier<V> supplier) {
    Objects.requireNonNull(key, "Memo key");
    return (V) memo.computeIfAbsent(key, ignored -> supplier.get());
  }
}
