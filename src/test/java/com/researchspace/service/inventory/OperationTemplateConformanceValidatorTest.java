package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.SampleApiPostValidator;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.service.ApiFieldsHelper;
import com.researchspace.model.User;
import com.researchspace.model.dtos.DTOControllerValidatorImpl;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.operations.AliquotOperation;
import com.researchspace.service.inventory.operations.OriginState;
import jakarta.ws.rs.NotFoundException;
import java.beans.Introspector;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindException;

class OperationTemplateConformanceValidatorTest {

  /**
   * A request as the Aliquot operation actually builds one, so what the validator sees here is what
   * it sees in production rather than a hand-assembled lookalike.
   */
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

  /**
   * {@code sampleApiPostValidator} is injected by bean name, not by type. The bean has no explicit
   * name, so the qualifier resolves only through Spring's default naming - renaming the class
   * breaks the wire at context startup with no compile-time warning.
   */
  @Test
  void theQualifierNamingTheControllerValidatorStillResolves() throws Exception {
    String qualifier =
        Arrays.stream(
                OperationTemplateConformanceValidator.class.getDeclaredConstructors()[0]
                    .getParameterAnnotations())
            .flatMap(Arrays::stream)
            .filter(Qualifier.class::isInstance)
            .map(annotation -> ((Qualifier) annotation).value())
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError(
                        "the constructor no longer injects anything by bean name; if the validator"
                            + " is now injected by type, delete this test"));

    Class<?> bean =
        Class.forName("com.researchspace.api.v1.controller." + "SampleApiPostValidator");
    assertTrue(
        bean.isAnnotationPresent(Component.class),
        bean.getName() + " must be a Spring bean for the qualifier above to resolve");
    assertEquals(
        "",
        bean.getAnnotation(Component.class).value(),
        bean.getName()
            + " now declares an explicit bean name, so the default-naming assumption below no"
            + " longer holds");
    assertEquals(
        qualifier,
        Introspector.decapitalize(bean.getSimpleName()),
        "OperationTemplateConformanceValidator injects the bean named '"
            + qualifier
            + "', which Spring derives from the class name. Renaming "
            + bean.getSimpleName()
            + " breaks that wire at context startup, with nothing at compile time to say so.");
  }

  @BeforeEach
  void wire() {
    SampleApiPostValidator postValidator = new SampleApiPostValidator();
    // The per-extra-field validator is a collaborator these tests do not assert on, but
    // ValidationUtils.invokeValidator asserts supports() before delegating, so it has to answer
    // true rather than a mock's default false.
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
    // The template's unit is checked against every child, not only the sample's aggregate total.
    ApiInventoryOperationPost built = templateBackedAliquot(RSUnitDef.GRAM);

    BindException rejection =
        assertThrows(BindException.class, () -> validator.validate(built, user));

    assertEquals(
        "errors.inventory.sample.unitIncompatibleWithTemplate",
        rejection.getFieldErrors("newSample.subSamples[0].quantity").get(0).getCode());
  }

  @Test
  void acceptsNewSubSamplesInAnotherUnitOfTheTemplatesCategory() {
    // The template fixes the measurement category, not the exact unit.
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
