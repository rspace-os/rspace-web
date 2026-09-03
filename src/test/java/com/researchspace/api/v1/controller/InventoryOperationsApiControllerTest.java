package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.InventoryOperationPostValidatorTest.aliquotRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

/**
 * Unit coverage for the controller's structural pass: the request-shape validation chain runs
 * exactly as in production (real validators over mocked managers), and only a structurally valid
 * request reaches the transactional manager, which owns the live-state checks (DevDocs/adr/0007).
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

  @Test
  void validRequestReachesTheManagerAndReturnsItsResult() throws Exception {
    ApiInventoryOperationPost request = aliquotRequest();
    ApiSampleWithFullSubSamples created = new ApiSampleWithFullSubSamples("Aliquots");
    when(operationManager.performOperation(request, user)).thenReturn(created);

    ApiSampleWithFullSubSamples returned =
        controller.performOperation(
            request, new BeanPropertyBindingResult(request, "request"), user);

    assertSame(created, returned);
    verify(operationManager).performOperation(request, user);
  }

  @Test
  void rejectsATemplateIdThatDoesNotResolveToAReadableTemplate() {
    // Mirrors POST /samples: a bogus templateId must be a clean 400 here, not a failure inside
    // the manager transaction after it has started work.
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().setTemplateId(999L);
    when(sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(999L, user))
        .thenThrow(new NotFoundException("no template"));

    BindException rejection =
        assertThrows(
            BindException.class,
            () ->
                controller.performOperation(
                    request, new BeanPropertyBindingResult(request, "request"), user));
    assertEquals(
        "errors.inventory.sample.templateNotFound",
        rejection.getFieldErrors("newSample.templateId").get(0).getCode());
    verifyNoInteractions(operationManager);
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
    ApiInventoryOperationPost request = aliquotRequest();
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
    // The server derives the sample's total from its children and ignores the top-level quantity
    // when children are present, so the template's unit must be checked against every child, not
    // only the optional top-level value a caller can use as a decoy (code review, finding 5).
    ApiInventoryOperationPost request = aliquotRequest();
    request.getNewSample().setTemplateId(7L);
    request
        .getNewSample()
        .setQuantity(new ApiQuantityInfo(new BigDecimal("0.5"), RSUnitDef.MILLI_LITRE.getId()));
    request
        .getNewSample()
        .getSubSamples()
        .get(0)
        .setQuantity(new ApiQuantityInfo(new BigDecimal("0.5"), RSUnitDef.GRAM.getId()));
    SampleTemplate volumeTemplate = new SampleTemplate();
    volumeTemplate.setDefaultUnitId(RSUnitDef.MILLI_LITRE.getId());
    when(sampleApiMgr.getSampleTemplateByIdWithPopulatedFields(7L, user))
        .thenReturn(volumeTemplate);

    BindException rejection =
        assertThrows(
            BindException.class,
            () ->
                controller.performOperation(
                    request, new BeanPropertyBindingResult(request, "request"), user));
    assertEquals(
        "errors.inventory.sample.unitIncompatibleWithTemplate",
        rejection.getFieldErrors("newSample.subSamples[0].quantity").get(0).getCode());
    verifyNoInteractions(operationManager);
  }
}
