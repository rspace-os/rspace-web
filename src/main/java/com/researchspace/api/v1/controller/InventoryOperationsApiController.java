package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.InventoryOperationsApi;
import com.researchspace.api.v1.controller.SamplesApiController.ApiSampleFullPost;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import com.researchspace.service.inventory.InventoryOperationManager;
import jakarta.validation.Valid;
import jakarta.ws.rs.NotFoundException;
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
