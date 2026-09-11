package com.researchspace.api.v1.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.InventoryOperationsApi;
import com.researchspace.api.v1.controller.SamplesApiController.ApiSampleFullPost;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.service.inventory.InventoryOperationConfig;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import com.researchspace.service.inventory.InventoryOperationManager;
import jakarta.validation.Valid;
import jakarta.ws.rs.NotFoundException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Thin coordinator endpoint for configured Inventory operations. Validates the request's structure,
 * then delegates to the transactional {@link InventoryOperationManager}, which enforces the
 * live-state rules inside its own transaction and performs the whole effect atomically. No
 * per-operation logic lives here (see DevDocs/adr/0007).
 */
@ApiController
public class InventoryOperationsApiController extends BaseApiInventoryController
    implements InventoryOperationsApi {

  @Autowired InventoryOperationManager inventoryOperationManager;
  @Autowired InventoryOperationPostValidator operationPostValidator;
  @Autowired InventoryOperationConfigRegistry operationConfigs;
  @Autowired SampleApiPostFullValidator sampleApiPostFullValidator;

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
    if (request.getInputs() != null) {
      // The server-built shape (M3): the manager validates the inputs against the definition,
      // builds the sample, and runs the same core. The template check below runs on what it built.
      return inventoryOperationManager.performOperation(
          request.getOperationType(),
          request.getOrigins(),
          typedInputs(request),
          request.getTemplateId(),
          request.getDocumentedByGlobalId(),
          user,
          built -> validateTemplateConformance(built, user));
    }
    // The live-state rules (origin currently holds something, amountTaken within it, emptying
    // operations take exactly what it holds) are enforced by the manager INSIDE the operation's
    // transaction, so they hold against the state the mutation sees; the template-conformance check
    // below is handed in and run there too, before any origin is read, so the template the sample
    // is created from is the one this request was validated against (Copilot review, PR #1090).
    // Either violation propagates as the same field-scoped 400 BindException the structural checks
    // above produce.
    return inventoryOperationManager.performOperation(
        request, user, () -> validateTemplateConformance(request, user));
  }

  /**
   * The inputs as the definition types them. Jackson binds a raw {@code Map<String, Object>} value
   * object to a LinkedHashMap, never to {@link ApiQuantityInfo}, and the input validator rightly
   * rejects a Map as the wrong type, so each declared quantity or temperature that arrived as a
   * well-formed object is converted first. Anything else is left as bound for the validator to
   * judge. (Probed: a JSON number binds as Integer, Long or Double; convertValue turns a Double 0.6
   * into the BigDecimal 0.6.)
   */
  private Map<String, Object> typedInputs(ApiInventoryOperationPost request) {
    InventoryOperationConfig definition =
        operationConfigs.get(request.getOperationType()).orElseThrow();
    Map<String, Object> typed = new LinkedHashMap<>(request.getInputs());
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

  /**
   * Template conformance, mirroring POST /samples (SamplesApiController.validateCreateSampleInput):
   * a template-based new sample must reference a readable template, and its fields and quantity
   * unit must match that template, so a mismatched field list is a clean 400 instead of a 500
   * inside the manager transaction. Runs inside the manager's transaction (as its {@code
   * InTransactionValidation}), so the template it validates is the same one the sample creation
   * reads.
   */
  private void validateTemplateConformance(ApiInventoryOperationPost request, User user)
      throws BindException {
    if (request.getNewSample() == null) {
      return;
    }
    BindingResult errors = new BeanPropertyBindingResult(request, "apiInventoryOperationPost");
    ApiSampleWithFullSubSamples newSample = request.getNewSample();
    SampleTemplate template = null;
    if (newSample.getTemplateId() != null) {
      try {
        template =
            sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(newSample.getTemplateId(), user);
      } catch (NotFoundException e) {
        errors.rejectValue(
            "newSample.templateId",
            "errors.inventory.sample.templateNotFound",
            new Object[] {newSample.getTemplateId()},
            null);
      }
    }
    if (!errors.hasErrors()) {
      // The full-post validator names fields relative to the sample (quantity,
      // subSamples[i].quantity); this binding result is rooted at the request, so nest the path
      // or a rejection would fail to resolve the field and surface as a 500.
      errors.pushNestedPath("newSample");
      try {
        inputValidator.validate(
            new ApiSampleFullPost(newSample, user, template), sampleApiPostFullValidator, errors);
      } finally {
        errors.popNestedPath();
      }
    }
    throwBindExceptionIfErrors(errors);
  }
}
