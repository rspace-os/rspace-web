package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.service.inventory.operations.InventoryOperation;
import java.util.List;
import org.springframework.validation.BindException;

/**
 * Coordinates an Inventory operation as a single atomic unit: creates the new sample (with its
 * subsamples, custom fields and relation links) and sets each origin subsample's quantity, rolling
 * everything back on any failure.
 *
 * <p>There is no per-operation logic here: what an operation validates and what it builds belong to
 * its own class ({@code com.researchspace.service.inventory.operations}). Transactionality comes
 * from the {@code service.inventory.*Manager} AOP advice (see {@code
 * applicationContext-service.xml}), whose pointcut matches this interface because it is in {@code
 * service.inventory} and named {@code *Manager} - moving or renaming it silently drops that
 * transaction boundary.
 */
public interface InventoryOperationManager {

  /**
   * What an operation produced: the created sample, and each origin as it stands afterwards.
   *
   * <p>Both are read INSIDE the operation's transaction, so together they are a single consistent
   * snapshot of what this operation actually produced.
   *
   * <p>{@code sample} is null for a terminal operation that creates nothing. {@code originsAfter}
   * is in the order the origins were given.
   */
  record OperationOutcome(ApiSampleWithFullSubSamples sample, List<ApiSubSample> originsAfter) {}

  /**
   * Runs one operation: asserts edit permission on every origin, snapshots each origin's live
   * state, lets the operation check its own values and build the request, then runs the
   * transactional core on it.
   *
   * <p>Generated field names resolve in the request's locale ({@code LocaleContextHolder}), and a
   * date an operation records is the current date in the session's timezone ({@code
   * SessionTimeZoneUtils}), which the login flow records from the browser.
   *
   * <p>The live-state rules (an origin must currently hold something, and the amount taken may not
   * exceed what it holds) are enforced inside this transaction, so they hold against the same state
   * the mutation sees, and a violation is reported before anything is written.
   *
   * @param originIds the origin subsamples, in the order the request listed them, already parsed
   *     from the global ids the caller sent
   * @return the created sample (null for a terminal operation) together with each origin as it
   *     stands afterwards, in the order given
   * @throws BindException when a value fails the operation's rules, the documentation target is
   *     unreadable, or a live-state rule is violated. The txAdvice for this method declares {@code
   *     rollback-for BindException} (BindException is checked, so Spring's default rules would
   *     otherwise COMMIT on it).
   */
  <R extends ApiInventoryOperationRequests.Request> OperationOutcome performBiobankOperation(
      InventoryOperation<R> operation, R request, List<Long> originIds, User user)
      throws BindException;
}
