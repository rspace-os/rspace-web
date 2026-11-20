package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.SampleApiPostValidator;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.service.ApiFieldsHelper;
import com.researchspace.model.User;
import com.researchspace.model.dtos.DTOControllerValidatorImpl;
import com.researchspace.model.field.rule.ConditionOperator;
import com.researchspace.model.field.rule.FieldDisplayRule;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.field.InventoryNumberField;
import com.researchspace.model.inventory.field.InventoryRadioField;
import com.researchspace.model.inventory.field.InventoryRadioFieldDef;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.operations.AliquotOperation;
import com.researchspace.service.inventory.operations.OriginState;
import com.researchspace.service.inventory.operations.PassageOperation;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindException;

class OperationTemplateConformanceValidatorTest {

  private static ApiInventoryOperationPost aliquotRequest() {
    ApiInventoryOperationRequests.Aliquot request = new ApiInventoryOperationRequests.Aliquot();
    ApiInventoryOperationRequests.Origin origin = new ApiInventoryOperationRequests.Origin();
    origin.setGlobalId("SS100");
    origin.setAmountTaken(new ApiQuantityInfo(new BigDecimal("6"), RSUnitDef.MILLI_LITRE.getId()));
    request.setOrigin(origin);
    request.setSampleName("Aliquots");
    request.setCount(new BigDecimal("2"));
    request.setEachAmount(new ApiQuantityInfo(new BigDecimal("2"), RSUnitDef.MILLI_LITRE.getId()));
    return new AliquotOperation()
        .build(
            request,
            List.of(
                new OriginState(
                    100L,
                    "SS100",
                    "subsample",
                    new ApiQuantityInfo(new BigDecimal("10"), RSUnitDef.MILLI_LITRE.getId()),
                    List.of())),
            (key, args) -> key,
            LocalDate.parse("2026-08-20"));
  }

  private final SampleApiManager sampleApiMgr = mock(SampleApiManager.class);
  private final User user = mock(User.class);
  private OperationTemplateConformanceValidator validator;

  @BeforeEach
  void wire() {
    SampleApiPostValidator postValidator = new SampleApiPostValidator();
    // ValidationUtils.invokeValidator asserts supports() before delegating, so this mock has to
    // answer true rather than a mock's default false.
    ApiExtraFieldsHelper extraFieldHelper = mock(ApiExtraFieldsHelper.class);
    when(extraFieldHelper.supports(any())).thenReturn(true);
    ReflectionTestUtils.setField(postValidator, "extraFieldHelper", extraFieldHelper);
    SampleApiPostFullValidator fullValidator = new SampleApiPostFullValidator();
    ReflectionTestUtils.setField(fullValidator, "fieldHelper", mock(ApiFieldsHelper.class));
    validator =
        new OperationTemplateConformanceValidator(
            sampleApiMgr, new DTOControllerValidatorImpl(), postValidator, fullValidator);
  }

  @Test
  void aBuiltRequestWithNoTemplateIsAccepted() {
    assertDoesNotThrow(() -> validator.validate(aliquotRequest(), user));
  }

  @Test
  void rejectsATemplateIdThatDoesNotResolveToAReadableTemplate() {
    when(sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(999L, user))
        .thenThrow(new NotFoundException("no template"));
    ApiInventoryOperationPost built = aliquotRequest();
    built.getNewSample().setTemplateId(999L);

    BindException rejection =
        assertThrows(BindException.class, () -> validator.validate(built, user));

    assertEquals(
        "errors.inventory.sample.templateNotFound",
        rejection.getFieldErrors("newSample.templateId").get(0).getCode());
  }

  @Test
  void rejectsNewSubSamplesOutsideTheChosenTemplatesCategory() {
    ApiInventoryOperationPost built = templateBackedAliquot(RSUnitDef.GRAM);

    BindException rejection =
        assertThrows(BindException.class, () -> validator.validate(built, user));

    assertEquals(
        "errors.inventory.sample.unitIncompatibleWithTemplate",
        rejection.getFieldErrors("newSample.subSamples[0].quantity").get(0).getCode());
  }

  @Test
  void acceptsNewSubSamplesInAnotherUnitOfTheTemplatesCategory() {
    ApiInventoryOperationPost built = templateBackedAliquot(RSUnitDef.MICRO_LITRE);

    assertDoesNotThrow(() -> validator.validate(built, user));
  }

  @Test
  void boundsTheBuiltSampleNameLikeTheSamplesEndpointDoes() {
    // EditInfo.name is varchar(255).
    ApiInventoryOperationPost built = aliquotRequest();
    built.getNewSample().setName("x".repeat(256));

    BindException thrown = assertThrows(BindException.class, () -> validator.validate(built, user));

    assertTrue(
        thrown.getFieldErrors().stream().anyMatch(e -> "newSample.name".equals(e.getField())),
        () -> "expected a newSample.name rejection, got: " + thrown.getFieldErrors());
  }

  @Test
  void aMandatoryFieldHiddenByTheTemplateDefaultsIsNotRequired() {
    validator = validatorCheckingTemplateFields();

    assertDoesNotThrow(() -> validator.validate(aliquotOfGmoTemplate("No"), user));

    BindException rejection =
        assertThrows(
            BindException.class, () -> validator.validate(aliquotOfGmoTemplate("Yes"), user));
    assertEquals(
        "errors.inventory.field.mandatoryFieldEmpty",
        rejection.getFieldErrors("newSample.fields").get(0).getCode());
  }

  @Test
  void aPassageNumberHidingTheMandatoryFieldIsTheGeneratedOneNotTheTemplateDefault() {
    validator = validatorCheckingTemplateFields();
    SampleTemplate template = splitRatioTemplate(ConditionOperator.GREATER_THAN, "2");

    assertDoesNotThrow(() -> validator.validate(firstPassageInto(template), user));
    assertEquals("4", template.getActiveFields().get(0).getFieldData());
  }

  @Test
  void anExplicitlyPostedPassageNumberThatShowsTheFieldOverridesOneGeneratedToHideIt() {
    validator = validatorCheckingTemplateFields();
    // the template default 4 and the generated 1 both hide "Split ratio"; only the posted 5 shows
    // it
    ApiInventoryOperationPost built =
        firstPassageInto(splitRatioTemplate(ConditionOperator.GREATER_THAN, "4"));
    postPassageNumber(built, "5");

    BindException rejection =
        assertThrows(BindException.class, () -> validator.validate(built, user));
    assertEquals(
        "errors.inventory.field.mandatoryFieldEmpty",
        rejection.getFieldErrors("newSample.fields").get(0).getCode());
  }

  @Test
  void anExplicitlyPostedPassageNumberThatHidesTheFieldOverridesOneGeneratedToShowIt() {
    validator = validatorCheckingTemplateFields();
    // the template default 4 and the generated 1 both show "Split ratio"; only the posted 7 hides
    // it
    ApiInventoryOperationPost built =
        firstPassageInto(splitRatioTemplate(ConditionOperator.LESS_THAN, "5"));
    postPassageNumber(built, "7");

    assertDoesNotThrow(() -> validator.validate(built, user));
  }

  private OperationTemplateConformanceValidator validatorCheckingTemplateFields() {
    SampleApiPostValidator postValidator = new SampleApiPostValidator();
    ApiExtraFieldsHelper extraFieldHelper = mock(ApiExtraFieldsHelper.class);
    when(extraFieldHelper.supports(any())).thenReturn(true);
    ReflectionTestUtils.setField(postValidator, "extraFieldHelper", extraFieldHelper);
    SampleApiPostFullValidator fullValidator = new SampleApiPostFullValidator();
    ApiFieldsHelper fieldHelper = new ApiFieldsHelper();
    ReflectionTestUtils.setField(
        fieldHelper, "messages", new MessageSourceUtils(new JsonMessageSource()));
    ReflectionTestUtils.setField(fullValidator, "fieldHelper", fieldHelper);
    return new OperationTemplateConformanceValidator(
        sampleApiMgr, new DTOControllerValidatorImpl(), postValidator, fullValidator);
  }

  /** An aliquot into a template whose "GMO permit number" is mandatory, shown when GMO is Yes. */
  private ApiInventoryOperationPost aliquotOfGmoTemplate(String gmoDefault) {
    String gmoKey = "6f1c2e0a-8d5b-4c51-9a3e-2b7f1d0c4e9a";
    SampleTemplate template = new SampleTemplate();
    template.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());
    InventoryRadioFieldDef def = new InventoryRadioFieldDef();
    def.setRadioOptionsList(List.of("Yes", "No"));
    InventoryRadioField gmo = new InventoryRadioField(def, "Is this a GMO?");
    gmo.setFieldKey(gmoKey);
    gmo.setSelectedOptions(List.of(gmoDefault));
    template.addSampleField(gmo);
    InventoryStringField permit = new InventoryStringField("GMO permit number");
    permit.setMandatory(true);
    permit.setDisplayRule(
        new FieldDisplayRule(gmoKey, ConditionOperator.IS_ANY_OF, List.of("Yes")));
    template.addSampleField(permit);
    when(sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(8L, user)).thenReturn(template);
    ApiInventoryOperationPost built = aliquotRequest();
    built.getNewSample().setTemplateId(8L);
    return built;
  }

  /**
   * A template, served as id 9, whose "Passage number" defaults to 4 and whose mandatory, empty
   * "Split ratio" is shown while the passage number meets {@code operator} {@code threshold}.
   */
  private SampleTemplate splitRatioTemplate(ConditionOperator operator, String threshold) {
    String numberKey = "3c9e1f7a-2b4d-4e8f-a6c1-5d0b9e7f2a13";
    SampleTemplate template = new SampleTemplate();
    template.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());
    InventoryNumberField passageNumber = new InventoryNumberField("Passage number");
    passageNumber.setFieldKey(numberKey);
    passageNumber.setFieldData("4");
    template.addSampleField(passageNumber);
    InventoryStringField splitRatio = new InventoryStringField("Split ratio");
    splitRatio.setMandatory(true);
    splitRatio.setDisplayRule(new FieldDisplayRule(numberKey, operator, List.of(threshold)));
    template.addSampleField(splitRatio);
    when(sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(9L, user)).thenReturn(template);
    return template;
  }

  /**
   * A first passage into {@code template}: the origin has no number, so the operation generates 1.
   */
  private ApiInventoryOperationPost firstPassageInto(SampleTemplate template) {
    ApiInventoryOperationRequests.Passage request = new ApiInventoryOperationRequests.Passage();
    ApiInventoryOperationRequests.Origin origin = new ApiInventoryOperationRequests.Origin();
    origin.setGlobalId("SS100");
    request.setOrigin(origin);
    request.setSampleName("HeLa p1");
    request.setCount(BigDecimal.ONE);
    request.setEachAmount(new ApiQuantityInfo(new BigDecimal("2"), RSUnitDef.MILLI_LITRE.getId()));
    ApiInventoryOperationPost built =
        new PassageOperation()
            .build(
                request,
                List.of(
                    new OriginState(
                        100L,
                        "SS100",
                        "flask",
                        new ApiQuantityInfo(new BigDecimal("10"), RSUnitDef.MILLI_LITRE.getId()),
                        List.of())),
                (key, args) ->
                    "inventory:operations.passage.numberField".equals(key) ? "Passage number" : key,
                LocalDate.parse("2026-08-20"));
    built.getNewSample().setTemplateId(9L);
    return built;
  }

  private static void postPassageNumber(ApiInventoryOperationPost built, String value) {
    ApiInventoryEntityField passageNumber = new ApiInventoryEntityField();
    passageNumber.setContent(value);
    built.getNewSample().setFields(List.of(passageNumber, new ApiInventoryEntityField()));
  }

  private ApiInventoryOperationPost templateBackedAliquot(RSUnitDef childUnit) {
    SampleTemplate volumeTemplate = new SampleTemplate();
    volumeTemplate.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());
    when(sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(7L, user))
        .thenReturn(volumeTemplate);
    ApiInventoryOperationPost built = aliquotRequest();
    built.getNewSample().setTemplateId(7L);
    built
        .getNewSample()
        .getSubSamples()
        .get(0)
        .setQuantity(new ApiQuantityInfo(new BigDecimal("500"), childUnit.getId()));
    return built;
  }
}
