package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.InventoryOperationPostValidatorTest.aliquotRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiInventoryOperationResult;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.dtos.DTOControllerValidatorImpl;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.webapp.config.WebConfig;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

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
  private final SubSampleApiManager subSampleApiMgr = mock(SubSampleApiManager.class);

  @BeforeEach
  void wireController() {
    controller.sampleApiMgr = sampleApiMgr;
    controller.subSampleApiMgr = subSampleApiMgr;
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example");
    controller.properties = properties;
    controller.inputValidator = new DTOControllerValidatorImpl();
    controller.operationPostValidator = InventoryOperationPostValidatorTest.newValidator();
    controller.operationConfigs = new InventoryOperationConfigRegistry();
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

  @Test
  void validRequestReachesTheManagerAndReturnsItsResult() throws Exception {
    ApiInventoryOperationPost request = aliquotInputs();
    ApiSampleWithFullSubSamples created = new ApiSampleWithFullSubSamples("Aliquots");
    when(operationManager.performOperation(
            eq("aliquot"), eq(request.getOrigins()), any(), any(), any(), eq(user)))
        .thenReturn(created);

    ApiSampleWithFullSubSamples returned =
        controller.performOperation(
            request, new BeanPropertyBindingResult(request, "request"), user);

    assertSame(created, returned);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> inputs = ArgumentCaptor.forClass(Map.class);
    verify(operationManager)
        .performOperation(eq("aliquot"), any(), inputs.capture(), any(), any(), eq(user));
    assertTrue(
        inputs.getValue().get("eachAmount") instanceof ApiQuantityInfo,
        "the bound quantity Map must reach the manager typed");
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

  // --- the typed facades (M6) ---

  private static ApiInventoryOperationRequests.Origin facadeOrigin(
      String globalId, ApiQuantityInfo amountTaken) {
    ApiInventoryOperationRequests.Origin origin = new ApiInventoryOperationRequests.Origin();
    origin.setGlobalId(globalId);
    origin.setAmountTaken(amountTaken);
    return origin;
  }

  private static ApiQuantityInfo millilitres(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.MILLI_LITRE.getId());
  }

  /** An Aliquot in M0's shape, taking 0.6 ml from SS100 into two 0.5 ml children. */
  private static ApiInventoryOperationRequests.Aliquot aliquotFacade() {
    ApiInventoryOperationRequests.Aliquot request = new ApiInventoryOperationRequests.Aliquot();
    request.setOrigin(facadeOrigin("SS100", millilitres("0.6")));
    request.setSampleName("Aliquots");
    request.setCount(2);
    request.setEachAmount(millilitres("0.5"));
    return request;
  }

  private static BindingResult bindingResultFor(Object request) {
    return new BeanPropertyBindingResult(request, "request");
  }

  private void originReadsBackAs(long id) {
    ApiSubSample after = new ApiSubSample();
    after.setId(id);
    when(subSampleApiMgr.getApiSubSampleById(id, user)).thenReturn(after);
  }

  @Test
  void facadeFieldRenamesEveryCorePathToTheFieldTheCallerSent() {
    // Single-origin: the list the core works with collapses to the singular the client sent.
    assertEquals("origin.amountTaken", facadeField("origins[0].amountTaken", true));
    assertEquals("origin.globalId", facadeField("origins[0].id", true));
    assertEquals("origin", facadeField("origins", true));
    assertEquals("origin.amountMode", facadeField("origins[0].amountMode", true));
    // Pool keeps the plural and the index; only the id becomes the global id.
    assertEquals("origins[1].globalId", facadeField("origins[1].id", false));
    assertEquals("origins[1].amountTaken", facadeField("origins[1].amountTaken", false));
    assertEquals("origins", facadeField("origins", false));
    // The built sample is the server's; what the template check finds on it maps back to the
    // caller's template id and eachAmount.
    assertEquals("templateId", facadeField("newSample.templateId", true));
    assertEquals("eachAmount", facadeField("newSample.subSamples[3].quantity", true));
    // Bare input keys and the documentation target already are the caller's names.
    assertEquals("sampleName", facadeField("sampleName", true));
    assertEquals("documentedByGlobalId", facadeField("documentedByGlobalId", false));
  }

  private static String facadeField(String field, boolean singleOrigin) {
    return InventoryOperationsApiController.facadeField(field, singleOrigin);
  }

  @Test
  void typedFacadeCreatesThroughTheSameManagerCallAndAnswers201WithALocation() throws Exception {
    ApiSampleWithFullSubSamples created = new ApiSampleWithFullSubSamples("Aliquots");
    created.setId(55L);
    when(operationManager.performOperation(eq("aliquot"), any(), any(), any(), any(), eq(user)))
        .thenReturn(created);
    originReadsBackAs(100L);
    ApiInventoryOperationRequests.Aliquot request = aliquotFacade();
    request.setTemplateId(42L);
    request.setDocumentedByGlobalId("SD99");

    ResponseEntity<ApiInventoryOperationResult> response =
        controller.aliquot(request, bindingResultFor(request), user);

    assertEquals(201, response.getStatusCode().value());
    assertEquals(
        "https://rspace.example/api/inventory/v1/samples/55",
        response.getHeaders().getFirst(HttpHeaders.LOCATION));
    assertSame(created, response.getBody().getSample());
    assertEquals(Long.valueOf(100L), response.getBody().getOrigins().get(0).getId());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ApiInventoryOperationOriginUpdate>> origins =
        ArgumentCaptor.forClass(List.class);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> inputs = ArgumentCaptor.forClass(Map.class);
    verify(operationManager)
        .performOperation(
            eq("aliquot"), origins.capture(), inputs.capture(), eq(42L), eq("SD99"), eq(user));
    assertEquals(Long.valueOf(100L), origins.getValue().get(0).getId());
    assertEquals(millilitres("0.6"), origins.getValue().get(0).getAmountTaken());
    assertEquals(
        Map.of("sampleName", "Aliquots", "count", 2, "eachAmount", millilitres("0.5")),
        inputs.getValue());
  }

  @Test
  void destroyAnswers200WithANullSampleAndTheOriginAsItStands() throws Exception {
    when(operationManager.performOperation(eq("destroy"), any(), any(), any(), any(), eq(user)))
        .thenReturn(null);
    originReadsBackAs(100L);
    ApiInventoryOperationRequests.Destroy request = new ApiInventoryOperationRequests.Destroy();
    ApiInventoryOperationRequests.Origin origin = facadeOrigin("SS100", null);
    origin.setExpectedQuantity(millilitres("5"));
    request.setOrigin(origin);

    ResponseEntity<ApiInventoryOperationResult> response =
        controller.destroy(request, bindingResultFor(request), user);

    assertEquals(200, response.getStatusCode().value());
    assertNull(response.getHeaders().getFirst(HttpHeaders.LOCATION));
    assertNull(response.getBody().getSample());
    assertEquals(Long.valueOf(100L), response.getBody().getOrigins().get(0).getId());
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<ApiInventoryOperationOriginUpdate>> origins =
        ArgumentCaptor.forClass(List.class);
    verify(operationManager)
        .performOperation(eq("destroy"), origins.capture(), eq(Map.of()), any(), any(), eq(user));
    // No amount: the manager's builder takes the whole origin. The expected quantity travels.
    assertNull(origins.getValue().get(0).getAmountTaken());
    assertEquals(millilitres("5"), origins.getValue().get(0).getExpectedQuantity());
  }

  @Test
  void passageSendsNoAmountAndStillReachesTheManager() throws Exception {
    // The structural validator must accept an absent amount where the definition takes nothing.
    when(operationManager.performOperation(eq("passage"), any(), any(), any(), any(), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("HeLa p3"));
    originReadsBackAs(100L);
    ApiInventoryOperationRequests.Passage request = new ApiInventoryOperationRequests.Passage();
    request.setOrigin(facadeOrigin("SS100", null));
    request.setSampleName("HeLa p3");
    request.setEachAmount(millilitres("5"));

    assertEquals(
        201, controller.passage(request, bindingResultFor(request), user).getStatusCode().value());
  }

  @Test
  void typedFacadeRejectsAnOriginThatIsNotASubsampleGlobalId() {
    for (String notASubSample : new String[] {"SA100", "IC7", "100", "garbage", null}) {
      ApiInventoryOperationRequests.Aliquot request = aliquotFacade();
      request.getOrigin().setGlobalId(notASubSample);
      BindException rejection =
          assertThrows(
              BindException.class,
              () -> controller.aliquot(request, bindingResultFor(request), user),
              String.valueOf(notASubSample));
      assertEquals(
          "errors.inventory.operation.originGlobalIdInvalid",
          rejection.getFieldErrors("origin.globalId").get(0).getCode(),
          String.valueOf(notASubSample));
    }
    verifyNoInteractions(operationManager);
  }

  @Test
  void aCoreRejectionIsRenamedToTheFieldTheCallerSent() throws Exception {
    // The manager reports the live-state rules under origins[i], as it does for the wizard; the
    // aliquot client sent origin.amountTaken and must be told about that field (M0, M6).
    ApiInventoryOperationPost generic = aliquotRequest();
    BeanPropertyBindingResult coreErrors = new BeanPropertyBindingResult(generic, "request");
    coreErrors.rejectValue(
        "origins[0].amountTaken",
        "errors.inventory.operation.amountTakenExceedsOrigin",
        "Cannot take more from an origin than it currently holds.");
    when(operationManager.performOperation(eq("aliquot"), any(), any(), any(), any(), eq(user)))
        .thenThrow(new BindException(coreErrors));
    ApiInventoryOperationRequests.Aliquot request = aliquotFacade();

    BindException rejection =
        assertThrows(
            BindException.class,
            () -> controller.aliquot(request, bindingResultFor(request), user));

    FieldError renamed = rejection.getFieldErrors().get(0);
    assertEquals("origin.amountTaken", renamed.getField());
    assertEquals("errors.inventory.operation.amountTakenExceedsOrigin", renamed.getCode());
    assertEquals(
        "Cannot take more from an origin than it currently holds.", renamed.getDefaultMessage());
    assertTrue(rejection.getFieldErrors("origins[0].amountTaken").isEmpty());
    verifyNoInteractions(subSampleApiMgr);
  }

  @Test
  void poolKeepsThePluralPathAndNamesTheGlobalId() {
    // The structural validator rejects a repeated origin on origins[1].id; the pool client sent
    // origins[1].globalId.
    ApiInventoryOperationRequests.Pool request = new ApiInventoryOperationRequests.Pool();
    request.setOrigins(
        List.of(facadeOrigin("SS100", millilitres("1")), facadeOrigin("SS100", millilitres("1"))));
    request.setSampleName("Pooled");
    request.setEachAmount(millilitres("2"));

    BindException rejection =
        assertThrows(
            BindException.class, () -> controller.pool(request, bindingResultFor(request), user));

    assertEquals(
        "errors.inventory.operation.duplicateOrigin",
        rejection.getFieldErrors("origins[1].globalId").get(0).getCode());
    verifyNoInteractions(operationManager);
  }
}
