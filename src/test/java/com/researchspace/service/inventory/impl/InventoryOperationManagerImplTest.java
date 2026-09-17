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
    // Lenient: only the server-built path reads the parent; most callers of this helper never
    // touch it.
    lenient().when(parent.getId()).thenReturn(sampleId);
    lenient().when(subSample.getSample()).thenReturn(parent);
    return subSample;
  }

  private void originHolds(long originId, SubSample subSample) {
    when(subSampleApiMgr.assertUserCanEditSubSample(originId, user)).thenReturn(subSample);
    // Lenient: a request rejected on its values never reaches the live-state read.
    lenient().when(subSampleApiMgr.getIfExists(originId)).thenReturn(subSample);
    // Lenient because most tests here assert on the mutation rather than the envelope and never
    // reach this stub; a test that cares stubs its own.
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
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    manager.execute(request, user);

    InOrder inOrder = inOrder(subSampleApiMgr, sampleApiMgr);
    inOrder.verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    inOrder.verify(sampleApiMgr).createNewApiSample(newSample, user);
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
    // A single-origin test cannot catch a refactor that merges the assert and mutate loops; this
    // one does - it would decrement origin 100 before checking origin 200's permission.
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
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(
            origin(200L, new ApiQuantityInfo(new BigDecimal("1.5"), 3)),
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));
    originHolds(200L, subSampleHolding("5", 3));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    manager.execute(request, user);

    InOrder inOrder = inOrder(subSampleApiMgr);
    inOrder.verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    inOrder.verify(subSampleApiMgr).registerApiSubSampleUsage(eq(200L), any(), eq(user));
  }

  @Test
  void reducesEveryOriginByItsOwnAmountTaken() throws Exception {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3)),
            origin(200L, new ApiQuantityInfo(new BigDecimal("1.5"), 3))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));
    originHolds(200L, subSampleHolding("5", 3));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

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
      assertEquals(
          "errors.inventory.operation.originEmpty",
          rejection.getFieldErrors("origins[0].id").get(0).getCode());
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
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("5"), 3))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.execute(request, user));
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void acceptsTakeAllSubmittedInADifferentUnitOfTheSameCategory() throws Exception {
    // The guard is numeric equality after unit conversion, not unit-literal equality: 0.01 l is the
    // whole of a 10 ml origin, and a client is free to submit either.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.01"), RSUnitDef.LITRE.getId()))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("10", RSUnitDef.MILLI_LITRE.getId()));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.execute(request, user));
  }

  @Test
  void acceptsAPartialAmountFromAnOperationThatDoesNotEmptyItsOrigin() throws Exception {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    ApiInventoryOperationOriginUpdate origin =
        origin(100L, new ApiQuantityInfo(new BigDecimal("3"), 3));
    request.setOrigins(List.of(origin));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

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
        rejection.getFieldErrors("origins[1].id").get(0).getCode());
  }

  @Test
  void allowsPoolingOriginsAcrossUnitsOfTheSameCategory() throws Exception {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), RSUnitDef.MILLI_LITRE.getId())),
            origin(200L, new ApiQuantityInfo(new BigDecimal("0.001"), RSUnitDef.LITRE.getId()))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Pooled material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId()));
    originHolds(200L, subSampleHolding("2", RSUnitDef.LITRE.getId()));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Pooled material"));

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
  }

  @Test
  void flagsPositiveAmountTakenFromOriginWithNoQuantity() {
    // A null origin quantity, or one with a null numeric value, is treated as zero available
    // rather than as "no limit".
    assertTrue(InventoryOperationManagerImpl.amountTakenExceedsOrigin(grams("6"), null));
    assertTrue(
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
    // With a template the created amounts follow the template's category, not the origin's; a
    // separate template-conformance check owns that rule.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, grams("1"))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("DNA extract");
    newSample.setTemplateId(7L);
    newSample.getSubSamples().add(subSampleOf(millilitres("0.5")));
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", RSUnitDef.GRAM.getId()));
    when(sampleApiMgr.createNewApiSample(newSample, user)).thenReturn(newSample);

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
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(
            origin(
                100L,
                new ApiQuantityInfo(new BigDecimal("0.001"), RSUnitDef.MICRO_LITRE.getId()))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("1", RSUnitDef.LITRE.getId()));
    when(sampleApiMgr.createNewApiSample(any(ApiSampleWithFullSubSamples.class), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.execute(request, user));

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void aWithdrawalWhoseRemainderNeedsASmallerUnitThanEitherOperandIsAccepted() throws Exception {
    // 1500.4 ml less 0.5 l: QuantitySummingVisitor works in the LARGEST unit of the operands, so
    // the remainder arises as 1.0004 l, which does not fit 3dp. It is 1000.4 ml, which does, so the
    // subtraction steps down to the unit that holds it and no stock is lost.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.5"), RSUnitDef.LITRE.getId()))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("1500.4", RSUnitDef.MILLI_LITRE.getId()));
    when(sampleApiMgr.createNewApiSample(any(ApiSampleWithFullSubSamples.class), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.execute(request, user));

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void aWithdrawalThatRedenominatesTheOriginIsAccepted() throws Exception {
    // 0.001 ul taken from a 1 ml origin redenominates it to 999.999 ul; the result is EXACT (no
    // stock lost), so it must not be rejected merely because the unit changed.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(
            origin(
                100L,
                new ApiQuantityInfo(new BigDecimal("0.001"), RSUnitDef.MICRO_LITRE.getId()))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("1", RSUnitDef.MILLI_LITRE.getId()));
    when(sampleApiMgr.createNewApiSample(any(ApiSampleWithFullSubSamples.class), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.execute(request, user));

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void aSubUnitWithdrawalIsAccepted() throws Exception {
    // 2.5 mg from a 5 g origin leaves 4.9975 g, which needs four decimal places and is
    // unrepresentable only IN GRAMS: as 4997.5 mg the same DECIMAL(19,3) column holds it exactly,
    // so the remainder must be stored in the finer unit rather than rejected.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(
            origin(
                100L, new ApiQuantityInfo(new BigDecimal("2.5"), RSUnitDef.MILLI_GRAM.getId()))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("5", RSUnitDef.GRAM.getId()));
    when(sampleApiMgr.createNewApiSample(any(ApiSampleWithFullSubSamples.class), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

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
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.5"), RSUnitDef.LITRE.getId()))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("1500", RSUnitDef.MILLI_LITRE.getId()));
    when(sampleApiMgr.createNewApiSample(any(), any()))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.execute(request, user));
  }

  // --- template conformance ---

  @Test
  void runsTheTemplateConformanceCheckBeforeAnyOriginRead() throws Exception {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    manager.execute(request, user);

    InOrder inOrder = inOrder(templateConformance, subSampleApiMgr);
    inOrder.verify(templateConformance).validate(request, user);
    inOrder.verify(subSampleApiMgr).assertUserCanEditSubSample(100L, user);
  }

  // --- the server-built path, as the typed facades drive it ---

  private static final int ML = RSUnitDef.MILLI_LITRE.getId();

  /**
   * An origin the server-built path can read: name and global id for the generated field names, a
   * parent with no fields for the computed values, and the same quantity as entity and builder
   * snapshot.
   */
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
    wireServerBuiltCollaborators();
  }

  private boolean serverBuiltCollaboratorsWired;

  /**
   * Once per test, so a multi-origin case can stub two origins without the second call replacing
   * the first's message source and leaving its stubbing unused (strict stubs would flag it).
   */
  private void wireServerBuiltCollaborators() {
    if (serverBuiltCollaboratorsWired) {
      return;
    }
    serverBuiltCollaboratorsWired = true;
    org.springframework.context.MessageSource messages =
        mock(org.springframework.context.MessageSource.class);
    // Lenient: a test that is rejected before the operation builds anything never resolves a
    // label, and the rejection is the point of those tests.
    lenient()
        .when(messages.getMessage(any(String.class), any(), any(String.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(2));
    ReflectionTestUtils.setField(manager, "messageSource", messages);
    // These built-path cases are about the builder, not conformance, so templateConformance is a
    // mock that accepts whatever the builder produced.
    ReflectionTestUtils.setField(
        manager,
        "templateConformance",
        mock(com.researchspace.service.inventory.OperationTemplateConformanceValidator.class));
  }

  // --- zero amounts: what "take nothing" means to each kind of operation ---

  @Test
  void aPassageWithAnExplicitZeroAmountDecrementsNothingAndStillCreatesTheSample()
      throws Exception {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    ApiInventoryOperationOriginUpdate origin = origin(100L, millilitres("0"));
    request.setOrigins(List.of(origin));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("HeLa p3");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", ML));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("HeLa p3"));

    manager.execute(request, user);

    ArgumentCaptor<QuantityInfo> used = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), used.capture(), eq(user));
    assertEquals(0, used.getValue().getNumericValue().signum(), "nothing is taken");
    assertEquals(ML, used.getValue().getUnitId());
    verify(sampleApiMgr).createNewApiSample(newSample, user);
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
        rejection.getFieldErrors("origins[0].id").get(0).getCode());
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

  @Test
  void anExplicitAmountEqualToTheOriginEmptiesIt() throws Exception {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    ApiInventoryOperationOriginUpdate origin = origin(100L, millilitres("5"));
    request.setOrigins(List.of(origin));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Aliquots");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", ML));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Aliquots"));

    manager.execute(request, user);

    ArgumentCaptor<QuantityInfo> used = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), used.capture(), eq(user));
    assertEquals(0, new BigDecimal("5").compareTo(used.getValue().getNumericValue()));
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
    // Passage sends no amount. A zero in the origin's unit must reach the core rather than a null
    // the decrement would dereference.
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
    wireServerBuiltCollaborators();
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
    // Destroy sends no amount: it takes whatever is there, read from the origin's live snapshot.
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
    // The outcome lists origins in the order the caller gave them, even though the core processes
    // them ascending by id.
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
   * The operation's own value rules run before anything is written, so a rejected request leaves
   * every origin exactly as it was. They run AFTER the origins are read, because an operation's
   * rules may need what the origins hold.
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
