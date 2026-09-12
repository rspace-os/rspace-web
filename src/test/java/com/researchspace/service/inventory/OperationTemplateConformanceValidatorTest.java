package com.researchspace.service.inventory;

import static com.researchspace.api.v1.controller.InventoryOperationPostValidatorTest.aliquotRequest;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.SampleApiPostValidator;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.service.ApiFieldsHelper;
import com.researchspace.model.User;
import com.researchspace.model.dtos.DTOControllerValidatorImpl;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.units.RSUnitDef;
import jakarta.ws.rs.NotFoundException;
import java.beans.Introspector;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindException;

/**
 * The operation's template-conformance invariant, tested where it lives. It used to be a private
 * method of {@code InventoryOperationsApiController}, handed to the manager as a callback, so these
 * cases could only be reached by capturing that callback from a controller call (parallel review,
 * L1). They now run the check directly, which is also what a reader of {@code service.inventory}
 * would expect to find.
 */
class OperationTemplateConformanceValidatorTest {

  private final SampleApiManager sampleApiMgr = mock(SampleApiManager.class);
  private final User user = mock(User.class);
  private OperationTemplateConformanceValidator validator;

  /**
   * The one wire this class has that the compiler cannot see.
   *
   * <p>{@code sampleApiPostValidator} is injected as a plain {@link org.springframework.validation
   * .Validator} by BEAN NAME, deliberately: naming its type would put back the {@code
   * api.v1.controller} import this class was moved out of the controller package to remove. The
   * compile-time arrow is gone; the runtime one is not, and it is invisible to the compiler, to IDE
   * refactoring and to any static layering check. The bean has no explicit name, so the qualifier
   * matches only through Spring's default naming - rename the class and the context fails to start,
   * which nothing short of an MVCIT would otherwise catch (parallel review, A8).
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
    // Mirrors POST /samples: a bogus templateId must be a clean 400, not a failure after the
    // manager has started mutating.
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
    // The server derives the sample's total from its children, so the template's unit must be
    // checked against every child the builder produced, not only the aggregate (code review,
    // finding 5).
    ApiInventoryOperationPost built = templateBackedAliquot(RSUnitDef.GRAM);

    BindException rejection =
        assertThrows(BindException.class, () -> validator.validate(built, user));

    assertEquals(
        "errors.inventory.sample.unitIncompatibleWithTemplate",
        rejection.getFieldErrors("newSample.subSamples[0].quantity").get(0).getCode());
  }

  @Test
  void acceptsNewSubSamplesInAnotherUnitOfTheTemplatesCategory() {
    // The template fixes the measurement category, not the exact unit, so microlitre children under
    // a millilitre template are a legitimate request: the check above must not have tightened into
    // unit equality (code review, finding 5).
    ApiInventoryOperationPost built = templateBackedAliquot(RSUnitDef.MICRO_LITRE);

    assertDoesNotThrow(() -> validator.validate(built, user));
  }

  @Test
  void boundsTheBuiltSampleNameLikeTheSamplesEndpointDoes() {
    // SamplesApiController.validateCreateSampleInput runs sampleApiPostValidator AND
    // sampleApiPostFullValidator; this path ran only the second, so the name/description/tag length
    // rules never applied. EditInfo.name is varchar(255), and nothing between the input validator
    // (which only checks that a "text" input is a CharSequence) and the entity bounded it, so an
    // over-long sampleName reached Hibernate inside the transaction, after the origin locks were
    // taken (parallel review).
    ApiInventoryOperationPost built = aliquotRequest();
    built.getNewSample().setName("x".repeat(256));

    BindException thrown = assertThrows(BindException.class, () -> validator.validate(built, user));

    assertTrue(
        thrown.getFieldErrors().stream().anyMatch(e -> "newSample.name".equals(e.getField())),
        () -> "expected a newSample.name rejection, got: " + thrown.getFieldErrors());
  }

  /** An Aliquot under a millilitre template, its first child measured in {@code childUnit}. */
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
