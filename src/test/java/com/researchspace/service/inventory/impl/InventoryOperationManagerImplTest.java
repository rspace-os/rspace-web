package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.InventoryOperationManager;
import com.researchspace.service.inventory.operations.AliquotOperation;
import com.researchspace.service.inventory.operations.DestroyOperation;
import com.researchspace.service.inventory.operations.PassageOperation;
import com.researchspace.service.inventory.operations.PoolOperation;
import com.researchspace.service.inventory.operations.ReviveOperation;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

@ExtendWith(MockitoExtension.class)
class InventoryOperationManagerImplTest {

  @Mock private com.researchspace.service.inventory.SampleApiManager sampleApiMgr;
  @Mock private com.researchspace.service.inventory.SubSampleApiManager subSampleApiMgr;
  @Mock private com.researchspace.service.inventory.LinkTargetResolver linkTargetResolver;

  @Mock
  private com.researchspace.service.inventory.OperationTemplateConformanceValidator
      templateConformance;

  private InventoryOperationManagerImpl manager;
  private final User user = new User("anyUser");
  private long nextParentSampleId = 900L;

  private static ApiInventoryOperationOriginUpdate origin(Long id, ApiQuantityInfo amountTaken) {
    ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
    origin.setId(id);
    origin.setGlobalId("SS" + id);
    origin.setAmountTaken(amountTaken);
    return origin;
  }

  private SubSample subSampleHolding(String value, int unitId) {
    return subSampleHolding(value, unitId, nextParentSampleId++);
  }

  private SubSample subSampleHolding(String value, int unitId, long sampleId) {
    SubSample subSample = mock(SubSample.class);
    when(subSample.getQuantity())
        .thenReturn(value == null ? null : new QuantityInfo(new BigDecimal(value), unitId));
    SampleEntity parent = mock(SampleEntity.class);
    lenient().when(parent.getId()).thenReturn(sampleId);
    lenient().when(subSample.getSample()).thenReturn(parent);
    return subSample;
  }

  private void originHolds(long originId, SubSample subSample) {
    when(subSampleApiMgr.assertUserCanEditSubSample(originId, user)).thenReturn(subSample);
    lenient().when(subSampleApiMgr.getIfExists(originId)).thenReturn(subSample);
    ApiSubSample mapped = new ApiSubSample();
    mapped.setId(originId);
    lenient().when(subSampleApiMgr.getApiSubSampleById(originId, user)).thenReturn(mapped);
  }

  @BeforeEach
  void setUp() {
    manager = new InventoryOperationManagerImpl();
    ReflectionTestUtils.setField(manager, "sampleApiMgr", sampleApiMgr);
    ReflectionTestUtils.setField(manager, "subSampleApiMgr", subSampleApiMgr);
    ReflectionTestUtils.setField(manager, "linkTargetResolver", linkTargetResolver);
    ReflectionTestUtils.setField(manager, "templateConformance", templateConformance);
    org.springframework.context.MessageSource messages =
        mock(org.springframework.context.MessageSource.class);
    lenient()
        .when(messages.getMessage(any(String.class), any(), any(String.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(2));
    ReflectionTestUtils.setField(manager, "messageSource", messages);
  }

  /** A creating request whose new sample is already stubbed to be created. */
  private ApiInventoryOperationPost creatingRequest(
      String name, ApiInventoryOperationOriginUpdate... origins) {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origins));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples(name);
    request.setNewSample(newSample);
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples(name));
    return request;
  }

  @Test
  void performOperationCreatesNewSampleAndReducesOriginByAmountTaken() throws Exception {
    ApiQuantityInfo amountTaken = new ApiQuantityInfo(new BigDecimal("0.6"), 3);
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, amountTaken)));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));

    ApiSampleWithFullSubSamples created = new ApiSampleWithFullSubSamples("Derived material");
    when(sampleApiMgr.createNewApiSample(newSample, user)).thenReturn(created);

    ApiSampleWithFullSubSamples result = manager.execute(request, user);

    assertSame(created, result);
    verify(subSampleApiMgr).assertUserCanEditSubSample(100L, user);
    verify(sampleApiMgr).createNewApiSample(newSample, user);
    ArgumentCaptor<QuantityInfo> used = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), used.capture(), eq(user));
    assertEquals(0, new BigDecimal("0.6").compareTo(used.getValue().getNumericValue()));
    assertEquals(Integer.valueOf(3), used.getValue().getUnitId());
  }

  @Test
  void decrementsOriginBeforeCreatingTheNewSample() throws Exception {
    // The new subsample must end up most-recently-modified, so the origin is decremented (which
    // stamps its modification date) BEFORE the new sample + subsample are created.
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material", origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3)));
    originHolds(100L, subSampleHolding("5", 3));

    manager.execute(request, user);

    InOrder inOrder = inOrder(subSampleApiMgr, sampleApiMgr);
    inOrder.verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    inOrder.verify(sampleApiMgr).createNewApiSample(request.getNewSample(), user);
  }

  @Test
  void abortsBeforeAnyMutationWhenAnOriginIsNotEditable() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    doThrow(new RuntimeException("no permission"))
        .when(subSampleApiMgr)
        .assertUserCanEditSubSample(100L, user);

    assertThrows(RuntimeException.class, () -> manager.execute(request, user));

    verify(sampleApiMgr, never()).createNewApiSample(any(), any());
    verify(subSampleApiMgr, never()).registerApiSubSampleUsage(any(), any(), any());
  }

  @Test
  void abortsBeforeAnyMutationWhenALaterOriginIsNotEditable() {
    // Origin 100's permission check must succeed, but nothing past it ever runs, so it needs no
    // quantity stub (the permission loop rejects origin 200 before the quantity loop starts).
    when(subSampleApiMgr.assertUserCanEditSubSample(100L, user)).thenReturn(mock(SubSample.class));
    doThrow(new RuntimeException("no permission"))
        .when(subSampleApiMgr)
        .assertUserCanEditSubSample(200L, user);
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3)),
            origin(200L, new ApiQuantityInfo(new BigDecimal("1.5"), 3))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));

    assertThrows(RuntimeException.class, () -> manager.execute(request, user));

    verify(subSampleApiMgr, never()).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    verify(sampleApiMgr, never()).createNewApiSample(any(), any());
  }

  @Test
  void terminalOperationAddsOriginFieldsAndCreatesNoSample() throws Exception {
    ApiExtraField disposed = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    disposed.setName("disposed");
    disposed.setContent("2026-07-20");
    disposed.setNewFieldRequest(true);
    ApiInventoryOperationOriginUpdate origin =
        origin(100L, new ApiQuantityInfo(new BigDecimal("2"), 3));
    origin.setExtraFields(List.of(disposed));
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setEmptiesOrigin(true);
    request.setOrigins(List.of(origin));
    request.setNewSample(null);
    originHolds(100L, subSampleHolding("2", 3));

    ApiSampleWithFullSubSamples result = manager.execute(request, user);

    assertNull(result);
    verify(sampleApiMgr, never()).createNewApiSample(any(), any());
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    ArgumentCaptor<ApiSubSample> update = ArgumentCaptor.forClass(ApiSubSample.class);
    verify(subSampleApiMgr).updateApiSubSample(update.capture(), eq(user));
    assertEquals(Long.valueOf(100L), update.getValue().getId());
    assertEquals("disposed", update.getValue().getExtraFields().get(0).getName());
    // The sparse update DTO must carry null tags: a non-null empty list means "clear all tags" in
    // applyChangesToDatabaseInventoryRecord, which would silently wipe a tagged origin's tags.
    assertNull(update.getValue().getTags());
  }

  @Test
  void decrementsOriginsInAscendingIdOrderRegardlessOfRequestOrder() throws Exception {
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material",
            origin(200L, new ApiQuantityInfo(new BigDecimal("1.5"), 3)),
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3)));
    originHolds(100L, subSampleHolding("5", 3));
    originHolds(200L, subSampleHolding("5", 3));

    manager.execute(request, user);

    InOrder inOrder = inOrder(subSampleApiMgr);
    inOrder.verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    inOrder.verify(subSampleApiMgr).registerApiSubSampleUsage(eq(200L), any(), eq(user));
  }

  @Test
  void reducesEveryOriginByItsOwnAmountTaken() throws Exception {
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material",
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3)),
            origin(200L, new ApiQuantityInfo(new BigDecimal("1.5"), 3)));
    originHolds(100L, subSampleHolding("5", 3));
    originHolds(200L, subSampleHolding("5", 3));

    manager.execute(request, user);

    verify(subSampleApiMgr).assertUserCanEditSubSample(100L, user);
    verify(subSampleApiMgr).assertUserCanEditSubSample(200L, user);
    ArgumentCaptor<QuantityInfo> first = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), first.capture(), eq(user));
    assertEquals(0, new BigDecimal("0.6").compareTo(first.getValue().getNumericValue()));
    ArgumentCaptor<QuantityInfo> second = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(200L), second.capture(), eq(user));
    assertEquals(0, new BigDecimal("1.5").compareTo(second.getValue().getNumericValue()));
  }

  private BindException performExpectingRejection(ApiInventoryOperationPost request) {
    BindException rejection =
        assertThrows(BindException.class, () -> manager.execute(request, user));
    verifyNoMutation();
    return rejection;
  }

  private void verifyNoMutation() {
    verify(subSampleApiMgr, never()).registerApiSubSampleUsage(any(), any(), any());
    verify(subSampleApiMgr, never()).updateApiSubSample(any(), any());
    verify(sampleApiMgr, never()).createNewApiSample(any(), any());
  }

  @Test
  void rejectsOperatingOnAnOriginThatCurrentlyHoldsNothing() {
    for (SubSample empty : List.of(subSampleHolding("0", 3), subSampleHolding(null, 3))) {
      ApiInventoryOperationPost request = new ApiInventoryOperationPost();
      request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3))));
      request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
      originHolds(100L, empty);

      BindException rejection = performExpectingRejection(request);
      FieldError error = rejection.getFieldErrors("origins[0].globalId").get(0);
      assertEquals("errors.inventory.operation.originEmpty", error.getCode());
      assertEquals("SS100", error.getRejectedValue());
    }
  }

  @Test
  void rejectsTakingMoreThanTheOriginCurrentlyHolds() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("6"), 3))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("5", 3));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.amountTakenExceedsOrigin",
        rejection.getFieldErrors("origins[0].amountTaken").get(0).getCode());
  }

  @Test
  void acceptsTakeAllWhoseAmountStillMatchesTheOrigin() throws Exception {
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material", origin(100L, new ApiQuantityInfo(new BigDecimal("5"), 3)));
    originHolds(100L, subSampleHolding("5", 3));

    assertDoesNotThrow(() -> manager.execute(request, user));

    ArgumentCaptor<QuantityInfo> used = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), used.capture(), eq(user));
    assertEquals(0, new BigDecimal("5").compareTo(used.getValue().getNumericValue()));
  }

  @Test
  void acceptsTakeAllSubmittedInADifferentUnitOfTheSameCategory() throws Exception {
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material",
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.01"), RSUnitDef.LITRE.getId())));
    originHolds(100L, subSampleHolding("10", RSUnitDef.MILLI_LITRE.getId()));

    assertDoesNotThrow(() -> manager.execute(request, user));
  }

  @Test
  void acceptsAPartialAmountFromAnOperationThatDoesNotEmptyItsOrigin() throws Exception {
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material", origin(100L, new ApiQuantityInfo(new BigDecimal("3"), 3)));
    originHolds(100L, subSampleHolding("5", 3));

    assertDoesNotThrow(() -> manager.execute(request, user));
  }

  @Test
  void acceptsAnEmptyingOperationWhoseAmountIsTheWholeOrigin() throws Exception {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setEmptiesOrigin(true);
    ApiInventoryOperationOriginUpdate origin =
        origin(100L, new ApiQuantityInfo(new BigDecimal("5"), 3));
    request.setOrigins(List.of(origin));
    originHolds(100L, subSampleHolding("5", 3));

    assertDoesNotThrow(() -> manager.execute(request, user));
  }

  @Test
  void rejectsAnEmptyingOperationAskingForOnlyPartOfTheOrigin() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setEmptiesOrigin(true);
    ApiInventoryOperationOriginUpdate origin =
        origin(100L, new ApiQuantityInfo(new BigDecimal("3"), 3));
    request.setOrigins(List.of(origin));
    originHolds(100L, subSampleHolding("5", 3));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.mustEmptyOrigin",
        rejection.getFieldErrors("origins[0].amountTaken").get(0).getCode());
  }

  @Test
  void rejectsPoolingOriginsFromDifferentMeasurementCategories() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(
            origin(100L, new ApiQuantityInfo(new BigDecimal("1"), RSUnitDef.MILLI_LITRE.getId())),
            origin(200L, new ApiQuantityInfo(new BigDecimal("1"), RSUnitDef.GRAM.getId()))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Pooled material"));
    originHolds(100L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId()));
    originHolds(200L, subSampleHolding("5", RSUnitDef.GRAM.getId()));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.originCategoryMismatch",
        rejection.getFieldErrors("origins[1].globalId").get(0).getCode());
  }

  @Test
  void allowsPoolingOriginsAcrossUnitsOfTheSameCategory() throws Exception {
    ApiInventoryOperationPost request =
        creatingRequest(
            "Pooled material",
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), RSUnitDef.MILLI_LITRE.getId())),
            origin(200L, new ApiQuantityInfo(new BigDecimal("0.001"), RSUnitDef.LITRE.getId())));
    originHolds(100L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId()));
    originHolds(200L, subSampleHolding("2", RSUnitDef.LITRE.getId()));

    manager.execute(request, user);

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(200L), any(), eq(user));
  }

  @Test
  void rejectionOnALaterOriginMutatesNothing() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3)),
            origin(200L, new ApiQuantityInfo(new BigDecimal("9"), 3))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Pooled material"));
    originHolds(100L, subSampleHolding("5", 3));
    originHolds(200L, subSampleHolding("5", 3));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.amountTakenExceedsOrigin",
        rejection.getFieldErrors("origins[1].amountTaken").get(0).getCode());
  }

  // --- the live-state helper predicates (unit-aware quantity comparisons) ---

  private static ApiQuantityInfo grams(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.GRAM.getId());
  }

  private static ApiQuantityInfo millilitres(String value) {
    return new ApiQuantityInfo(new BigDecimal(value), RSUnitDef.MILLI_LITRE.getId());
  }

  @Test
  void detectsOverRemovalInTheSameUnit() {
    assertTrue(InventoryOperationManagerImpl.amountTakenExceedsOrigin(grams("6"), grams("5")));
    assertFalse(InventoryOperationManagerImpl.amountTakenExceedsOrigin(grams("5"), grams("5")));
    assertFalse(InventoryOperationManagerImpl.amountTakenExceedsOrigin(grams("4"), grams("5")));
  }

  @Test
  void comparesUnitAwareAcrossUnitsInTheSameCategory() {
    ApiQuantityInfo sixGramsAsKilos =
        new ApiQuantityInfo(new BigDecimal("0.006"), RSUnitDef.KILO.getId());
    assertTrue(InventoryOperationManagerImpl.amountTakenExceedsOrigin(sixGramsAsKilos, grams("5")));
    ApiQuantityInfo fourGramsAsKilos =
        new ApiQuantityInfo(new BigDecimal("0.004"), RSUnitDef.KILO.getId());
    assertFalse(
        InventoryOperationManagerImpl.amountTakenExceedsOrigin(fourGramsAsKilos, grams("5")));
  }

  @Test
  void doesNotFlagNullAmountTakenOrDifferentCategories() {
    assertFalse(InventoryOperationManagerImpl.amountTakenExceedsOrigin(null, grams("5")));
    assertFalse(
        InventoryOperationManagerImpl.amountTakenExceedsOrigin(millilitres("6"), grams("5")));
    assertFalse(InventoryOperationManagerImpl.amountTakenExceedsOrigin(grams("6"), null));
    assertFalse(
        InventoryOperationManagerImpl.amountTakenExceedsOrigin(
            grams("6"), new ApiQuantityInfo(null, RSUnitDef.GRAM.getId())));
  }

  @Test
  void originHoldsNothingTreatsMissingOrNonPositiveQuantityAsEmpty() {
    assertTrue(InventoryOperationManagerImpl.originHoldsNothing(null));
    assertTrue(
        InventoryOperationManagerImpl.originHoldsNothing(
            new ApiQuantityInfo(null, RSUnitDef.GRAM.getId())));
    assertTrue(InventoryOperationManagerImpl.originHoldsNothing(grams("0")));
    assertTrue(InventoryOperationManagerImpl.originHoldsNothing(grams("-1")));
    assertFalse(InventoryOperationManagerImpl.originHoldsNothing(grams("0.001")));
  }

  @Test
  void amountTakenEmptiesOriginIsUnitAwareEquality() {
    assertTrue(InventoryOperationManagerImpl.amountTakenEmptiesOrigin(grams("5"), grams("5")));
    assertTrue(
        InventoryOperationManagerImpl.amountTakenEmptiesOrigin(
            new ApiQuantityInfo(new BigDecimal("0.005"), RSUnitDef.KILO.getId()), grams("5")));
    assertFalse(InventoryOperationManagerImpl.amountTakenEmptiesOrigin(grams("4"), grams("5")));
    assertFalse(InventoryOperationManagerImpl.amountTakenEmptiesOrigin(grams("6"), grams("5")));
    assertFalse(
        InventoryOperationManagerImpl.amountTakenEmptiesOrigin(millilitres("5"), grams("5")));
    assertFalse(InventoryOperationManagerImpl.amountTakenEmptiesOrigin(null, grams("5")));
    assertFalse(InventoryOperationManagerImpl.amountTakenEmptiesOrigin(grams("5"), null));
  }

  // --- measurement categories against the origin's live quantity ---

  private static ApiSubSample subSampleOf(ApiQuantityInfo quantity) {
    ApiSubSample subSample = new ApiSubSample();
    subSample.setQuantity(quantity);
    return subSample;
  }

  @Test
  void rejectsAmountTakenFromADifferentMeasurementCategoryThanTheOrigin() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, grams("1"))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId()));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.amountTakenCategoryMismatch",
        rejection.getFieldErrors("origins[0].amountTaken").get(0).getCode());
  }

  @Test
  void rejectsNewSubSamplesInADifferentMeasurementCategoryThanTheOrigin() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, millilitres("1"))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    newSample.getSubSamples().add(subSampleOf(grams("0.5")));
    newSample.getSubSamples().add(subSampleOf(millilitres("0.5")));
    newSample.getSubSamples().add(subSampleOf(grams("0.5")));
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId()));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.subSampleCategoryMismatch",
        rejection.getFieldErrors("newSample.subSamples[0].quantity").get(0).getCode());
    assertTrue(rejection.getFieldErrors("newSample.subSamples[1].quantity").isEmpty());
    assertEquals(
        "errors.inventory.operation.subSampleCategoryMismatch",
        rejection.getFieldErrors("newSample.subSamples[2].quantity").get(0).getCode());
  }

  @Test
  void leavesNewSubSampleCategoriesToTheTemplateCheckWhenATemplateIsChosen() throws Exception {
    ApiInventoryOperationPost request = creatingRequest("DNA extract", origin(100L, grams("1")));
    ApiSampleWithFullSubSamples newSample = request.getNewSample();
    newSample.setTemplateId(7L);
    newSample.getSubSamples().add(subSampleOf(millilitres("0.5")));
    originHolds(100L, subSampleHolding("5", RSUnitDef.GRAM.getId()));

    manager.execute(request, user);

    verify(sampleApiMgr).createNewApiSample(newSample, user);
  }

  @Test
  void readsOriginsInAscendingIdOrderAndReportsErrorsAtTheirRequestIndex() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(300L, millilitres("1")), origin(100L, millilitres("9"))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Pooled material"));
    originHolds(300L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId()));
    originHolds(100L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId()));

    BindException rejection = performExpectingRejection(request);

    InOrder inOrder = inOrder(subSampleApiMgr);
    inOrder.verify(subSampleApiMgr).assertUserCanEditSubSample(100L, user);
    inOrder.verify(subSampleApiMgr).assertUserCanEditSubSample(300L, user);
    assertEquals(
        "errors.inventory.operation.amountTakenExceedsOrigin",
        rejection.getFieldErrors("origins[1].amountTaken").get(0).getCode());
  }

  @Test
  void aWithdrawalFarBelowTheOriginsOwnResolutionIsAccepted() throws Exception {
    // 0.001 ul from a 1 l origin leaves 999999.999 ul, which DECIMAL(19,3) holds exactly, even
    // though 0.999999999 l rounds back to 1 l; storing the remainder in the unit that holds it
    // exactly is what avoids losing it.
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material",
            origin(
                100L, new ApiQuantityInfo(new BigDecimal("0.001"), RSUnitDef.MICRO_LITRE.getId())));
    originHolds(100L, subSampleHolding("1", RSUnitDef.LITRE.getId()));

    assertDoesNotThrow(() -> manager.execute(request, user));

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void aWithdrawalWhoseRemainderNeedsASmallerUnitThanEitherOperandIsAccepted() throws Exception {
    // 1500.4 ml less 0.5 l: QuantitySummingVisitor works in the LARGEST unit of the operands, so
    // the remainder arises as 1.0004 l, which does not fit 3dp. It is 1000.4 ml, which does, so the
    // subtraction steps down to the unit that holds it and no stock is lost.
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material",
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.5"), RSUnitDef.LITRE.getId())));
    originHolds(100L, subSampleHolding("1500.4", RSUnitDef.MILLI_LITRE.getId()));

    assertDoesNotThrow(() -> manager.execute(request, user));

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void aWithdrawalThatRedenominatesTheOriginIsAccepted() throws Exception {
    // 0.001 ul taken from a 1 ml origin redenominates it to 999.999 ul; the result is EXACT (no
    // stock lost), so it must not be rejected merely because the unit changed.
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material",
            origin(
                100L, new ApiQuantityInfo(new BigDecimal("0.001"), RSUnitDef.MICRO_LITRE.getId())));
    originHolds(100L, subSampleHolding("1", RSUnitDef.MILLI_LITRE.getId()));

    assertDoesNotThrow(() -> manager.execute(request, user));

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void aSubUnitWithdrawalIsAccepted() throws Exception {
    // 2.5 mg from a 5 g origin leaves 4.9975 g, which needs four decimal places and is
    // unrepresentable only IN GRAMS: as 4997.5 mg the same DECIMAL(19,3) column holds it exactly,
    // so the remainder must be stored in the finer unit rather than rejected.
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material",
            origin(100L, new ApiQuantityInfo(new BigDecimal("2.5"), RSUnitDef.MILLI_GRAM.getId())));
    originHolds(100L, subSampleHolding("5", RSUnitDef.GRAM.getId()));

    assertDoesNotThrow(() -> manager.execute(request, user));

    verify(subSampleApiMgr)
        .registerApiSubSampleUsage(
            eq(100L),
            argThat(
                taken ->
                    taken.getUnitId().equals(RSUnitDef.MILLI_GRAM.getId())
                        && new BigDecimal("2.5").compareTo(taken.getNumericValue()) == 0),
            eq(user));
  }

  @Test
  void acceptsACrossUnitAmountTakenWhoseSubtractionIsExact() throws Exception {
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material",
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.5"), RSUnitDef.LITRE.getId())));
    originHolds(100L, subSampleHolding("1500", RSUnitDef.MILLI_LITRE.getId()));

    assertDoesNotThrow(() -> manager.execute(request, user));
  }

  // --- template conformance ---

  @Test
  void runsTheTemplateConformanceCheckBeforeAnyOriginRead() throws Exception {
    ApiInventoryOperationPost request =
        creatingRequest(
            "Derived material", origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3)));
    originHolds(100L, subSampleHolding("5", 3));

    manager.execute(request, user);

    InOrder inOrder = inOrder(templateConformance, subSampleApiMgr);
    inOrder.verify(templateConformance).validate(request, user);
    inOrder.verify(subSampleApiMgr).assertUserCanEditSubSample(100L, user);
  }

  // --- the server-built path, as the typed facades drive it ---

  private static final int ML = RSUnitDef.MILLI_LITRE.getId();

  private void serverBuiltOriginHolds(long originId, String value) {
    SubSample subSample = subSampleHolding(value, ML);
    // Every mock read happens BEFORE its when(): a mock call inside thenReturn is unfinished
    // stubbing.
    QuantityInfo quantity = subSample.getQuantity();
    SampleEntity parent = subSample.getSample();
    when(subSample.getQuantityInfo()).thenReturn(quantity);
    when(subSample.getGlobalIdentifier()).thenReturn("SS" + originId);
    when(subSample.getName()).thenReturn("Vial");
    when(parent.getActiveFields()).thenReturn(List.of());
    when(parent.getActiveExtraFields()).thenReturn(List.of());
    originHolds(originId, subSample);
  }

  // --- zero amounts: what "take nothing" means to each kind of operation ---

  @Test
  void aPassageWithAnExplicitZeroAmountDecrementsNothingAndStillCreatesTheSample()
      throws Exception {
    ApiInventoryOperationPost request = creatingRequest("HeLa p3", origin(100L, millilitres("0")));
    originHolds(100L, subSampleHolding("5", ML));

    manager.execute(request, user);

    ArgumentCaptor<QuantityInfo> used = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), used.capture(), eq(user));
    assertEquals(0, used.getValue().getNumericValue().signum(), "nothing is taken");
    assertEquals(ML, used.getValue().getUnitId());
    verify(sampleApiMgr).createNewApiSample(request.getNewSample(), user);
  }

  @Test
  void aPassageOnAnOriginThatHoldsNothingIsStillRejectedAsEmpty() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, millilitres("0"))));
    request.setNewSample(new ApiSampleWithFullSubSamples("HeLa p3"));
    originHolds(100L, subSampleHolding("0", ML));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.originEmpty",
        rejection.getFieldErrors("origins[0].globalId").get(0).getCode());
  }

  @Test
  void aZeroAmountOnAnEmptyingOperationWithoutAModeIsAMalformedRequest() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setEmptiesOrigin(true);
    request.setOrigins(List.of(origin(100L, millilitres("0"))));
    originHolds(100L, subSampleHolding("5", ML));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.mustEmptyOrigin",
        rejection.getFieldErrors("origins[0].amountTaken").get(0).getCode());
  }

  // --- the operation-driven path, as each endpoint drives it ---

  private static ApiInventoryOperationRequests.Origin facadeOrigin(
      long id, ApiQuantityInfo amount) {
    ApiInventoryOperationRequests.Origin origin = new ApiInventoryOperationRequests.Origin();
    origin.setGlobalId("SS" + id);
    origin.setAmountTaken(amount);
    return origin;
  }

  private static <R extends ApiInventoryOperationRequests.Creating> R creating(
      R request, ApiInventoryOperationRequests.Origin origin, String name, Integer count) {
    request.setSampleName(name);
    request.setEachAmount(millilitres("0.5"));
    if (count != null) {
      request.setCount(BigDecimal.valueOf(count));
    }
    if (request instanceof ApiInventoryOperationRequests.SingleOriginCreating single) {
      single.setOrigin(origin);
    }
    return request;
  }

  private ApiSampleWithFullSubSamples createdSample() {
    ArgumentCaptor<ApiSampleWithFullSubSamples> built =
        ArgumentCaptor.forClass(ApiSampleWithFullSubSamples.class);
    verify(sampleApiMgr).createNewApiSample(built.capture(), eq(user));
    return built.getValue();
  }

  @Test
  void anOperationThatTakesNothingStillDecrementsTheOriginByZeroInItsOwnUnit() throws Exception {
    serverBuiltOriginHolds(100L, "5");
    when(sampleApiMgr.createNewApiSample(any(), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("HeLa p3"));

    manager.performOperation(
        new PassageOperation(),
        creating(
            new ApiInventoryOperationRequests.Passage(), facadeOrigin(100L, null), "HeLa p3", 1),
        List.of(100L),
        user);

    ArgumentCaptor<QuantityInfo> taken = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), taken.capture(), eq(user));
    assertEquals(0, taken.getValue().getNumericValue().signum());
    assertEquals(ML, taken.getValue().getUnitId());
  }

  @Test
  void anOriginWhoseParentSampleIsAbsentStillPerformsWithNoParentFields() throws Exception {
    // SubSample.getSample() is nullable enough that the entity null-guards it in getOwner(),
    // getSharingACL() and getParentId(), and the controller guards it when collecting lock
    // targets. Reading the parent's fields unguarded would 500 before anything was validated.
    SubSample subSample = mock(SubSample.class);
    QuantityInfo quantity = new QuantityInfo(new BigDecimal("5"), ML);
    when(subSample.getQuantity()).thenReturn(quantity);
    when(subSample.getQuantityInfo()).thenReturn(quantity);
    when(subSample.getGlobalIdentifier()).thenReturn("SS100");
    when(subSample.getName()).thenReturn("Orphan vial");
    when(subSample.getSample()).thenReturn(null);
    originHolds(100L, subSample);
    when(sampleApiMgr.createNewApiSample(any(), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Aliquots"));

    manager.performOperation(
        new AliquotOperation(),
        creating(
            new ApiInventoryOperationRequests.Aliquot(),
            facadeOrigin(100L, millilitres("1")),
            "Aliquots",
            1),
        List.of(100L),
        user);

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void anOriginWithoutAnAmountIsEmptiedByADestroy() throws Exception {
    serverBuiltOriginHolds(100L, "5");
    ApiInventoryOperationRequests.Destroy request = new ApiInventoryOperationRequests.Destroy();
    request.setOrigin(facadeOrigin(100L, null));

    assertNull(
        manager.performOperation(new DestroyOperation(), request, List.of(100L), user).sample(),
        "a terminal operation creates no sample, but still reports its origins");

    ArgumentCaptor<QuantityInfo> taken = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), taken.capture(), eq(user));
    assertEquals(0, new BigDecimal("5").compareTo(taken.getValue().getNumericValue()));
    verify(subSampleApiMgr).updateApiSubSample(any(), eq(user));
  }

  @Test
  void anAbsentCountDefaultsToOneSubsample() throws Exception {
    serverBuiltOriginHolds(100L, "5");
    when(sampleApiMgr.createNewApiSample(any(), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Aliquots"));

    manager.performOperation(
        new AliquotOperation(),
        creating(
            new ApiInventoryOperationRequests.Aliquot(),
            facadeOrigin(100L, millilitres("1")),
            "Aliquots",
            null),
        List.of(100L),
        user);

    assertEquals(1, createdSample().getSubSamples().size());
  }

  @Test
  void anAbsentReviveStorageTempDefaultsToFourCelsius() throws Exception {
    serverBuiltOriginHolds(100L, "5");
    when(sampleApiMgr.createNewApiSample(any(), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Revived"));

    manager.performOperation(
        new ReviveOperation(),
        creating(
            new ApiInventoryOperationRequests.Revive(),
            facadeOrigin(100L, millilitres("1")),
            "Revived",
            1),
        List.of(100L),
        user);

    ApiSampleWithFullSubSamples created = createdSample();
    assertEquals(
        new ApiQuantityInfo(new BigDecimal("4"), RSUnitDef.CELSIUS), created.getStorageTempMin());
    assertEquals(created.getStorageTempMin(), created.getStorageTempMax());
  }

  @Test
  void theOriginsAfterComeBackInRequestOrderRegardlessOfProcessingOrder() throws Exception {
    serverBuiltOriginHolds(200L, "5");
    serverBuiltOriginHolds(100L, "5");
    ApiSubSample after200 = new ApiSubSample();
    after200.setId(200L);
    ApiSubSample after100 = new ApiSubSample();
    after100.setId(100L);
    when(subSampleApiMgr.getApiSubSampleById(200L, user)).thenReturn(after200);
    when(subSampleApiMgr.getApiSubSampleById(100L, user)).thenReturn(after100);
    when(sampleApiMgr.createNewApiSample(any(), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Pooled"));
    ApiInventoryOperationRequests.Pool request =
        creating(new ApiInventoryOperationRequests.Pool(), null, "Pooled", 1);
    request.setOrigins(
        List.of(facadeOrigin(200L, millilitres("1")), facadeOrigin(100L, millilitres("1"))));

    InventoryOperationManager.OperationOutcome outcome =
        manager.performOperation(new PoolOperation(), request, List.of(200L, 100L), user);

    assertEquals(
        List.of(200L, 100L), outcome.originsAfter().stream().map(ApiSubSample::getId).toList());
  }

  @Test
  void poolsTakeAllEmptiesEveryOriginOfWhatItActuallyHolds() throws Exception {
    serverBuiltOriginHolds(200L, "3");
    serverBuiltOriginHolds(100L, "5");
    when(sampleApiMgr.createNewApiSample(any(), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Pooled"));
    ApiInventoryOperationRequests.Pool request =
        creating(new ApiInventoryOperationRequests.Pool(), null, "Pooled", 1);
    request.setTakeAll(true);
    request.setOrigins(List.of(facadeOrigin(200L, null), facadeOrigin(100L, null)));

    manager.performOperation(new PoolOperation(), request, List.of(200L, 100L), user);

    ArgumentCaptor<QuantityInfo> taken = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(200L), taken.capture(), eq(user));
    assertEquals(0, new BigDecimal("3").compareTo(taken.getValue().getNumericValue()));
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), taken.capture(), eq(user));
    assertEquals(0, new BigDecimal("5").compareTo(taken.getValue().getNumericValue()));
  }

  /**
   * The value rules run AFTER the origins are read, because an operation's rules may need what the
   * origins hold.
   */
  @Test
  void aValueRejectionWritesNothing() {
    serverBuiltOriginHolds(100L, "5");
    ApiInventoryOperationRequests.Aliquot request =
        creating(
            new ApiInventoryOperationRequests.Aliquot(),
            facadeOrigin(100L, millilitres("1")),
            "Aliquots",
            1);
    request.setEachAmount(millilitres("0"));

    BindException rejection =
        assertThrows(
            BindException.class,
            () -> manager.performOperation(new AliquotOperation(), request, List.of(100L), user));

    assertEquals(
        "errors.inventory.operation.createdAmountNotPositive",
        rejection.getFieldErrors("eachAmount").get(0).getCode());
    verify(subSampleApiMgr, never()).registerApiSubSampleUsage(any(), any(), any());
    verifyNoInteractions(sampleApiMgr);
  }

  @Test
  void anUnreadableDocumentationTargetIsRejectedOnTheFieldTheCallerSentIt() {
    serverBuiltOriginHolds(100L, "5");
    ApiInventoryOperationRequests.Aliquot request =
        creating(
            new ApiInventoryOperationRequests.Aliquot(),
            facadeOrigin(100L, millilitres("1")),
            "Aliquots",
            1);
    request.setDocumentedByGlobalId("SD99");
    when(linkTargetResolver.targetExistsAndIsReadable(any(), eq(user))).thenReturn(false);

    BindException rejection =
        assertThrows(
            BindException.class,
            () -> manager.performOperation(new AliquotOperation(), request, List.of(100L), user));

    assertEquals(
        "errors.inventory.field.linkTargetNotFound",
        rejection.getFieldErrors("documentedByGlobalId").get(0).getCode());
    verifyNoInteractions(sampleApiMgr);
  }
}
