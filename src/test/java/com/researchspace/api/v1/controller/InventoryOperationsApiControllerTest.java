package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.InventoryOperationPostValidatorTest.aliquotRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiInventoryOperationResult;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.User;
import com.researchspace.model.dtos.DTOControllerValidatorImpl;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.inventory.InventoryEditLockHeldException;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.InventoryOperationManager.OperationOutcome;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.service.inventory.impl.InventoryEditLockTracker;
import com.researchspace.webapp.config.WebConfig;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
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
 * the live-state checks.
 */
class InventoryOperationsApiControllerTest {

  private final InventoryOperationsApiController controller =
      new InventoryOperationsApiController();
  private final InventoryOperationManager operationManager = mock(InventoryOperationManager.class);
  private final User user = mock(User.class);

  private final SampleApiManager sampleApiMgr = mock(SampleApiManager.class);
  private final SubSampleApiManager subSampleApiMgr = mock(SubSampleApiManager.class);
  private final InventoryEditLockTracker tracker = mock(InventoryEditLockTracker.class);
  private final SystemPropertyPermissionManager systemPropertyManager =
      mock(SystemPropertyPermissionManager.class);

  private void operationsDenied() {
    when(systemPropertyManager.isPropertyAllowed(
            user, SystemPropertyName.INVENTORY_OPERATIONS_AVAILABLE))
        .thenReturn(false);
  }

  private void originExists(long subSampleId, long sampleId) {
    Sample sample = new Sample();
    sample.setId(sampleId);
    SubSample subSample = new SubSample(sample);
    subSample.setId(subSampleId);
    when(subSampleApiMgr.assertUserCanEditSubSample(subSampleId, user)).thenReturn(subSample);
  }

  private void lockIsFree(String globalId) {
    when(tracker.attemptToLockForEdit(eq(globalId), any()))
        .thenReturn(lock(globalId, ApiInventoryEditLockStatus.LOCKED_OK));
  }

  private static ApiInventoryEditLock lock(String globalId, ApiInventoryEditLockStatus status) {
    ApiInventoryEditLock editLock = new ApiInventoryEditLock();
    editLock.setGlobalId(globalId);
    editLock.setStatus(status);
    editLock.setOwner(new ApiUser(1L, "carol", "carol@x.com", "Carol", "Holder"));
    return editLock;
  }

  @BeforeEach
  void wireController() {
    controller.sampleApiMgr = sampleApiMgr;
    controller.subSampleApiMgr = subSampleApiMgr;
    controller.tracker = tracker;
    originExists(100L, 10L);
    lockIsFree("SS100");
    lockIsFree("SA10");
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example");
    controller.properties = properties;
    controller.inputValidator = new DTOControllerValidatorImpl();
    controller.operationPostValidator = InventoryOperationPostValidatorTest.newValidator();
    controller.operationConfigs = new InventoryOperationConfigRegistry();
    controller.inventoryOperationManager = operationManager;
    controller.systemPropertyManager = systemPropertyManager;
    when(systemPropertyManager.isPropertyAllowed(
            user, SystemPropertyName.INVENTORY_OPERATIONS_AVAILABLE))
        .thenReturn(true);
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    when(messages.getMessage(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    controller.setMessageSource(messages);
  }

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
        .thenReturn(new OperationOutcome(created, List.of(originAfter(100L))));

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

  /**
   * A converter configured the way {@code WebConfig.extendMessageConverters} configures the app's
   * real one: {@code USE_BIG_DECIMAL_FOR_FLOATS} enabled, so a JSON number written with a decimal
   * point binds as {@link BigDecimal} rather than {@code Double} wherever Jackson has to guess an
   * untyped value's Java type.
   */
  private static MappingJackson2HttpMessageConverter preciseJsonConverter() {
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter.getObjectMapper().configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
    return converter;
  }

  @Test
  void genericAndTypedEndpointsAgreeOnAQuantityBeyondDoublesExactPrecision() throws Exception {
    // 9007199254740992 is 2^53, the largest integer a double represents exactly; appending ".001"
    // demands more significant digits than a double has room for, so an untyped Double binding
    // cannot hold it. Both endpoints must agree on the value, since they are two doors onto the
    // same request.
    String precise = "9007199254740992.001";
    ApiSampleWithFullSubSamples created = new ApiSampleWithFullSubSamples("Precise");
    when(operationManager.performOperation(any(), any(), any(), any(), any(), eq(user)))
        .thenReturn(new OperationOutcome(created, List.of(originAfter(100L))));
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(preciseJsonConverter())
            .build();

    mvc.perform(
            post("/api/inventory/v1/operations")
                .contentType("application/json")
                .requestAttr("user", user)
                .content(
                    "{\"operationType\":\"aliquot\","
                        + "\"origins\":[{\"id\":100,\"amountMode\":\"explicit\","
                        + "\"amountTaken\":{\"numericValue\":1,\"unitId\":3}}],"
                        + "\"inputs\":{\"sampleName\":\"Precise\",\"count\":1,"
                        + "\"eachAmount\":{\"numericValue\":"
                        + precise
                        + ",\"unitId\":3}}}"))
        .andExpect(status().isCreated());
    mvc.perform(
            post("/api/inventory/v1/operations/aliquot")
                .contentType("application/json")
                .requestAttr("user", user)
                .content(
                    "{\"origin\":{\"globalId\":\"SS100\","
                        + "\"amountTaken\":{\"numericValue\":1,\"unitId\":3}},"
                        + "\"sampleName\":\"Precise\",\"count\":1,"
                        + "\"eachAmount\":{\"numericValue\":"
                        + precise
                        + ",\"unitId\":3}}"))
        .andExpect(status().isCreated());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> inputs = ArgumentCaptor.forClass(Map.class);
    verify(operationManager, times(2))
        .performOperation(eq("aliquot"), any(), inputs.capture(), any(), any(), eq(user));
    for (Map<String, Object> captured : inputs.getAllValues()) {
      ApiQuantityInfo eachAmount = (ApiQuantityInfo) captured.get("eachAmount");
      assertEquals(new BigDecimal(precise), eachAmount.getNumericValue());
    }
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
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
    mvc.perform(get("/api/inventory/v1/operations/config").requestAttr("user", user))
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

  // --- the edit-session lock the controller holds around the manager ---

  /** Locks every origin plus every distinct parent sample, in ascending global-id order. */
  @Test
  void locksEveryOriginAndParentSampleInAscendingOrder() throws Exception {
    originExists(300L, 20L);
    lockIsFree("SS300");
    lockIsFree("SA20");
    ApiInventoryOperationPost request = poolInputs(300L, 100L);
    when(operationManager.performOperation(any(), any(), any(), any(), any(), eq(user)))
        .thenReturn(new OperationOutcome(new ApiSampleWithFullSubSamples("Pooled"), List.of()));

    controller.performOperation(request, new BeanPropertyBindingResult(request, "request"), user);

    InOrder inOrder = inOrder(tracker);
    inOrder.verify(tracker).attemptToLockForEdit("SA10", user);
    inOrder.verify(tracker).attemptToLockForEdit("SA20", user);
    inOrder.verify(tracker).attemptToLockForEdit("SS100", user);
    inOrder.verify(tracker).attemptToLockForEdit("SS300", user);
  }

  @Test
  void releasesOnlyTheLocksItTookItself() throws Exception {
    when(tracker.attemptToLockForEdit(eq("SA10"), any()))
        .thenReturn(lock("SA10", ApiInventoryEditLockStatus.WAS_ALREADY_LOCKED));
    ApiInventoryOperationPost request = aliquotInputs();
    when(operationManager.performOperation(any(), any(), any(), any(), any(), eq(user)))
        .thenReturn(new OperationOutcome(new ApiSampleWithFullSubSamples("Aliquots"), List.of()));

    controller.performOperation(request, new BeanPropertyBindingResult(request, "request"), user);

    verify(tracker).attemptToUnlock("SS100", user);
    verify(tracker, never()).attemptToUnlock("SA10", user);
  }

  @Test
  void aHeldLockReleasesWhatWasTakenAndNeverReachesTheManager() {
    when(tracker.attemptToLockForEdit(eq("SS100"), any()))
        .thenReturn(lock("SS100", ApiInventoryEditLockStatus.CANNOT_LOCK));
    ApiInventoryOperationPost request = aliquotInputs();

    InventoryEditLockHeldException held =
        assertThrows(
            InventoryEditLockHeldException.class,
            () ->
                controller.performOperation(
                    request, new BeanPropertyBindingResult(request, "request"), user));

    assertEquals("SS100", held.getGlobalId());
    assertEquals("Carol Holder", held.getOwnerDisplayName());
    verify(tracker).attemptToUnlock("SA10", user);
    verifyNoInteractions(operationManager);
  }

  /** A rejected operation must not leave the origins locked for the next five minutes. */
  @Test
  void releasesTheLocksWhenTheManagerRejectsTheRequest() throws Exception {
    ApiInventoryOperationPost request = aliquotInputs();
    when(operationManager.performOperation(any(), any(), any(), any(), any(), eq(user)))
        .thenThrow(new IllegalStateException("boom"));

    assertThrows(
        IllegalStateException.class,
        () ->
            controller.performOperation(
                request, new BeanPropertyBindingResult(request, "request"), user));

    verify(tracker).attemptToUnlock("SS100", user);
    verify(tracker).attemptToUnlock("SA10", user);
  }

  /**
   * Permission is asserted on every origin before the first lock is taken, so a caller who may not
   * edit an origin cannot hold other users' records for the duration of the request.
   */
  @Test
  void assertsEditPermissionOnEveryOriginBeforeTakingAnyLock() {
    when(subSampleApiMgr.assertUserCanEditSubSample(100L, user))
        .thenThrow(new org.apache.shiro.authz.AuthorizationException("no"));
    ApiInventoryOperationPost request = aliquotInputs();

    assertThrows(
        org.apache.shiro.authz.AuthorizationException.class,
        () ->
            controller.performOperation(
                request, new BeanPropertyBindingResult(request, "request"), user));

    verifyNoInteractions(tracker);
    verifyNoInteractions(operationManager);
  }

  private static ApiInventoryOperationPost poolInputs(long... originIds) {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
    for (long id : originIds) {
      ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
      origin.setId(id);
      origin.setAmountTaken(
          new ApiQuantityInfo(new BigDecimal("0.1"), RSUnitDef.MILLI_LITRE.getId()));
      request.getOrigins().add(origin);
    }
    Map<String, Object> inputs = new LinkedHashMap<>();
    inputs.put("sampleName", "Pooled");
    inputs.put("eachAmount", Map.of("numericValue", 0.2, "unitId", RSUnitDef.MILLI_LITRE.getId()));
    request.setInputs(inputs);
    return request;
  }

  // --- the typed facades ---

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

  private static ApiInventoryOperationRequests.Aliquot aliquotFacade() {
    ApiInventoryOperationRequests.Aliquot request = new ApiInventoryOperationRequests.Aliquot();
    request.setOrigin(facadeOrigin("SS100", millilitres("0.6")));
    request.setSampleName("Aliquots");
    request.setCount(java.math.BigDecimal.valueOf(2));
    request.setEachAmount(millilitres("0.5"));
    return request;
  }

  private static BindingResult bindingResultFor(Object request) {
    return new BeanPropertyBindingResult(request, "request");
  }

  /** The origin as the manager reports it afterwards; the controller does not re-read it. */
  private static ApiSubSample originAfter(long id) {
    ApiSubSample after = new ApiSubSample();
    after.setId(id);
    return after;
  }

  @Test
  void facadeFieldRenamesEveryCorePathToTheFieldTheCallerSent() {
    assertEquals("origin.amountTaken", facadeField("origins[0].amountTaken", true));
    assertEquals("origin.globalId", facadeField("origins[0].id", true));
    assertEquals("origin", facadeField("origins", true));
    assertEquals("origin.amountMode", facadeField("origins[0].amountMode", true));
    assertEquals("origins[1].globalId", facadeField("origins[1].id", false));
    assertEquals("origins[1].amountTaken", facadeField("origins[1].amountTaken", false));
    assertEquals("origins", facadeField("origins", false));
    assertEquals("templateId", facadeField("newSample.templateId", true));
    assertEquals("eachAmount", facadeField("newSample.subSamples[3].quantity", true));
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
        .thenReturn(new OperationOutcome(created, List.of(originAfter(100L))));
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
        .thenReturn(new OperationOutcome(null, List.of(originAfter(100L))));
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
    assertNull(origins.getValue().get(0).getAmountTaken());
    assertEquals(millilitres("5"), origins.getValue().get(0).getExpectedQuantity());
  }

  @Test
  void passageSendsNoAmountAndStillReachesTheManager() throws Exception {
    when(operationManager.performOperation(eq("passage"), any(), any(), any(), any(), eq(user)))
        .thenReturn(
            new OperationOutcome(
                new ApiSampleWithFullSubSamples("HeLa p3"), List.of(originAfter(100L))));
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
  }

  /**
   * The facade's BindException catch renames the core's field errors; a held lock is not one of
   * them and must reach the caller as the 409 it is, not as a 400 with no field to correct.
   */
  @Test
  void aHeldLockFromTheControllerPassesThroughTheFacadeUnchanged() {
    when(tracker.attemptToLockForEdit(eq("SS100"), any()))
        .thenReturn(lock("SS100", ApiInventoryEditLockStatus.CANNOT_LOCK));
    ApiInventoryOperationRequests.Aliquot request = aliquotFacade();

    InventoryEditLockHeldException held =
        assertThrows(
            InventoryEditLockHeldException.class,
            () -> controller.aliquot(request, bindingResultFor(request), user));

    assertEquals("SS100", held.getGlobalId());
    verifyNoInteractions(operationManager);
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

  // --- the rename must cover every path the core can report, or a caller is told about a field
  // it never sent ---

  @Test
  void facadeFieldRenamesTheExpectedQuantityPath() {
    assertEquals("origin.expectedQuantity", facadeField("origins[0].expectedQuantity", true));
    assertEquals("origins[1].expectedQuantity", facadeField("origins[1].expectedQuantity", false));
  }

  /**
   * OperationTemplateConformanceValidator runs the samples endpoint's validators over the BUILT
   * sample for every creating operation, templated or not, under the nested path {@code newSample}:
   * an over-long sampleName comes back as {@code newSample.name}, a storage temperature as {@code
   * newSample.storageTempMin}/{@code Max}, a generated field as {@code
   * newSample.extraFields[i].name} or {@code .content}. Only {@code templateId} and {@code
   * subSamples[i].quantity} were renamed, so every other path leaked a field the caller never sent.
   */
  @Test
  void facadeFieldNeverLeaksTheServerBuiltSamplesPaths() {
    assertEquals("sampleName", facadeField("newSample.name", true));
    assertEquals("storageTemp", facadeField("newSample.storageTempMin", true));
    assertEquals("storageTemp", facadeField("newSample.storageTempMax", false));
  }

  /**
   * Each generated field on the built sample traces to something the caller sent, recorded on the
   * field's {@code operationFieldKey}: the documentation link to {@code documentedByGlobalId}
   * (whose target the shared link validation checks for existence and readability), a text field to
   * the input its {@code contentFrom} names, a provenance link to the origin it targets. The rename
   * receives the built request as the binding result's target, so it can look these up.
   */
  @Test
  void aCoreRejectionOnABuiltFieldIsRenamedToWhatTheCallerSentForIt() {
    ApiInventoryOperationPost built = InventoryOperationPostValidatorTest.cryopreserveRequest();
    ApiExtraField documentation = new ApiExtraField(ApiExtraField.ExtraFieldTypeEnum.LINK);
    documentation.setName("Documented by");
    documentation.setNewFieldRequest(true);
    documentation.setOperationFieldKey("operations.documentationLink");
    ApiInventoryLink target = new ApiInventoryLink();
    target.setRelationType("IsDocumentedBy");
    target.setTargetGlobalId("SD99");
    documentation.setLink(target);
    built.getNewSample().getExtraFields().add(documentation);
    // [0] the provenance link to SS100, [1] the Cryomedium text field, [2] the documentation link
    BeanPropertyBindingResult core =
        new BeanPropertyBindingResult(built, "apiInventoryOperationPost");
    core.rejectValue(
        "newSample.extraFields[2].link.targetGlobalId",
        "errors.inventory.field.linkTargetNotFound",
        "No such record.");
    core.rejectValue(
        "newSample.extraFields[1].content", "errors.inventory.field.validation", "Too long.");
    core.rejectValue("newSample.extraFields[0].name", "errors.maxLength", "Too long.");

    List<String> renamed =
        InventoryOperationsApiController.facadeFieldNames(core, true).getFieldErrors().stream()
            .map(FieldError::getField)
            .toList();

    assertEquals(List.of("documentedByGlobalId", "cryomedium", "origin"), renamed);
  }

  @Test
  void aCoreRejectionOnTheBuiltSamplesNameIsRenamedToSampleName() throws Exception {
    ApiInventoryOperationPost generic = aliquotRequest();
    BeanPropertyBindingResult coreErrors =
        new BeanPropertyBindingResult(generic, "apiInventoryOperationPost");
    coreErrors.rejectValue("newSample.name", "errors.maxLength", new Object[] {"name", 255}, null);
    when(operationManager.performOperation(eq("aliquot"), any(), any(), any(), any(), eq(user)))
        .thenThrow(new BindException(coreErrors));
    ApiInventoryOperationRequests.Aliquot request = aliquotFacade();
    request.setSampleName("x".repeat(256));

    BindException rejection =
        assertThrows(
            BindException.class,
            () -> controller.aliquot(request, bindingResultFor(request), user));

    assertEquals(
        1, rejection.getFieldErrors("sampleName").size(), rejection.getAllErrors().toString());
    assertTrue(rejection.getFieldErrors("newSample.name").isEmpty());
  }

  // --- shape rules the facade must apply at the door ---

  @Test
  void typedFacadeRejectsAMalformedExpectedQuantityBeforeTheManager() throws Exception {
    when(operationManager.performOperation(eq("destroy"), any(), any(), any(), any(), eq(user)))
        .thenReturn(new OperationOutcome(null, List.of(originAfter(100L))));
    for (ApiQuantityInfo malformed :
        List.of(
            new ApiQuantityInfo(null, RSUnitDef.MILLI_LITRE),
            new ApiQuantityInfo(new BigDecimal("5"), 9999),
            new ApiQuantityInfo(new BigDecimal("5"), RSUnitDef.CELSIUS),
            millilitres("-1"))) {
      ApiInventoryOperationRequests.Destroy request = new ApiInventoryOperationRequests.Destroy();
      ApiInventoryOperationRequests.Origin origin = facadeOrigin("SS100", null);
      origin.setExpectedQuantity(malformed);
      request.setOrigin(origin);

      BindException rejection =
          assertThrows(
              BindException.class,
              () -> controller.destroy(request, bindingResultFor(request), user),
              () -> "expectedQuantity " + malformed + " must be a 400 at the door");

      assertEquals(
          1,
          rejection.getFieldErrors("origin.expectedQuantity").size(),
          () -> malformed + ": " + rejection.getAllErrors());
      assertTrue(
          rejection.getFieldErrors().stream().noneMatch(e -> e.getField().startsWith("origins")),
          () -> malformed + " leaked the plural path: " + rejection.getAllErrors());
    }
    verify(operationManager, never()).performOperation(any(), any(), any(), any(), any(), any());
  }

  @Test
  void poolRejectsANullOriginElementAtItsIndex() {
    ApiInventoryOperationRequests.Pool request = new ApiInventoryOperationRequests.Pool();
    request.setOrigins(Arrays.asList(facadeOrigin("SS100", millilitres("1")), null));
    request.setSampleName("Pooled");
    request.setEachAmount(millilitres("2"));

    BindException rejection =
        assertThrows(
            BindException.class, () -> controller.pool(request, bindingResultFor(request), user));

    assertEquals(
        "errors.inventory.operation.originIdRequired",
        rejection.getFieldErrors("origins[1]").get(0).getCode());
    verifyNoInteractions(operationManager);
  }

  @Test
  void poolWithoutAnOriginsListIsRejectedAsMissingOrigins() {
    // @Size does not fire on null, so an absent list reaches the core as an empty origin list and
    // must come back as the plural field the pool client knows.
    ApiInventoryOperationRequests.Pool request = new ApiInventoryOperationRequests.Pool();
    request.setSampleName("Pooled");
    request.setEachAmount(millilitres("2"));

    BindException rejection =
        assertThrows(
            BindException.class, () -> controller.pool(request, bindingResultFor(request), user));

    assertEquals(
        "errors.inventory.operation.originsRequired",
        rejection.getFieldErrors("origins").get(0).getCode());
    verifyNoInteractions(operationManager);
  }

  @Test
  void aZeroAmountOnADecrementingFacadeIsRejectedOnTheCallersField() {
    ApiInventoryOperationRequests.Aliquot request = aliquotFacade();
    request.getOrigin().setAmountTaken(millilitres("0"));

    BindException rejection =
        assertThrows(
            BindException.class,
            () -> controller.aliquot(request, bindingResultFor(request), user));

    assertEquals(
        "errors.inventory.operation.amountTakenPositive",
        rejection.getFieldErrors("origin.amountTaken").get(0).getCode());
    assertTrue(rejection.getFieldErrors().stream().noneMatch(e -> e.getField().contains("[")));
    verifyNoInteractions(operationManager);
  }

  @Test
  void typedFacadeRejectsEveryNearMissOfASubsampleGlobalId() {
    // Strict "SS" + digits: a lenient parse could resolve to a different subsample's id.
    for (String nearMiss :
        List.of(
            "ss100", " SS100", "SS100 ", "SS-1", "SS1.5", "SS", "", "SS 100", "S100", "SSS100")) {
      ApiInventoryOperationRequests.Aliquot request = aliquotFacade();
      request.getOrigin().setGlobalId(nearMiss);
      BindException rejection =
          assertThrows(
              BindException.class,
              () -> controller.aliquot(request, bindingResultFor(request), user),
              "[" + nearMiss + "]");
      assertEquals(
          "errors.inventory.operation.originGlobalIdInvalid",
          rejection.getFieldErrors("origin.globalId").get(0).getCode(),
          "[" + nearMiss + "]");
    }
    verifyNoInteractions(operationManager);
  }

  @Test
  void performIsRefusedWhileTheOperationsPropertyIsDenied() {
    operationsDenied();
    ApiInventoryOperationPost request = aliquotInputs();

    assertEquals(
        "errors.inventory.operations.notEnabled",
        assertThrows(
                UnsupportedOperationException.class,
                () ->
                    controller.performOperation(
                        request, new BeanPropertyBindingResult(request, "request"), user))
            .getMessage());
    verifyNoInteractions(operationManager);
  }

  @Test
  void everyOtherRouteIsRefusedTheSameWayWhileTheOperationsPropertyIsDenied() {
    operationsDenied();
    // The gate is the first statement of each route, ahead of body validation, so an empty body is
    // still answered with the configured-unavailable refusal rather than a 400 that would tell a
    // caller the feature is there.
    Map<String, Executable> routes = new LinkedHashMap<>();
    routes.put("config", () -> controller.getOperationsConfig(user));
    ApiInventoryOperationRequests.Aliquot aliquot = new ApiInventoryOperationRequests.Aliquot();
    routes.put("aliquot", () -> controller.aliquot(aliquot, bindingResultFor(aliquot), user));
    ApiInventoryOperationRequests.Passage passage = new ApiInventoryOperationRequests.Passage();
    routes.put("passage", () -> controller.passage(passage, bindingResultFor(passage), user));
    ApiInventoryOperationRequests.Pool pool = new ApiInventoryOperationRequests.Pool();
    routes.put("pool", () -> controller.pool(pool, bindingResultFor(pool), user));
    ApiInventoryOperationRequests.Derive derive = new ApiInventoryOperationRequests.Derive();
    routes.put("derive", () -> controller.derive(derive, bindingResultFor(derive), user));
    ApiInventoryOperationRequests.Cryopreserve cryo =
        new ApiInventoryOperationRequests.Cryopreserve();
    routes.put("cryopreserve", () -> controller.cryopreserve(cryo, bindingResultFor(cryo), user));
    ApiInventoryOperationRequests.Revive revive = new ApiInventoryOperationRequests.Revive();
    routes.put("revive", () -> controller.revive(revive, bindingResultFor(revive), user));
    ApiInventoryOperationRequests.Destroy destroy = new ApiInventoryOperationRequests.Destroy();
    routes.put("destroy", () -> controller.destroy(destroy, bindingResultFor(destroy), user));

    for (Map.Entry<String, Executable> route : routes.entrySet()) {
      assertEquals(
          "errors.inventory.operations.notEnabled",
          assertThrows(
                  UnsupportedOperationException.class, route.getValue(), "[" + route.getKey() + "]")
              .getMessage(),
          "[" + route.getKey() + "]");
    }
    verifyNoInteractions(operationManager);
    verifyNoInteractions(tracker);
  }
}
