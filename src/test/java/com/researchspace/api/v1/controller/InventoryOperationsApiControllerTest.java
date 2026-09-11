package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.InventoryOperationPostValidatorTest.aliquotRequest;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.service.ApiFieldsHelper;
import com.researchspace.model.User;
import com.researchspace.model.dtos.DTOControllerValidatorImpl;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.webapp.config.WebConfig;
import jakarta.ws.rs.NotFoundException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

/**
 * Unit coverage for the controller's structural pass: the request-shape validation chain runs
 * exactly as in production (real validators over mocked managers), and only a structurally valid
 * request reaches the transactional manager, which validates the inputs, builds the sample and owns
 * the live-state checks (DevDocs/adr/0007).
 */
class InventoryOperationsApiControllerTest {

  private final InventoryOperationsApiController controller =
      new InventoryOperationsApiController();
  private final InventoryOperationManager operationManager = mock(InventoryOperationManager.class);
  private final User user = mock(User.class);

  private final SampleApiManager sampleApiMgr = mock(SampleApiManager.class);

  @BeforeEach
  void wireController() {
    controller.sampleApiMgr = sampleApiMgr;
    controller.inputValidator = new DTOControllerValidatorImpl();
    controller.operationPostValidator = InventoryOperationPostValidatorTest.newValidator();
    controller.operationConfigs = new InventoryOperationConfigRegistry();
    controller.sampleApiPostFullValidator = new SampleApiPostFullValidator();
    ReflectionTestUtils.setField(
        controller.sampleApiPostFullValidator, "fieldHelper", mock(ApiFieldsHelper.class));
    controller.inventoryOperationManager = operationManager;
  }

  /** An Aliquot in the shape the endpoint accepts: the origin, and the typed inputs as bound. */
  private static ApiInventoryOperationPost aliquotInputs() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("aliquot");
    ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
    origin.setId(100L);
    origin.setAmountTaken(
        new ApiQuantityInfo(new BigDecimal("0.6"), RSUnitDef.MILLI_LITRE.getId()));
    request.getOrigins().add(origin);
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("sampleName", "Aliquots");
    inputs.put("count", 2);
    // A quantity arrives from Jackson as a Map; the controller types it before the manager sees it.
    inputs.put("eachAmount", Map.of("numericValue", 0.5, "unitId", RSUnitDef.MILLI_LITRE.getId()));
    request.setInputs(inputs);
    return request;
  }

  /**
   * The template-conformance check the controller hands to the manager, which runs it inside the
   * operation's transaction on the request the server built (Copilot review, PR #1090). Captured
   * after a controller call so these tests can exercise it the way the manager does, against a
   * built request.
   */
  private InventoryOperationManager.BuiltRequestValidation handedInValidation() throws Exception {
    ArgumentCaptor<InventoryOperationManager.BuiltRequestValidation> captor =
        ArgumentCaptor.forClass(InventoryOperationManager.BuiltRequestValidation.class);
    verify(operationManager)
        .performOperation(eq("aliquot"), any(), any(), any(), any(), eq(user), captor.capture());
    return captor.getValue();
  }

  @Test
  void validRequestReachesTheManagerAndReturnsItsResult() throws Exception {
    ApiInventoryOperationPost request = aliquotInputs();
    ApiSampleWithFullSubSamples created = new ApiSampleWithFullSubSamples("Aliquots");
    when(operationManager.performOperation(
            eq("aliquot"), eq(request.getOrigins()), any(), any(), any(), eq(user), any()))
        .thenReturn(created);

    ApiSampleWithFullSubSamples returned =
        controller.performOperation(
            request, new BeanPropertyBindingResult(request, "request"), user);

    assertSame(created, returned);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> inputs = ArgumentCaptor.forClass(Map.class);
    verify(operationManager)
        .performOperation(eq("aliquot"), any(), inputs.capture(), any(), any(), eq(user), any());
    assertTrue(
        inputs.getValue().get("eachAmount") instanceof ApiQuantityInfo,
        "the bound quantity Map must reach the manager typed");
    InventoryOperationManager.BuiltRequestValidation validation = handedInValidation();
    assertDoesNotThrow(() -> validation.validate(aliquotRequest()));
  }

  @Test
  void rejectsATemplateIdThatDoesNotResolveToAReadableTemplate() throws Exception {
    // Mirrors POST /samples: a bogus templateId must be a clean 400, not a failure after the
    // manager has started mutating. The check runs inside the manager's transaction on the built
    // request, so it is asserted here by running the validation the controller hands in.
    ApiInventoryOperationPost request = aliquotInputs();
    request.setTemplateId(999L);
    when(sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(999L, user))
        .thenThrow(new NotFoundException("no template"));

    controller.performOperation(request, new BeanPropertyBindingResult(request, "request"), user);

    ApiInventoryOperationPost built = aliquotRequest();
    built.getNewSample().setTemplateId(999L);
    InventoryOperationManager.BuiltRequestValidation validation = handedInValidation();
    BindException rejection = assertThrows(BindException.class, () -> validation.validate(built));
    assertEquals(
        "errors.inventory.sample.templateNotFound",
        rejection.getFieldErrors("newSample.templateId").get(0).getCode());
  }

  @Test
  void yamlBodiesAreRejectedWith415BeforeAnyInventoryEffect() throws Exception {
    // The app registers a global YAML converter (WebConfig.YamlJackson2HttpMessageConverter), so
    // without an explicit JSON-only consumes clause this endpoint would bind YAML bodies too. The
    // same converter is registered here so this test fails if the consumes guard is ever dropped.
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(),
                new WebConfig.YamlJackson2HttpMessageConverter())
            .build();
    for (String yamlType : List.of("application/x-yaml", "application/yaml", "text/yaml")) {
      mvc.perform(
              post("/api/inventory/v1/operations")
                  .contentType(yamlType)
                  .content("operationType: aliquot"))
          .andExpect(status().isUnsupportedMediaType());
    }
    verifyNoInteractions(operationManager);
  }

  @Test
  void servesTheOperationDefinitionsVerbatimAsJson() throws Exception {
    // The frontend has no copy of operations_config.json (DevDocs/adr/0007): the wizard fetches
    // this endpoint and renders whatever the backend's authoritative copy declares.
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
    mvc.perform(get("/api/inventory/v1/operations/config"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("application/json"))
        .andExpect(content().string(controller.operationConfigs.rawConfigJson()));
  }

  @Test
  void structuralFailureNeverReachesTheManager() {
    ApiInventoryOperationPost request = aliquotInputs();
    request.setOperationType("teleport");
    assertThrows(
        BindException.class,
        () ->
            controller.performOperation(
                request, new BeanPropertyBindingResult(request, "request"), user));
    verifyNoInteractions(operationManager);
  }

  @Test
  void rejectsNewSubSamplesOutsideTheChosenTemplatesCategory() throws Exception {
    // The server derives the sample's total from its children, so the template's unit must be
    // checked against every child the builder produced, not only the aggregate (code review,
    // finding 5).
    ApiInventoryOperationPost request = aliquotInputs();
    request.setTemplateId(7L);
    SampleTemplate volumeTemplate = new SampleTemplate();
    volumeTemplate.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());
    when(sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(7L, user))
        .thenReturn(volumeTemplate);

    controller.performOperation(request, new BeanPropertyBindingResult(request, "request"), user);

    ApiInventoryOperationPost built = aliquotRequest();
    built.getNewSample().setTemplateId(7L);
    built
        .getNewSample()
        .getSubSamples()
        .get(0)
        .setQuantity(new ApiQuantityInfo(new BigDecimal("0.5"), RSUnitDef.GRAM.getId()));
    InventoryOperationManager.BuiltRequestValidation validation = handedInValidation();
    BindException rejection = assertThrows(BindException.class, () -> validation.validate(built));
    assertEquals(
        "errors.inventory.sample.unitIncompatibleWithTemplate",
        rejection.getFieldErrors("newSample.subSamples[0].quantity").get(0).getCode());
  }

  @Test
  void acceptsNewSubSamplesInAnotherUnitOfTheTemplatesCategory() throws Exception {
    // The template fixes the measurement category, not the exact unit, so microlitre children under
    // a millilitre template are a legitimate request: the check above must not have tightened into
    // unit equality (code review, finding 5).
    ApiInventoryOperationPost request = aliquotInputs();
    request.setTemplateId(7L);
    SampleTemplate volumeTemplate = new SampleTemplate();
    volumeTemplate.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());
    when(sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(7L, user))
        .thenReturn(volumeTemplate);

    controller.performOperation(request, new BeanPropertyBindingResult(request, "request"), user);

    ApiInventoryOperationPost built = aliquotRequest();
    built.getNewSample().setTemplateId(7L);
    built
        .getNewSample()
        .getSubSamples()
        .get(0)
        .setQuantity(new ApiQuantityInfo(new BigDecimal("500"), RSUnitDef.MICRO_LITRE.getId()));
    InventoryOperationManager.BuiltRequestValidation validation = handedInValidation();
    assertDoesNotThrow(() -> validation.validate(built));
  }
}
