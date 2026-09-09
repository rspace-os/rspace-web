package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.UnknownPropertyCapturing;
import com.researchspace.service.inventory.ApiExtraFieldsHelper;
import com.researchspace.service.inventory.InventoryOperationConfig;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/**
 * Validates an {@link ApiInventoryOperationPost} against the operation definition its {@code
 * operationType} names (DevDocs/adr/0007). The rules are interpreted generically from the shared
 * {@code operations_config.json} (no per-operation Java): origin cardinality, new-sample presence
 * (a noOutput operation like Destroy creates nothing), per-origin amount semantics (positive for a
 * decrementing operation, exactly zero for one that only links, e.g. Passage), configured
 * storage-temperature bounds (unit-aware), and a provenance link from the new sample back to every
 * origin. The new sample is also run through the same {@link SampleApiPostValidator} the public
 * samples endpoint uses. Checks needing an origin's live quantity are enforced by the manager
 * inside the operation's transaction, not here (DevDocs/adr/0007).
 *
 * <p>This class owns the root of the payload: resolving {@code operationType} to a definition, and
 * the origin-list checks every later rule depends on (non-empty, no null entries, within the cap,
 * right cardinality). Everything below the root is delegated by payload region to {@link
 * OperationOriginValidator} and {@link OperationNewSampleValidator}, with the rules they share in
 * {@link OperationValidationSupport}. Error codes, field paths and messages are unchanged by that
 * split, so it is invisible on the wire.
 */
@Component
public class InventoryOperationPostValidator implements Validator {

  /**
   * Ceiling on origins per request: each origin costs a read, a lock and an update cycle, so the
   * batch is capped like the samples endpoint caps newSampleSubSamplesCount (both 100).
   */
  static final int MAX_ORIGINS = 100;

  /**
   * Ceiling on unknown-property errors reported for one request. {@link
   * UnknownPropertyCapturing#MAX_CAPTURED_UNKNOWN_PROPERTIES} caps names per OBJECT, which does not
   * bound the response, because nothing bounds the number of objects: the walk deliberately runs
   * before MAX_ORIGINS and MAX_EXTRA_FIELDS so a typo is reported even in a structurally broken
   * request, and each error costs a reflective field read plus a message resolution. Without this a
   * body of junk keys amplifies into an unbounded error list (parallel review). A caller with this
   * many typos does not need the rest enumerated, only to be told there are more.
   */
  static final int MAX_REPORTED_UNKNOWN_PROPERTIES = 50;

  private final InventoryOperationConfigRegistry operationConfigs;

  /**
   * Plain collaborators rather than beans: they hold only the validators they delegate to, so they
   * need no Spring lifecycle, and constructing them here keeps this class's own dependencies the
   * ones the container already injects.
   */
  private final OperationOriginValidator originValidator;

  private final OperationNewSampleValidator newSampleValidator;

  public InventoryOperationPostValidator(
      InventoryOperationConfigRegistry operationConfigs,
      SampleApiPostValidator sampleApiPostValidator,
      ApiExtraFieldsHelper extraFieldsHelper) {
    this.operationConfigs = operationConfigs;
    this.originValidator = new OperationOriginValidator(extraFieldsHelper);
    this.newSampleValidator = new OperationNewSampleValidator(sampleApiPostValidator);
  }

  @Override
  public boolean supports(Class<?> clazz) {
    return ApiInventoryOperationPost.class.isAssignableFrom(clazz);
  }

  @Override
  public void validate(Object target, Errors errors) {
    ApiInventoryOperationPost request = (ApiInventoryOperationPost) target;

    // Reported first and never skipped by a later early return: a property no DTO declares was
    // dropped during binding, so every rule below has been evaluated against a request that is not
    // the one the caller sent.
    rejectUnknownProperties(request, errors);

    // The operation key names the definition every other rule comes from, so an unknown key is
    // rejected alone: there is nothing meaningful to validate the rest of the request against.
    Optional<InventoryOperationConfig> configForKey =
        operationConfigs.get(request.getOperationType());
    if (configForKey.isEmpty()) {
      errors.rejectValue(
          "operationType",
          StringUtils.isBlank(request.getOperationType())
              ? "errors.inventory.operation.operationTypeRequired"
              : "errors.inventory.operation.unknownType",
          new Object[] {request.getOperationType()},
          "Unknown operation type.");
      return;
    }
    InventoryOperationConfig config = configForKey.get();

    if (CollectionUtils.isEmpty(request.getOrigins())) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originsRequired",
          "At least one origin subsample must be provided for the operation.");
      return;
    }
    // A null list entry (JSON "[null]") cannot be iterated by the checks below; reject it as a
    // clean 400 rather than letting it surface as a 500.
    if (request.getOrigins().stream().anyMatch(Objects::isNull)) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originIdRequired",
          "Each origin must identify a subsample by id.");
      return;
    }
    // The ceiling is checked before the cardinality, and returns: an oversized list is the more
    // specific reason (a single-origin operation would otherwise report only "exactly one" and hide
    // it), and stopping here keeps the per-origin and per-link work bounded by the cap rather than
    // by whatever the caller sent (Copilot review, PR #1090). Each origin costs a read, a lock and
    // an update cycle; the batch is capped like the samples endpoint caps newSampleSubSamplesCount.
    if (request.getOrigins().size() > MAX_ORIGINS) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originCountMaximum",
          new Object[] {MAX_ORIGINS},
          "This operation accepts at most 100 origin subsamples.");
      return;
    }
    if (config.requiresMultiple() && request.getOrigins().size() < 2) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originCountMinimum",
          "This operation requires at least two origin subsamples.");
    } else if (!config.requiresMultiple() && request.getOrigins().size() != 1) {
      errors.rejectValue(
          "origins",
          "errors.inventory.operation.originCountExact",
          "This operation requires exactly one origin subsample.");
    }

    originValidator.validateOrigins(request, config, errors);
    newSampleValidator.validateNewSample(request, config, errors);
  }

  /**
   * Reports every property the request body carried that no DTO declares (F7). The API's mapper has
   * FAIL_ON_UNKNOWN_PROPERTIES off, so binding drops such a key silently, and afterwards a dropped
   * key is indistinguishable from an optional one the caller omitted: no presence rule can see it.
   * Each object in the payload captures the NAME at binding instead ({@link
   * UnknownPropertyCapturing}), and this turns those into ordinary field errors, aggregated with
   * every other error in the one 400.
   *
   * <p>A walk of its own rather than a call inside each region's rules, because those return early
   * on the first structural problem while an unknown property must be reported either way. Only the
   * levels a client actually sends are visited; a quantity is a leaf of numbers and needs no pass.
   */
  private static void rejectUnknownProperties(ApiInventoryOperationPost request, Errors errors) {
    Budget budget = new Budget();
    rejectCaptured(errors, "", request, budget);
    forEachAt(
        request.getOrigins(),
        "origins",
        (path, origin) -> {
          rejectCaptured(errors, path, origin, budget);
          forEachAt(
              origin.getExtraFields(),
              path + ".extraFields",
              (fieldPath, field) -> rejectCaptured(errors, fieldPath, field, budget));
        });
    ApiSampleWithFullSubSamples newSample = request.getNewSample();
    if (newSample != null) {
      rejectCaptured(errors, "newSample", newSample, budget);
      forEachAt(
          newSample.getSubSamples(),
          "newSample.subSamples",
          (path, subSample) -> rejectCaptured(errors, path, subSample, budget));
      forEachAt(
          newSample.getExtraFields(),
          "newSample.extraFields",
          (path, field) -> rejectCaptured(errors, path, field, budget));
    }
    if (budget.unreported > 0) {
      errors.reject(
          "errors.inventory.operation.unknownPropertiesTruncated",
          new Object[] {budget.unreported},
          "Further unrecognised properties were not listed.");
    }
  }

  /** How many unknown-property errors are left to report, and how many were skipped. */
  private static final class Budget {
    private int remaining = MAX_REPORTED_UNKNOWN_PROPERTIES;
    private int unreported;
  }

  /** Visits each non-null element of a nullable list, at {@code path[index]}. */
  private static <T> void forEachAt(List<T> list, String path, BiConsumer<String, T> visit) {
    if (list == null) {
      return;
    }
    for (int index = 0; index < list.size(); index++) {
      T element = list.get(index);
      if (element != null) {
        visit.accept(String.format("%s[%d]", path, index), element);
      }
    }
  }

  /**
   * One error per captured name, scoped to the object that carried it, so the response says which
   * part of the payload the property was on. An empty path is the request itself, which is not a
   * field of anything and so becomes a global error.
   */
  private static void rejectCaptured(
      Errors errors, String path, UnknownPropertyCapturing object, Budget budget) {
    for (String property : object.getUnknownProperties()) {
      if (budget.remaining <= 0) {
        budget.unreported++;
        continue;
      }
      budget.remaining--;
      // Truncated because the name is echoed back to the caller and a property name is bounded only
      // by Jackson's 50,000-character limit; enough to identify the typo is enough.
      Object[] arguments =
          new Object[] {StringUtils.abbreviate(property, MAX_REPORTED_NAME_LENGTH)};
      String defaultMessage = "This operation does not accept this property.";
      if (path.isEmpty()) {
        errors.reject("errors.inventory.operation.unknownProperty", arguments, defaultMessage);
      } else {
        errors.rejectValue(
            path, "errors.inventory.operation.unknownProperty", arguments, defaultMessage);
      }
    }
  }

  /** How much of an unrecognised property's name is echoed back in the error. */
  private static final int MAX_REPORTED_NAME_LENGTH = 64;
}
