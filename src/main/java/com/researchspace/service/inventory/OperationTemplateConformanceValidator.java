package com.researchspace.service.inventory;

import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleFullPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.dtos.IControllerInputValidator;
import com.researchspace.model.inventory.SampleTemplate;
import jakarta.ws.rs.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;

/**
 * Template conformance for an operation's new sample, mirroring {@code POST /samples}
 * (SamplesApiController.validateCreateSampleInput): a template-based sample must reference a
 * readable template, and its fields and quantity unit must match that template, so a mismatch is a
 * clean 400 instead of a 500 inside the manager transaction.
 *
 * <p>This is a domain invariant of the operation and it runs INSIDE the operation's transaction, on
 * the request the server built and before any origin is read, so the template it validates is the
 * template the sample is created from. It lived in {@code InventoryOperationsApiController} until
 * the parallel review (L1): the import direction was legal, because the manager defined the
 * callback interface it was handed through, but a check with a transactional precondition sat in a
 * package nothing reasons about or tests as transactional. Someone moving that controller method to
 * a cached, non-transactional template read - a normal thing to do in a controller - would reopen
 * exactly the race the callback was introduced to close.
 */
@Component
public class OperationTemplateConformanceValidator {

  private final SampleApiManager sampleApiMgr;
  private final IControllerInputValidator inputValidator;
  private final Validator sampleApiPostValidator;
  private final SampleApiPostFullValidator sampleApiPostFullValidator;

  /**
   * {@code sampleApiPostValidator} is taken as a plain Spring {@link Validator} by bean name rather
   * than by its own type: it extends a package-private hierarchy in {@code api.v1.controller}, and
   * naming that type here would put back the upward import this class exists to remove.
   */
  @Autowired
  public OperationTemplateConformanceValidator(
      SampleApiManager sampleApiMgr,
      IControllerInputValidator inputValidator,
      @Qualifier("sampleApiPostValidator") Validator sampleApiPostValidator,
      SampleApiPostFullValidator sampleApiPostFullValidator) {
    this.sampleApiMgr = sampleApiMgr;
    this.inputValidator = inputValidator;
    this.sampleApiPostValidator = sampleApiPostValidator;
    this.sampleApiPostFullValidator = sampleApiPostFullValidator;
  }

  /**
   * @throws BindException naming the offending field, so the endpoint reports it as the same
   *     field-scoped 400 its structural checks produce
   */
  public void validate(ApiInventoryOperationPost built, User user) throws BindException {
    if (built.getNewSample() == null) {
      return;
    }
    BindingResult errors = new BeanPropertyBindingResult(built, "apiInventoryOperationPost");
    ApiSampleWithFullSubSamples newSample = built.getNewSample();
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
      // Both validators, exactly as POST /samples runs them. Running only the full-post one left
      // the sample unbounded: the length, tag and extra-field rules live on sampleApiPostValidator,
      // and nothing else bounded them - the input validator only checks that a "text" input is a
      // CharSequence, so an over-long sampleName reached EditInfo.name (varchar(255)) inside the
      // manager's transaction, after the origin locks were taken (parallel review).
      //
      // Both name fields relative to the sample (name, quantity, subSamples[i].quantity); this
      // binding result is rooted at the request, so nest the path or a rejection would fail to
      // resolve the field and surface as a 500.
      errors.pushNestedPath("newSample");
      try {
        inputValidator.validate(newSample, sampleApiPostValidator, errors);
        inputValidator.validate(
            new ApiSampleFullPost(newSample, user, template), sampleApiPostFullValidator, errors);
      } finally {
        errors.popNestedPath();
      }
    }
    if (errors.hasErrors()) {
      throw new BindException(errors);
    }
  }
}
