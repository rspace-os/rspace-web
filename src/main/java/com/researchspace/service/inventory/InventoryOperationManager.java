package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import java.util.List;
import java.util.Map;
import org.springframework.validation.BindException;

/**
 * Coordinates a configured Inventory operation as a single atomic unit: creates the new sample
 * (with its subsamples, custom fields and relation links) and sets each origin subsample's
 * quantity, rolling everything back on any failure.
 *
 * <p>There is no per-operation logic here: a new operation is a new {@code operations_config.json}
 * entry, not new Java. Transactionality comes from the {@code service.inventory.*Manager} AOP
 * advice (see {@code applicationContext-service.xml}), whose pointcut matches this interface
 * because it is in {@code service.inventory} and named {@code *Manager} - moving or renaming it
 * silently drops that transaction boundary.
 */
public interface InventoryOperationManager {

  /**
   * A validation the caller needs run INSIDE the operation's transaction, before any origin is read
   * or mutated: validating in a separate transaction would leave a window for the checked state to
   * change before the mutation actually runs.
   */
  @FunctionalInterface
  interface InTransactionValidation {

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
   * @return the newly created sample (with its subsamples), as returned by the sample-creation
   *     manager, or {@code null} for a terminal operation that creates nothing (noOutput).
   * @throws BindException when a live-state rule is violated, before any origin is decremented or
   *     any sample created. The txAdvice for this method declares {@code rollback-for
   *     BindException} (BindException is checked, so Spring's default rules would otherwise COMMIT
   *     on it).
   */
  ApiSampleWithFullSubSamples performOperation(
      ApiInventoryOperationPost request, User user, InTransactionValidation callerValidation)
      throws BindException;

  /**
   * What an operation produced: the created sample, and each origin as it stands afterwards.
   *
   * <p>Both are read INSIDE the operation's transaction, so together they are a single consistent
   * snapshot of what this operation actually produced.
   *
   * <p>{@code sample} is null for a terminal operation that creates nothing (noOutput). {@code
   * originsAfter} is in the order the origins were given.
   */
  record OperationOutcome(ApiSampleWithFullSubSamples sample, List<ApiSubSample> originsAfter) {}

  /**
   * The server-built path: validates {@code inputs} against the definition's declared inputs
   * ({@link InventoryOperationInputValidator}), builds the request the core executes from the
   * definition, the origins' live state and those inputs ({@link
   * InventoryOperationRequestBuilder}), then runs the same transactional core as {@link
   * #performOperation(ApiInventoryOperationPost, User, InTransactionValidation)}, unchanged.
   *
   * <p>Each origin element carries its own {@code amountTaken} and {@code amountMode}, which the
   * core validates against the origin's live quantity; they are not inputs. An origin element MAY
   * carry no amount when the definition itself decides it (an operation that takes nothing, or one
   * that empties its origins): the builder then supplies zero, or the live quantity under a
   * whole-origin claim. Absent optional inputs that declare a {@code default} are filled before
   * validation. Precondition otherwise as for the core overload: the origin list has passed the
   * endpoint's structural validation.
   *
   * <p>Generated field names resolve in the request's locale ({@code LocaleContextHolder}), and a
   * {@code today} computed value is the current date in the session's timezone ({@code
   * SessionTimeZoneUtils}), which the login flow records from the browser.
   *
   * @param templateId the built sample's template; null for an ad-hoc sample
   * @param documentedByGlobalId the ELN record the built sample gets an {@code IsDocumentedBy} link
   *     to; null for none. The caller has checked it names a documentable record kind.
   * @return the created sample (null for a terminal operation) together with each origin as it
   *     stands afterwards, in the order given
   * @throws BindException when an input fails the definition's rules (field errors named by the
   *     bare input key, the name a typed facade client sends), or as the core overload
   */
  OperationOutcome performOperation(
      String operationKey,
      List<ApiInventoryOperationOriginUpdate> origins,
      Map<String, Object> inputs,
      Long templateId,
      String documentedByGlobalId,
      User user)
      throws BindException;
}
