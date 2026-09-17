package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InventoryOperationsApi;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiInventoryOperationResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.inventory.InventoryEditLockHeldException;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.impl.InventoryOperationInFlightOrigins;
import com.researchspace.service.inventory.operations.AliquotOperation;
import com.researchspace.service.inventory.operations.CryopreserveOperation;
import com.researchspace.service.inventory.operations.DeriveOperation;
import com.researchspace.service.inventory.operations.DestroyOperation;
import com.researchspace.service.inventory.operations.InventoryOperation;
import com.researchspace.service.inventory.operations.OperationOriginRules;
import com.researchspace.service.inventory.operations.PassageOperation;
import com.researchspace.service.inventory.operations.PoolOperation;
import com.researchspace.service.inventory.operations.ReviveOperation;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Thin coordinator endpoint for the Inventory operations. Checks the request's shape, holds the
 * edit lock on everything the operation touches, then delegates to the transactional {@link
 * InventoryOperationManager}, which lets the operation validate its own values and build the
 * sample, and enforces the live-state rules inside its own transaction.
 *
 * <p>No per-operation logic lives here: each endpoint names its operation and nothing else (see
 * DevDocs/adr/0007).
 */
@ApiController
public class InventoryOperationsApiController extends BaseApiInventoryController
    implements InventoryOperationsApi {

  @Autowired InventoryOperationManager inventoryOperationManager;
  @Autowired SystemPropertyPermissionManager systemPropertyManager;
  @Autowired InventoryOperationInFlightOrigins inFlightOrigins;

  @Autowired AliquotOperation aliquotOperation;
  @Autowired PassageOperation passageOperation;
  @Autowired PoolOperation poolOperation;
  @Autowired DeriveOperation deriveOperation;
  @Autowired CryopreserveOperation cryopreserveOperation;
  @Autowired ReviveOperation reviveOperation;
  @Autowired DestroyOperation destroyOperation;

  /**
   * {@link UnsupportedOperationException} is what {@code ApiControllerAdvice} already maps to a 404
   * with errorCode CONFIGURED_UNAVAILABLE, so a disabled feature is indistinguishable from one this
   * build does not have. No sysadmin bypass.
   */
  private void assertOperationsAvailable(User user) {
    if (!systemPropertyManager.isPropertyAllowed(
        user, SystemPropertyName.INVENTORY_OPERATIONS_AVAILABLE)) {
      throw new UnsupportedOperationException(getMessage("errors.inventory.operations.notEnabled"));
    }
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> aliquot(
      @RequestBody @Valid ApiInventoryOperationRequests.Aliquot request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return perform(aliquotOperation, request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> passage(
      @RequestBody @Valid ApiInventoryOperationRequests.Passage request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return perform(passageOperation, request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> pool(
      @RequestBody @Valid ApiInventoryOperationRequests.Pool request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return perform(poolOperation, request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> derive(
      @RequestBody @Valid ApiInventoryOperationRequests.Derive request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return perform(deriveOperation, request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> cryopreserve(
      @RequestBody @Valid ApiInventoryOperationRequests.Cryopreserve request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return perform(cryopreserveOperation, request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> revive(
      @RequestBody @Valid ApiInventoryOperationRequests.Revive request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return perform(reviveOperation, request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> destroy(
      @RequestBody @Valid ApiInventoryOperationRequests.Destroy request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return perform(destroyOperation, request, errors, user);
  }

  /**
   * One operation: the bean-validated body, then the shared origin rules, then the origin global
   * ids parsed to subsample ids, then the manager with the edit lock held. The response is the
   * created sample (null for Destroy) and each origin as it stands afterwards, read back inside the
   * operation's own transaction.
   */
  private <R extends ApiInventoryOperationRequests.Request>
      ResponseEntity<ApiInventoryOperationResult> perform(
          InventoryOperation<R> operation, R request, BindingResult errors, User user)
          throws BindException {
    assertOperationsAvailable(user);
    throwBindExceptionIfErrors(errors);

    boolean singleOrigin = !operation.requiresMultiple();
    OperationOriginRules.validate(operation, request, errors);
    throwBindExceptionIfErrors(errors);

    List<ApiInventoryOperationRequests.Origin> origins = request.originList();
    List<Long> originIds = new ArrayList<>();
    for (int i = 0; i < origins.size(); i++) {
      originIds.add(
          OperationOriginRules.subSampleId(
              origins.get(i).getGlobalId(),
              OperationOriginRules.originField(singleOrigin, i) + ".globalId",
              errors));
    }
    throwBindExceptionIfErrors(errors);

    InventoryOperationManager.OperationOutcome outcome;
    try {
      outcome =
          withOriginsLocked(
              originIds,
              user,
              () ->
                  inventoryOperationManager.performBiobankOperation(
                      operation, request, originIds, user));
    } catch (BindException coreRejection) {
      throw new BindException(facadeFieldNames(coreRejection.getBindingResult(), singleOrigin));
    }

    ApiSampleWithFullSubSamples sample = outcome.sample();
    List<ApiSubSample> originsAfter = outcome.originsAfter();
    originsAfter.forEach(this::buildAndAddInventoryRecordLinks);
    ApiInventoryOperationResult result = new ApiInventoryOperationResult(sample, originsAfter);
    if (sample == null) {
      return ResponseEntity.ok(result);
    }
    buildAndAddInventoryRecordLinks(sample);
    URI location =
        URI.create(
            getInventoryApiBaseURIBuilder()
                .path(SAMPLES_ENDPOINT + "/" + sample.getId())
                .build()
                .encode()
                .toUriString());
    return ResponseEntity.created(location).body(result);
  }

  @FunctionalInterface
  private interface OperationCall {
    InventoryOperationManager.OperationOutcome call() throws BindException;
  }

  private static final Comparator<String> ASCENDING_GLOBAL_ID =
      Comparator.comparing((String id) -> new GlobalIdentifier(id).getPrefix())
          .thenComparing(id -> new GlobalIdentifier(id).getDbId());

  /**
   * Runs the operation with every origin claimed as in flight and the Inventory edit-session lock
   * held on every origin and every parent sample, in ascending id order. The claim is what refuses
   * a second overlapping request from the SAME user (a double submit), which the edit lock treats
   * as an extension; it is released after the manager's transaction has ended.
   */
  private InventoryOperationManager.OperationOutcome withOriginsLocked(
      List<Long> originIds, User user, OperationCall work) throws BindException {
    SortedSet<String> toLock = new TreeSet<>(ASCENDING_GLOBAL_ID);
    List<String> originGlobalIds = new ArrayList<>();
    for (Long originId : originIds) {
      SubSample subSample = subSampleApiMgr.assertUserCanEditSubSample(originId, user);
      originGlobalIds.add(subSample.getGlobalIdentifier());
      toLock.add(subSample.getGlobalIdentifier());
      SampleEntity parent = subSample.getSample();
      if (parent != null) {
        toLock.add(parent.getGlobalIdentifier());
      }
    }
    List<String> taken = new ArrayList<>();
    try (InventoryOperationInFlightOrigins.Claim claim = inFlightOrigins.claim(originGlobalIds)) {
      for (String globalId : toLock) {
        ApiInventoryEditLock lock = tracker.attemptToLockForEdit(globalId, user);
        if (ApiInventoryEditLockStatus.CANNOT_LOCK.equals(lock.getStatus())) {
          throw new InventoryEditLockHeldException(globalId, lock.getOwner());
        }
        if (ApiInventoryEditLockStatus.LOCKED_OK.equals(lock.getStatus())) {
          taken.add(globalId);
        }
      }
      return work.call();
    } finally {
      for (int i = taken.size() - 1; i >= 0; i--) {
        tracker.attemptToUnlock(taken.get(i), user);
      }
    }
  }

  /**
   * The core's errors with every field renamed to the one the caller sent ({@link #facadeField});
   * codes, arguments and default message travel unchanged, so the resolved text is identical. Built
   * as plain field errors rather than through rejectValue, which would resolve the renamed path
   * against the built request it no longer fits.
   */
  static BindingResult facadeFieldNames(BindingResult core, boolean singleOrigin) {
    BindingResult renamed = new BeanPropertyBindingResult(core.getTarget(), core.getObjectName());
    for (FieldError error : core.getFieldErrors()) {
      renamed.addError(
          new FieldError(
              error.getObjectName(),
              facadeField(error.getField(), singleOrigin),
              error.getRejectedValue(),
              error.isBindingFailure(),
              error.getCodes(),
              error.getArguments(),
              error.getDefaultMessage()));
    }
    for (ObjectError error : core.getGlobalErrors()) {
      renamed.addError(error);
    }
    return renamed;
  }

  /**
   * The caller's name for a field the core reports. The core works with an origin LIST and a
   * server-built sample, so it names {@code origins[0].amountTaken} where a six-operation client
   * sent {@code origin.amountTaken}, numeric {@code id} where the client sent {@code globalId}, and
   * {@code newSample.*} for what the template check finds on the built sample: its template id is
   * the caller's {@code templateId}, and a subsample quantity is the caller's {@code eachAmount},
   * which every built subsample copies. The caller's own field names pass through.
   */
  static String facadeField(String field, boolean singleOrigin) {
    String renamed = field;
    if (singleOrigin) {
      renamed = renamed.replaceFirst("^origins(\\[0\\])?(?=\\.|$)", "origin");
    }
    return renamed
        .replaceFirst("^(origins?(?:\\[\\d+\\])?)\\.id$", "$1.globalId")
        .replaceFirst("^newSample\\.templateId$", "templateId")
        .replaceFirst("^newSample\\.subSamples\\[\\d+\\]\\.quantity$", "eachAmount")
        .replaceFirst("^newSample\\.name$", "sampleName")
        .replaceFirst("^newSample\\.storageTemp(?:Min|Max)$", "storageTemp");
  }
}
