package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.InventoryOperationsApi;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiInventoryOperationResult;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.service.inventory.InventoryOperationConfig;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import com.researchspace.service.inventory.InventoryOperationManager;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * Thin coordinator endpoint for configured Inventory operations. Validates the request's structure,
 * then delegates to the transactional {@link InventoryOperationManager}, which validates the inputs
 * against the definition, builds the sample, enforces the live-state rules inside its own
 * transaction and performs the whole effect atomically. No per-operation logic lives here (see
 * DevDocs/adr/0007).
 *
 * <p>The seven typed endpoints (DevDocs/adr/0007 M6) are facades over the same path: each converts
 * its typed body to the generic request, runs the same structural validator and the same manager
 * call, and renames the error paths back to the fields the caller sent on the way out ({@link
 * #facadeField}). Their only own rules are the shape ones the typed body carries.
 */
@ApiController
public class InventoryOperationsApiController extends BaseApiInventoryController
    implements InventoryOperationsApi {

  @Autowired InventoryOperationManager inventoryOperationManager;
  @Autowired InventoryOperationPostValidator operationPostValidator;
  @Autowired InventoryOperationConfigRegistry operationConfigs;

  /** Only converts an already-bound Map into a DTO, so it needs none of the API mapper's setup. */
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public String getOperationsConfig() {
    return operationConfigs.rawConfigJson();
  }

  @Override
  public ApiSampleWithFullSubSamples performOperation(
      @RequestBody @Valid ApiInventoryOperationPost request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    inputValidator.validate(request, operationPostValidator, errors);
    throwBindExceptionIfErrors(errors);
    // The manager validates the inputs, builds the sample and runs the transactional core. The
    // template-conformance check below is handed in and run there too, on what it built and before
    // any origin is read, so the template the sample is created from is the one this request was
    // validated against (Copilot review, PR #1090). Either kind of rejection propagates as the same
    // field-scoped 400 BindException the structural checks above produce.
    return inventoryOperationManager.performOperation(
        request.getOperationType(),
        request.getOrigins(),
        typedInputs(request),
        request.getTemplateId(),
        request.getDocumentedByGlobalId(),
        user);
  }

  // --- the seven typed endpoints (M6) ---

  @Override
  public ResponseEntity<ApiInventoryOperationResult> aliquot(
      @RequestBody @Valid ApiInventoryOperationRequests.Aliquot request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return performTyped("aliquot", request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> passage(
      @RequestBody @Valid ApiInventoryOperationRequests.Passage request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return performTyped("passage", request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> pool(
      @RequestBody @Valid ApiInventoryOperationRequests.Pool request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return performTyped("pool", request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> derive(
      @RequestBody @Valid ApiInventoryOperationRequests.Derive request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return performTyped("derive", request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> cryopreserve(
      @RequestBody @Valid ApiInventoryOperationRequests.Cryopreserve request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return performTyped("cryopreserve", request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> revive(
      @RequestBody @Valid ApiInventoryOperationRequests.Revive request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return performTyped("revive", request, errors, user);
  }

  @Override
  public ResponseEntity<ApiInventoryOperationResult> destroy(
      @RequestBody @Valid ApiInventoryOperationRequests.Destroy request,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return performTyped("destroy", request, errors, user);
  }

  /**
   * One typed facade: the bean-validated shape, origins parsed from global ids (a non-subsample
   * prefix is rejected here, M0 D3), then the generic request through the same validator and
   * manager call as {@link #performOperation}, with every error path renamed to the field the
   * caller sent. The response is M0's envelope: the created sample (null for Destroy) and each
   * origin as it stands afterwards, read back after the transaction committed. 201 with a Location
   * header for a created sample, 200 otherwise.
   */
  private ResponseEntity<ApiInventoryOperationResult> performTyped(
      String operationKey,
      ApiInventoryOperationRequests.Request request,
      BindingResult errors,
      User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    boolean singleOrigin = !operationConfigs.get(operationKey).orElseThrow().requiresMultiple();
    ApiInventoryOperationPost generic = new ApiInventoryOperationPost();
    generic.setOperationType(operationKey);
    List<ApiInventoryOperationRequests.Origin> origins = request.originList();
    for (int i = 0; i < origins.size(); i++) {
      String field = singleOrigin ? "origin" : "origins[" + i + "]";
      ApiInventoryOperationRequests.Origin origin = origins.get(i);
      ApiInventoryOperationOriginUpdate update = new ApiInventoryOperationOriginUpdate();
      if (origin == null) {
        errors.rejectValue(
            field,
            "errors.inventory.operation.originIdRequired",
            "Each origin must identify a subsample by id.");
      } else {
        update.setId(subSampleId(origin.getGlobalId(), field + ".globalId", errors));
        update.setAmountTaken(origin.getAmountTaken());
        update.setExpectedQuantity(origin.getExpectedQuantity());
      }
      generic.getOrigins().add(update);
    }
    throwBindExceptionIfErrors(errors);
    generic.setInputs(request.toOperationInputs());
    generic.setTemplateId(request.getTemplateId());
    generic.setDocumentedByGlobalId(request.getDocumentedByGlobalId());

    ApiSampleWithFullSubSamples sample;
    try {
      BindingResult genericErrors = new BeanPropertyBindingResult(generic, errors.getObjectName());
      inputValidator.validate(generic, operationPostValidator, genericErrors);
      throwBindExceptionIfErrors(genericErrors);
      sample =
          inventoryOperationManager.performOperation(
              operationKey,
              generic.getOrigins(),
              typedInputs(generic),
              generic.getTemplateId(),
              generic.getDocumentedByGlobalId(),
              user);
    } catch (BindException coreRejection) {
      throw new BindException(facadeFieldNames(coreRejection.getBindingResult(), singleOrigin));
    }

    List<ApiSubSample> originsAfter = new ArrayList<>();
    for (ApiInventoryOperationOriginUpdate update : generic.getOrigins()) {
      ApiSubSample after = subSampleApiMgr.getApiSubSampleById(update.getId(), user);
      buildAndAddInventoryRecordLinks(after);
      originsAfter.add(after);
    }
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

  /**
   * The subsample id a facade origin's global id names, or null with a field error: the prefix is
   * what makes "SS1234" unambiguous where a bare number could be a sample or a container (M0 D3),
   * so anything but a well-formed SS id is rejected at bind time rather than through a confusing
   * lookup failure later.
   */
  private static Long subSampleId(String globalId, String field, BindingResult errors) {
    if (globalId != null && GlobalIdentifier.isValid(globalId)) {
      GlobalIdentifier parsed = new GlobalIdentifier(globalId);
      if (parsed.getPrefix() == GlobalIdPrefix.SS) {
        return parsed.getDbId();
      }
    }
    errors.rejectValue(
        field,
        "errors.inventory.operation.originGlobalIdInvalid",
        new Object[] {globalId},
        "Each origin must be identified by a subsample global id.");
    return null;
  }

  /**
   * The core's errors with every field renamed to the one the typed caller sent ({@link
   * #facadeField}); codes, arguments and default message travel unchanged, so the resolved text is
   * identical. Built as plain field errors rather than through rejectValue, which would resolve the
   * renamed path against the generic request it no longer fits.
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
   * The typed facade's name for a field the core reports. The core works with an origin LIST and a
   * server-built sample, so it names {@code origins[0].amountTaken} where a six-operation client
   * sent {@code origin.amountTaken} (M0: an M6 concern precisely so it is not shipped), numeric
   * {@code id} where the client sent {@code globalId}, and {@code newSample.*} for what the
   * template check finds on the built sample: its template id is the caller's {@code templateId},
   * and a subsample quantity is the caller's {@code eachAmount}, which every built subsample
   * copies. Bare input keys are already the caller's field names and pass through.
   */
  static String facadeField(String field, boolean singleOrigin) {
    String renamed = field;
    if (singleOrigin) {
      renamed = renamed.replaceFirst("^origins(\\[0\\])?(?=\\.|$)", "origin");
    }
    return renamed
        .replaceFirst("^(origins?(?:\\[\\d+\\])?)\\.id$", "$1.globalId")
        .replaceFirst("^newSample\\.templateId$", "templateId")
        .replaceFirst("^newSample\\.subSamples\\[\\d+\\]\\.quantity$", "eachAmount");
  }

  /**
   * The inputs as the definition types them. Jackson binds a raw {@code Map<String, Object>} value
   * object to a LinkedHashMap, never to {@link ApiQuantityInfo}, and the input validator rightly
   * rejects a Map as the wrong type, so each declared quantity or temperature that arrived as a
   * well-formed object is converted first. Anything else is left as bound for the validator to
   * judge. (Probed: a JSON number binds as Integer, Long or Double; convertValue turns a Double 0.6
   * into the BigDecimal 0.6.) Absent inputs are an empty map: Destroy declares none.
   */
  private Map<String, Object> typedInputs(ApiInventoryOperationPost request) {
    InventoryOperationConfig definition =
        operationConfigs.get(request.getOperationType()).orElseThrow();
    Map<String, Object> typed = new LinkedHashMap<>();
    if (request.getInputs() != null) {
      typed.putAll(request.getInputs());
    }
    for (InventoryOperationConfig.Input input : definition.inputs()) {
      boolean quantityTyped = "quantity".equals(input.type()) || "temperature".equals(input.type());
      if (quantityTyped
          && typed.get(input.key()) instanceof Map<?, ?> raw
          && raw.get("numericValue") instanceof Number
          && raw.get("unitId") instanceof Integer) {
        typed.put(input.key(), MAPPER.convertValue(raw, ApiQuantityInfo.class));
      }
    }
    return typed;
  }
}
