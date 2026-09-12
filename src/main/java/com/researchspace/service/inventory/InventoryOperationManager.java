package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import java.util.List;
import java.util.Map;
import org.springframework.validation.BindException;

/**
 * Coordinates a configured Inventory operation as a single atomic unit: creates the new sample
 * (with its subsamples, custom fields and relation links) and sets each origin subsample's
 * quantity, rolling everything back on any failure.
 *
 * <p>This is generic. The effect is described entirely by the request; there is no per-operation
 * logic here, so a new operation is a new {@code operations_config.json} entry rather than new
 * Java. Transactionality comes from the {@code service.inventory.*Manager} AOP advice (see {@code
 * applicationContext-service.xml}), whose pointcut matches this interface - it is in {@code
 * service.inventory} and named {@code *Manager}. Calls through it, including the implementation in
 * {@code service.inventory.impl}, run in one transaction that the coordinated sub-manager calls
 * join (see DevDocs/adr/0007).
 */
public interface InventoryOperationManager {

  /**
   * A validation the caller needs run INSIDE the operation's transaction, before any origin is read
   * or mutated. The controller supplies its template-conformance check this way: the check itself
   * belongs to the controller layer (it delegates to the shared samples validator), but running it
   * in a separate transaction would leave a window in which the template changes between validation
   * and use, failing the operation mid-mutation (Copilot review, PR #1090). A rejection is the same
   * field-scoped 400 every other validation produces.
   */
  @FunctionalInterface
  interface InTransactionValidation {

    /** No additional validation; for callers with nothing to check transactionally. */
    InTransactionValidation NONE = () -> {};

    void validate() throws BindException;
  }

  /**
   * The transactional core, run on a request the server built ({@link #performOperation(String,
   * List, Map, Long, String, User)}). Precondition: the origins must already have passed the
   * structural validation the operations endpoint applies (InventoryOperationPostValidator): every
   * origin carries a non-null id and a non-null, unit-bearing amountTaken, and origin ids are
   * unique. The implementation dereferences these without guards, so a caller that skips validation
   * would fail mid-transaction instead of cleanly.
   *
   * <p>The live-state rules (an origin must currently hold something, and the amount taken may not
   * exceed what it holds) are enforced HERE, inside the operation's own transaction, so they hold
   * against the same state the mutation sees. A violation is reported as a {@link BindException}
   * carrying field errors under {@code origins[i]}, before anything is written.
   *
   * <p>An origin whose amount is a whole-origin claim (amountMode ALL, or an origin-emptying
   * operation) is additionally compare-and-swapped against the live quantity, and a mismatch raises
   * {@link InventoryEditConflictException} rather than a BindException: the request was valid
   * against the state the client read, so it is a 409 to reload from, not a 400 to correct.
   *
   * @return the newly created sample (with its subsamples), as returned by the sample-creation
   *     manager, or {@code null} for a terminal operation that creates nothing (noOutput, e.g.
   *     Destroy, which only acts on its origins). See DevDocs/adr/0007.
   * @throws InventoryEditConflictException when a whole-origin claim no longer matches the origin's
   *     live quantity, before any origin is decremented or any sample created. Unchecked, so
   *     Spring's default rules roll the transaction back without a {@code rollback-for} entry.
   * @throws BindException when a live-state rule is violated, before any origin is decremented or
   *     any sample created. The txAdvice for this method declares {@code rollback-for
   *     BindException} (BindException is checked, so Spring's default rules would otherwise COMMIT
   *     on it): the sibling-set locks the live-state pass takes go through {@code
   *     lockSiblingRowsAndRecalculateTotal}, which writes each parent's recomputed total, and those
   *     writes must not be committed alongside a 400 (Copilot review, PR #1090).
   *     InventoryOperationManagerImplTest pins the ordering: performExpectingRejection asserts none
   *     of the mutating collaborators were called.
   */
  ApiSampleWithFullSubSamples performOperation(
      ApiInventoryOperationPost request, User user, InTransactionValidation callerValidation)
      throws BindException;

  /**
   * The server-built path (DevDocs/adr/0007, M3): validates {@code inputs} against the definition's
   * declared inputs ({@link InventoryOperationInputValidator}), builds the request the core
   * executes from the definition, the origins' live state and those inputs ({@link
   * InventoryOperationRequestBuilder}), then runs the same transactional core as {@link
   * #performOperation(ApiInventoryOperationPost, User, InTransactionValidation)}, unchanged.
   *
   * <p>Each origin element carries its own {@code amountTaken} and {@code amountMode}, which the
   * core validates against the live locked quantity; they are not inputs. An origin element MAY
   * carry no amount when the definition itself decides it (an operation that takes nothing, or one
   * that empties its origins): the builder then supplies zero, or the live quantity under a
   * whole-origin claim. An element's {@code expectedQuantity}, when set, is compare-and-swapped
   * against the live locked quantity (M0 D5), raising {@link InventoryEditConflictException} on a
   * mismatch. Absent optional inputs that declare a {@code default} are filled before validation
   * (M0 D7). Precondition otherwise as for the core overload: the origin list has passed the
   * endpoint's structural validation.
   *
   * <p>Generated field names resolve in the request's locale ({@code LocaleContextHolder}, M0 D1),
   * and a {@code today} computed value is the current date in the session's timezone ({@code
   * SessionTimeZoneUtils}), which the login flow records from the browser.
   *
   * @param templateId the built sample's template; null for an ad-hoc sample
   * @param documentedByGlobalId the ELN record the built sample gets an {@code IsDocumentedBy} link
   *     to (the wizard's documentation step); null for none. The caller has checked it names a
   *     documentable record kind.
   * @return as the core overload
   * @throws BindException when an input fails the definition's rules (field errors named by the
   *     bare input key, which is the name a typed facade client sends: M0), or as the core overload
   */
  ApiSampleWithFullSubSamples performOperation(
      String operationKey,
      List<ApiInventoryOperationOriginUpdate> origins,
      Map<String, Object> inputs,
      Long templateId,
      String documentedByGlobalId,
      User user)
      throws BindException;
}
