package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
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
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.InventoryOperationManager.OperationOutcome;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.service.inventory.impl.InventoryEditLockTracker;
import com.researchspace.service.inventory.operations.AliquotOperation;
import com.researchspace.service.inventory.operations.CryopreserveOperation;
import com.researchspace.service.inventory.operations.DeriveOperation;
import com.researchspace.service.inventory.operations.DestroyOperation;
import com.researchspace.service.inventory.operations.PassageOperation;
import com.researchspace.service.inventory.operations.PoolOperation;
import com.researchspace.service.inventory.operations.ReviveOperation;
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
 * Unit coverage for what the controller owns: the shape rules it applies at the door, the
 * edit-session lock it holds around the manager, and the renaming of the core's field paths back to
 * the fields the caller sent. Real operations over a mocked manager, so only a request that passed
 * every door check reaches the transactional core.
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
    controller.inventoryOperationManager = operationManager;
    controller.systemPropertyManager = systemPropertyManager;
    controller.aliquotOperation = new AliquotOperation();
    controller.passageOperation = new PassageOperation();
    controller.poolOperation = new PoolOperation();
    controller.deriveOperation = new DeriveOperation();
    controller.cryopreserveOperation = new CryopreserveOperation();
    controller.reviveOperation = new ReviveOperation();
    controller.destroyOperation = new DestroyOperation();
    when(systemPropertyManager.isPropertyAllowed(
            user, SystemPropertyName.INVENTORY_OPERATIONS_AVAILABLE))
        .thenReturn(true);
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    when(messages.getMessage(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
    controller.setMessageSource(messages);
  }

  private static ApiQuantityInfo millilitres(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.MILLI_LITRE.getId());
  }

  private static ApiInventoryOperationRequests.Origin facadeOrigin(
      String globalId, ApiQuantityInfo amountTaken) {
    ApiInventoryOperationRequests.Origin origin = new ApiInventoryOperationRequests.Origin();
    origin.setGlobalId(globalId);
    origin.setAmountTaken(amountTaken);
    return origin;
  }

  private static ApiInventoryOperationRequests.Aliquot aliquotFacade() {
    ApiInventoryOperationRequests.Aliquot request = new ApiInventoryOperationRequests.Aliquot();
    request.setOrigin(facadeOrigin("SS100", millilitres("0.6")));
    request.setSampleName("Aliquots");
    request.setCount(BigDecimal.valueOf(2));
    request.setEachAmount(millilitres("0.5"));
    return request;
  }

  private static ApiInventoryOperationRequests.Pool poolFacade(String... originGlobalIds) {
    ApiInventoryOperationRequests.Pool request = new ApiInventoryOperationRequests.Pool();
    request.setOrigins(
        Arrays.stream(originGlobalIds).map(id -> facadeOrigin(id, millilitres("0.1"))).toList());
    request.setSampleName("Pooled");
    request.setEachAmount(millilitres("0.2"));
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

  private void managerCreates(ApiSampleWithFullSubSamples created, long... originIds)
      throws BindException {
    when(operationManager.perform(any(), any(), any(), eq(user)))
        .thenReturn(
            new OperationOutcome(
                created,
                Arrays.stream(originIds)
                    .mapToObj(InventoryOperationsApiControllerTest::originAfter)
                    .toList()));
  }

  @Test
  void yamlBodiesAreRejectedWith415BeforeAnyInventoryEffect() throws Exception {
    // The app registers a global YAML converter (WebConfig.YamlJackson2HttpMessageConverter), so
    // without an explicit JSON-only consumes clause these endpoints would bind YAML bodies too. The
    // same converter is registered here so this test fails if the consumes guard is ever dropped.
    MockMvc mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(
                new MappingJackson2HttpMessageConverter(),
                new WebConfig.YamlJackson2HttpMessageConverter())
            .build();
    for (String yamlType : List.of("application/x-yaml", "application/yaml", "text/yaml")) {
      mvc.perform(
              post("/api/inventory/v1/operations/aliquot")
                  .contentType(yamlType)
                  .content("sampleName: Aliquots"))
          .andExpect(status().isUnsupportedMediaType());
    }
    verifyNoInteractions(operationManager);
  }

  // --- the edit-session lock the controller holds around the manager ---

  /** Locks every origin plus every distinct parent sample, in ascending global-id order. */
  @Test
  void locksEveryOriginAndParentSampleInAscendingOrder() throws Exception {
    originExists(300L, 20L);
    lockIsFree("SS300");
    lockIsFree("SA20");
    managerCreates(new ApiSampleWithFullSubSamples("Pooled"));
    ApiInventoryOperationRequests.Pool request = poolFacade("SS300", "SS100");

    controller.pool(request, bindingResultFor(request), user);

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
    managerCreates(new ApiSampleWithFullSubSamples("Aliquots"));
    ApiInventoryOperationRequests.Aliquot request = aliquotFacade();

    controller.aliquot(request, bindingResultFor(request), user);

    verify(tracker).attemptToUnlock("SS100", user);
    verify(tracker, never()).attemptToUnlock("SA10", user);
  }

  @Test
  void aHeldLockReleasesWhatWasTakenAndNeverReachesTheManager() {
    when(tracker.attemptToLockForEdit(eq("SS100"), any()))
        .thenReturn(lock("SS100", ApiInventoryEditLockStatus.CANNOT_LOCK));
    ApiInventoryOperationRequests.Aliquot request = aliquotFacade();

    InventoryEditLockHeldException held =
        assertThrows(
            InventoryEditLockHeldException.class,
            () -> controller.aliquot(request, bindingResultFor(request), user));

    assertEquals("SS100", held.getGlobalId());
    assertEquals("Carol Holder", held.getOwnerDisplayName());
    verify(tracker).attemptToUnlock("SA10", user);
    verifyNoInteractions(operationManager);
  }

  /** A rejected operation must not leave the origins locked for the next five minutes. */
  @Test
  void releasesTheLocksWhenTheManagerRejectsTheRequest() throws Exception {
    when(operationManager.perform(any(), any(), any(), eq(user)))
        .thenThrow(new IllegalStateException("boom"));
    ApiInventoryOperationRequests.Aliquot request = aliquotFacade();

    assertThrows(
        IllegalStateException.class,
        () -> controller.aliquot(request, bindingResultFor(request), user));

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
    ApiInventoryOperationRequests.Aliquot request = aliquotFacade();

    assertThrows(
        org.apache.shiro.authz.AuthorizationException.class,
        () -> controller.aliquot(request, bindingResultFor(request), user));

    verifyNoInteractions(tracker);
    verifyNoInteractions(operationManager);
  }

  // --- what each endpoint answers ---

  @Test
  void creatingOperationsAnswer201WithALocationAndTheManagersResult() throws Exception {
    ApiSampleWithFullSubSamples created = new ApiSampleWithFullSubSamples("Aliquots");
    created.setId(55L);
    managerCreates(created, 100L);
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
  }

  @Test
  void handsTheManagerTheOperationAndTheOriginIdsItParsed() throws Exception {
    managerCreates(new ApiSampleWithFullSubSamples("Pooled"), 100L);
    originExists(300L, 20L);
    lockIsFree("SS300");
    lockIsFree("SA20");
    ApiInventoryOperationRequests.Pool request = poolFacade("SS100", "SS300");

    controller.pool(request, bindingResultFor(request), user);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<Long>> originIds = ArgumentCaptor.forClass(List.class);
    verify(operationManager)
        .perform(same(controller.poolOperation), same(request), originIds.capture(), eq(user));
    assertEquals(List.of(100L, 300L), originIds.getValue());
  }

  @Test
  void destroyAnswers200WithANullSampleAndTheOriginAsItStands() throws Exception {
    managerCreates(null, 100L);
    ApiInventoryOperationRequests.Destroy request = new ApiInventoryOperationRequests.Destroy();
    request.setOrigin(facadeOrigin("SS100", null));

    ResponseEntity<ApiInventoryOperationResult> response =
        controller.destroy(request, bindingResultFor(request), user);

    assertEquals(200, response.getStatusCode().value());
    assertNull(response.getHeaders().getFirst(HttpHeaders.LOCATION));
    assertNull(response.getBody().getSample());
    assertEquals(Long.valueOf(100L), response.getBody().getOrigins().get(0).getId());
  }

  @Test
  void passageSendsNoAmountAndStillReachesTheManager() throws Exception {
    managerCreates(new ApiSampleWithFullSubSamples("HeLa p3"), 100L);
    ApiInventoryOperationRequests.Passage request = new ApiInventoryOperationRequests.Passage();
    request.setOrigin(facadeOrigin("SS100", null));
    request.setSampleName("HeLa p3");
    request.setEachAmount(millilitres("5"));

    assertEquals(
        201, controller.passage(request, bindingResultFor(request), user).getStatusCode().value());
  }

  // --- shape rules the endpoint applies at the door ---

  @Test
  void rejectsAnOriginThatIsNotASubsampleGlobalId() {
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
  void rejectsEveryNearMissOfASubsampleGlobalId() {
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
  void aZeroAmountOnADecrementingOperationIsRejectedOnTheCallersField() {
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
  void anAmountSentToAnOperationThatTakesNoneIsRefused() {
    ApiInventoryOperationRequests.Destroy request = new ApiInventoryOperationRequests.Destroy();
    request.setOrigin(facadeOrigin("SS100", millilitres("1")));

    BindException rejection =
        assertThrows(
            BindException.class,
            () -> controller.destroy(request, bindingResultFor(request), user));

    assertEquals(
        "errors.inventory.operation.amountTakenNotApplicable",
        rejection.getFieldErrors("origin.amountTaken").get(0).getCode());
    verifyNoInteractions(operationManager);
  }

  @Test
  void anAmountSentAlongsidePoolsTakeAllIsRefusedAtThatOriginsIndex() {
    ApiInventoryOperationRequests.Pool request = poolFacade("SS100", "SS300");
    request.setTakeAll(true);
    request.getOrigins().get(0).setAmountTaken(null);

    BindException rejection =
        assertThrows(
            BindException.class, () -> controller.pool(request, bindingResultFor(request), user));

    assertEquals(
        "errors.inventory.operation.amountTakenNotWithTakeAll",
        rejection.getFieldErrors("origins[1].amountTaken").get(0).getCode());
    assertTrue(rejection.getFieldErrors("origins[0].amountTaken").isEmpty());
    verifyNoInteractions(operationManager);
  }

  @Test
  void anOriginMissingItsAmountOnADecrementingOperationIsRefused() {
    ApiInventoryOperationRequests.Aliquot request = aliquotFacade();
    request.getOrigin().setAmountTaken(null);

    BindException rejection =
        assertThrows(
            BindException.class,
            () -> controller.aliquot(request, bindingResultFor(request), user));

    assertEquals(
        "errors.inventory.operation.amountTakenInvalid",
        rejection.getFieldErrors("origin.amountTaken").get(0).getCode());
    verifyNoInteractions(operationManager);
  }

  @Test
  void poolKeepsThePluralPathAndNamesTheGlobalId() {
    ApiInventoryOperationRequests.Pool request = poolFacade("SS100", "SS100");

    BindException rejection =
        assertThrows(
            BindException.class, () -> controller.pool(request, bindingResultFor(request), user));

    assertEquals(
        "errors.inventory.operation.duplicateOrigin",
        rejection.getFieldErrors("origins[1].globalId").get(0).getCode());
    verifyNoInteractions(operationManager);
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
  void aDocumentationTargetOfTheWrongRecordKindIsRefused() {
    ApiInventoryOperationRequests.Aliquot request = aliquotFacade();
    request.setDocumentedByGlobalId("SS42");

    BindException rejection =
        assertThrows(
            BindException.class,
            () -> controller.aliquot(request, bindingResultFor(request), user));

    assertEquals(
        "errors.inventory.operation.documentationLinkTargetInvalid",
        rejection.getFieldErrors("documentedByGlobalId").get(0).getCode());
    verifyNoInteractions(operationManager);
  }

  // --- the rename must cover every path the core can report, or a caller is told about a field
  // it never sent ---

  private static String facadeField(String field, boolean singleOrigin) {
    return InventoryOperationsApiController.facadeField(field, singleOrigin);
  }

  @Test
  void facadeFieldRenamesEveryCorePathToTheFieldTheCallerSent() {
    assertEquals("origin.amountTaken", facadeField("origins[0].amountTaken", true));
    assertEquals("origin.globalId", facadeField("origins[0].id", true));
    assertEquals("origin", facadeField("origins", true));
    assertEquals("origins[1].globalId", facadeField("origins[1].id", false));
    assertEquals("origins[1].amountTaken", facadeField("origins[1].amountTaken", false));
    assertEquals("origins", facadeField("origins", false));
    assertEquals("templateId", facadeField("newSample.templateId", true));
    assertEquals("eachAmount", facadeField("newSample.subSamples[3].quantity", true));
    assertEquals("sampleName", facadeField("sampleName", true));
    assertEquals("documentedByGlobalId", facadeField("documentedByGlobalId", false));
  }

  /**
   * OperationTemplateConformanceValidator runs the samples endpoint's validators over the BUILT
   * sample for every creating operation, templated or not, under the nested path {@code newSample}:
   * an over-long sampleName comes back as {@code newSample.name}, a storage temperature as {@code
   * newSample.storageTempMin}/{@code Max}.
   */
  @Test
  void facadeFieldNeverLeaksTheServerBuiltSamplesPaths() {
    assertEquals("sampleName", facadeField("newSample.name", true));
    assertEquals("storageTemp", facadeField("newSample.storageTempMin", true));
    assertEquals("storageTemp", facadeField("newSample.storageTempMax", false));
  }

  @Test
  void aCoreRejectionIsRenamedToTheFieldTheCallerSent() throws Exception {
    ApiInventoryOperationPost built = new ApiInventoryOperationPost();
    BeanPropertyBindingResult coreErrors = new BeanPropertyBindingResult(built, "request");
    coreErrors.rejectValue(
        "origins[0].amountTaken",
        "errors.inventory.operation.amountTakenExceedsOrigin",
        "Cannot take more from an origin than it currently holds.");
    when(operationManager.perform(any(), any(), any(), eq(user)))
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
   * The BindException catch renames the core's field errors; a held lock is not one of them and
   * must reach the caller as the 409 it is, not as a 400 with no field to correct.
   */
  @Test
  void aHeldLockPassesThroughTheRenameUnchanged() {
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
  void everyRouteIsRefusedTheSameWayWhileTheOperationsPropertyIsDenied() {
    operationsDenied();
    Map<String, Executable> routes = new LinkedHashMap<>();
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
