package com.researchspace.service.inventory.operations;

import static com.researchspace.service.inventory.operations.OperationTestFixtures.KEYS;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.errorsFor;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.millilitres;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.originState;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.requestOrigin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.validation.BeanPropertyBindingResult;

class AliquotOperationTest {

  private static final AliquotOperation ALIQUOT = new AliquotOperation();
  private static final LocalDate TODAY = LocalDate.parse("2026-08-20");

  private static ApiInventoryOperationRequests.Aliquot request() {
    ApiInventoryOperationRequests.Aliquot request = new ApiInventoryOperationRequests.Aliquot();
    request.setSampleName("Working stock");
    request.setCount(new BigDecimal("3"));
    request.setEachAmount(millilitres("2"));
    request.setOrigin(requestOrigin(100, millilitres("6")));
    return request;
  }

  private static ApiSampleWithFullSubSamples build(ApiInventoryOperationRequests.Aliquot request) {
    return ALIQUOT.build(request, List.of(originState(100, "10")), KEYS, TODAY).getNewSample();
  }

  @Test
  void createsTheNamedSampleWithOneSubsamplePerCount() {
    ApiSampleWithFullSubSamples sample = build(request());

    assertNotNull(sample);
    assertEquals("Working stock", sample.getName());
    assertEquals(3, sample.getSubSamples().size());
    assertTrue(
        sample.getSubSamples().stream().allMatch(s -> millilitres("2").equals(s.getQuantity())),
        "every created subsample holds the requested each-amount");
  }

  @Test
  void createsOneSubsampleWhenNoCountIsSent() {
    ApiInventoryOperationRequests.Aliquot request = request();
    request.setCount(null);

    assertEquals(1, build(request).getSubSamples().size());
  }

  @Test
  void linksTheCreatedSampleBackToItsOrigin() {
    ApiExtraField link = build(request()).getExtraFields().get(0);

    assertEquals("operations.aliquot.linkFieldName", link.getOperationFieldKey());
    assertEquals(ApiExtraField.ExtraFieldTypeEnum.LINK, link.getType());
    assertEquals("IsPartOf", link.getLink().getRelationType());
    assertEquals("SS100", link.getLink().getTargetGlobalId());
  }

  @Test
  void passesTheTemplateAndDocumentationTargetThrough() {
    ApiInventoryOperationRequests.Aliquot request = request();
    request.setTemplateId(42L);
    request.setDocumentedByGlobalId("SD7");

    ApiSampleWithFullSubSamples sample = build(request);

    assertEquals(42L, sample.getTemplateId());
    ApiExtraField documentation =
        OperationTestFixtures.fieldNamed(
            sample.getExtraFields(), "operations.documentation.fieldName");
    assertEquals("IsDocumentedBy", documentation.getLink().getRelationType());
    assertEquals("SD7", documentation.getLink().getTargetGlobalId());
  }

  @Test
  void takesTheAmountTheCallerChoseFromTheOrigin() {
    ApiInventoryOperationPost built =
        ALIQUOT.build(request(), List.of(originState(100, "10")), KEYS, TODAY);

    assertEquals(millilitres("6"), built.getOrigins().get(0).getAmountTaken());
    assertEquals(false, built.isEmptiesOrigin());
  }

  @Test
  void rejectsACreatedAmountOfZero() {
    ApiInventoryOperationRequests.Aliquot request = request();
    request.setEachAmount(millilitres("0"));
    BeanPropertyBindingResult errors = errorsFor(request);

    ALIQUOT.validate(request, errors);

    assertEquals(
        "errors.inventory.operation.createdAmountNotPositive",
        errors.getFieldError("eachAmount").getCode());
  }

  @Test
  void rejectsACreatedAmountInAUnitThatIsNotAnAmount() {
    ApiInventoryOperationRequests.Aliquot request = request();
    request.setEachAmount(new ApiQuantityInfo(new BigDecimal("2"), RSUnitDef.CELSIUS.getId()));
    BeanPropertyBindingResult errors = errorsFor(request);

    ALIQUOT.validate(request, errors);

    assertEquals(
        "errors.inventory.quantity.unitNotAmount", errors.getFieldError("eachAmount").getCode());
  }

  @Test
  void rejectsATotalTooLargeToStore() {
    ApiInventoryOperationRequests.Aliquot request = request();
    request.setCount(new BigDecimal("100"));
    request.setEachAmount(millilitres("9999999999999999"));
    BeanPropertyBindingResult errors = errorsFor(request);

    ALIQUOT.validate(request, errors);

    assertEquals(
        "errors.inventory.operation.totalNotStorable",
        errors.getFieldError("eachAmount").getCode());
  }
}
