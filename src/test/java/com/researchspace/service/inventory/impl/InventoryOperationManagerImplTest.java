package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleEntity;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.InventoryOperationConfigRegistry;
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

  private InventoryOperationManagerImpl manager;
  private final User user = new User("anyUser");
  private long nextParentSampleId = 900L;

  private static ApiInventoryOperationOriginUpdate origin(Long id, ApiQuantityInfo amountTaken) {
    ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
    origin.setId(id);
    origin.setAmountTaken(amountTaken);
    return origin;
  }

  /**
   * A subsample entity mock currently holding the given quantity, in the given unit, under a parent
   * sample of its own (the manager locks the parents too, and a real subsample always has one).
   */
  private SubSample subSampleHolding(String value, int unitId) {
    return subSampleHolding(value, unitId, nextParentSampleId++);
  }

  /** A subsample holding the given quantity whose parent sample has the given id. */
  private SubSample subSampleHolding(String value, int unitId, long sampleId) {
    SubSample subSample = mock(SubSample.class);
    when(subSample.getQuantity())
        .thenReturn(value == null ? null : new QuantityInfo(new BigDecimal(value), unitId));
    SampleEntity parent = mock(SampleEntity.class);
    when(parent.getId()).thenReturn(sampleId);
    when(subSample.getSample()).thenReturn(parent);
    return subSample;
  }

  private void originHolds(long originId, SubSample subSample) {
    when(subSampleApiMgr.lockSubSampleForEdit(originId, user)).thenReturn(subSample);
  }

  @BeforeEach
  void setUp() {
    manager = new InventoryOperationManagerImpl();
    ReflectionTestUtils.setField(manager, "sampleApiMgr", sampleApiMgr);
    ReflectionTestUtils.setField(manager, "subSampleApiMgr", subSampleApiMgr);
    // the real registry over the real config: the live checks read emptiesOrigin per operation
    ReflectionTestUtils.setField(
        manager, "operationConfigs", new InventoryOperationConfigRegistry());
  }

  @Test
  void performOperationCreatesNewSampleAndReducesOriginByAmountTaken() throws Exception {
    ApiQuantityInfo amountTaken = new ApiQuantityInfo(new BigDecimal("0.6"), 3);
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(List.of(origin(100L, amountTaken)));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));

    ApiSampleWithFullSubSamples created = new ApiSampleWithFullSubSamples("Derived material");
    when(sampleApiMgr.createNewApiSample(newSample, user)).thenReturn(created);

    ApiSampleWithFullSubSamples result = manager.performOperation(request, user);

    // the created sample is returned unchanged
    assertSame(created, result);
    // permission on the origin is asserted, and the sample is created exactly once
    verify(subSampleApiMgr).lockSubSampleForEdit(100L, user);
    verify(sampleApiMgr).createNewApiSample(newSample, user);
    // the origin is REDUCED by the amount taken (registerApiSubSampleUsage subtracts and clamps at
    // zero, so it can never increase the origin)
    ArgumentCaptor<QuantityInfo> used = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), used.capture(), eq(user));
    assertEquals(0, new BigDecimal("0.6").compareTo(used.getValue().getNumericValue()));
    assertEquals(Integer.valueOf(3), used.getValue().getUnitId());
  }

  @Test
  void decrementsOriginBeforeCreatingTheNewSample() throws Exception {
    // The new subsample must end up most-recently-modified, so the origin is decremented (which
    // stamps its modification date) BEFORE the new sample + subsample are created
    // (DevDocs/adr/0007).
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    manager.performOperation(request, user);

    InOrder inOrder = inOrder(subSampleApiMgr, sampleApiMgr);
    inOrder.verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    inOrder.verify(sampleApiMgr).createNewApiSample(newSample, user);
  }

  @Test
  void abortsBeforeAnyMutationWhenAnOriginIsNotEditable() {
    // Validate-before-mutate (DevDocs/adr/0007): if the permission check on any origin fails,
    // nothing must
    // be written - neither the new sample created nor any origin reduced.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    doThrow(new RuntimeException("no permission"))
        .when(subSampleApiMgr)
        .lockSubSampleForEdit(100L, user);

    assertThrows(RuntimeException.class, () -> manager.performOperation(request, user));

    verify(sampleApiMgr, never()).createNewApiSample(any(), any());
    verify(subSampleApiMgr, never()).registerApiSubSampleUsage(any(), any(), any());
  }

  @Test
  void abortsBeforeAnyMutationWhenALaterOriginIsNotEditable() {
    // Multi-origin (Pool): permission is asserted on EVERY origin before ANY origin is mutated
    // (DevDocs/adr/0007). If a later origin fails the check, an earlier origin must NOT have been
    // decremented.
    // A single-origin test cannot catch a refactor that merges the assert and mutate loops; this
    // one
    // does - it would decrement origin 100 before checking origin 200's permission.
    originHolds(100L, subSampleHolding("5", 3));
    doThrow(new RuntimeException("no permission"))
        .when(subSampleApiMgr)
        .lockSubSampleForEdit(200L, user);
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
    request.setOrigins(
        List.of(
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3)),
            origin(200L, new ApiQuantityInfo(new BigDecimal("1.5"), 3))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));

    assertThrows(RuntimeException.class, () -> manager.performOperation(request, user));

    verify(subSampleApiMgr, never()).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    verify(sampleApiMgr, never()).createNewApiSample(any(), any());
  }

  @Test
  void terminalOperationAddsOriginFieldsAndCreatesNoSample() throws Exception {
    // Destroy (noOutput): no new sample is sent, and the operation adds a custom field to the
    // origin
    // itself. The manager must create no sample, return null, and apply the origin's extra fields
    // via
    // the subsample-edit path (DevDocs/adr/0007).
    ApiExtraField disposed = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    disposed.setName("disposed");
    disposed.setContent("2026-07-20");
    disposed.setNewFieldRequest(true);
    ApiInventoryOperationOriginUpdate origin =
        origin(100L, new ApiQuantityInfo(new BigDecimal("2"), 3));
    origin.setExtraFields(List.of(disposed));
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("destroy");
    request.setOrigins(List.of(origin));
    request.setNewSample(null);
    // destroy empties its origin: the amount taken equals what the origin currently holds
    originHolds(100L, subSampleHolding("2", 3));

    ApiSampleWithFullSubSamples result = manager.performOperation(request, user);

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
  void decrementsOriginsInAscendingIdOrderToAvoidLockOrderDeadlocks() throws Exception {
    // Two concurrent multi-origin operations over overlapping origins must acquire row locks in a
    // consistent order; the manager therefore mutates origins sorted by id, not in request order.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
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

    manager.performOperation(request, user);

    InOrder inOrder = inOrder(subSampleApiMgr);
    inOrder.verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    inOrder.verify(subSampleApiMgr).registerApiSubSampleUsage(eq(200L), any(), eq(user));
  }

  @Test
  void reducesEveryOriginByItsOwnAmountTaken() throws Exception {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
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

    manager.performOperation(request, user);

    // both origins are permission-checked and each is reduced by its own amount
    verify(subSampleApiMgr).lockSubSampleForEdit(100L, user);
    verify(subSampleApiMgr).lockSubSampleForEdit(200L, user);
    ArgumentCaptor<QuantityInfo> first = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), first.capture(), eq(user));
    assertEquals(0, new BigDecimal("0.6").compareTo(first.getValue().getNumericValue()));
    ArgumentCaptor<QuantityInfo> second = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(200L), second.capture(), eq(user));
    assertEquals(0, new BigDecimal("1.5").compareTo(second.getValue().getNumericValue()));
  }

  // --- live-state rules, enforced inside the operation's transaction (DevDocs/adr/0007) ---

  private BindException performExpectingRejection(ApiInventoryOperationPost request) {
    BindException rejection =
        assertThrows(BindException.class, () -> manager.performOperation(request, user));
    verify(subSampleApiMgr, never()).registerApiSubSampleUsage(any(), any(), any());
    verify(subSampleApiMgr, never()).updateApiSubSample(any(), any());
    verify(sampleApiMgr, never()).createNewApiSample(any(), any());
    return rejection;
  }

  @Test
  void rejectsOperatingOnAnOriginThatCurrentlyHoldsNothing() {
    for (SubSample empty : List.of(subSampleHolding("0", 3), subSampleHolding(null, 3))) {
      ApiInventoryOperationPost request = new ApiInventoryOperationPost();
      request.setOperationType("derive");
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
    request.setOperationType("derive");
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("6"), 3))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("5", 3));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.amountTakenExceedsOrigin",
        rejection.getFieldErrors("origins[0].amountTaken").get(0).getCode());
  }

  @Test
  void rejectsOriginEmptyingOperationThatTakesLessThanTheOriginHolds() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("destroy");
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("3"), 3))));
    originHolds(100L, subSampleHolding("5", 3));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.mustEmptyOrigin",
        rejection.getFieldErrors("origins[0].amountTaken").get(0).getCode());
  }

  @Test
  void rejectsPoolingOriginsFromDifferentMeasurementCategories() {
    // The wizard blocks pooling a volume origin with a mass origin; the endpoint must too, or the
    // pooled sample's quantity would be meaningless (security review, finding 4).
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
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
    // millilitres and litres share the volume category: unit variety is fine, category mixing not
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
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

    manager.performOperation(request, user);

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(200L), any(), eq(user));
  }

  @Test
  void rejectionOnALaterOriginMutatesNothing() {
    // The live checks run over EVERY origin before ANY origin is mutated, in the same transaction:
    // a violation on the second origin must leave the first untouched.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
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
    // 0.006 kg = 6 g, which exceeds a 5 g origin.
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
    // a volume amount against a mass origin is not commensurate, so it is not treated as
    // over-removal
    assertFalse(
        InventoryOperationManagerImpl.amountTakenExceedsOrigin(millilitres("6"), grams("5")));
  }

  @Test
  void flagsPositiveAmountTakenFromOriginWithNoQuantity() {
    // A subsample whose quantity was never set holds nothing, so taking any positive amount from it
    // is over-removal (DevDocs/adr/0007). A null origin quantity, or one with a null numeric value,
    // is treated as zero available rather than as "no limit".
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
    // 0.005 kg denotes the same amount as 5 g
    assertTrue(
        InventoryOperationManagerImpl.amountTakenEmptiesOrigin(
            new ApiQuantityInfo(new BigDecimal("0.005"), RSUnitDef.KILO.getId()), grams("5")));
    assertFalse(InventoryOperationManagerImpl.amountTakenEmptiesOrigin(grams("4"), grams("5")));
    assertFalse(InventoryOperationManagerImpl.amountTakenEmptiesOrigin(grams("6"), grams("5")));
    // incomparable categories and missing values never count as emptying
    assertFalse(
        InventoryOperationManagerImpl.amountTakenEmptiesOrigin(millilitres("5"), grams("5")));
    assertFalse(InventoryOperationManagerImpl.amountTakenEmptiesOrigin(null, grams("5")));
    assertFalse(InventoryOperationManagerImpl.amountTakenEmptiesOrigin(grams("5"), null));
  }

  // --- measurement categories against the origin's live quantity (code review F4, F5) ---

  private static ApiSubSample subSampleOf(ApiQuantityInfo quantity) {
    ApiSubSample subSample = new ApiSubSample();
    subSample.setQuantity(quantity);
    return subSample;
  }

  @Test
  void rejectsAmountTakenFromADifferentMeasurementCategoryThanTheOrigin() {
    // Grams taken from a millilitre origin used to reach QuantityUtils.sum and surface as a 422.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
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
    // Without a template the wizard offers only the origin's category for the created amounts, so
    // a gram child from a millilitre origin is a request the wizard never builds.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
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
    // With a template the created amounts follow the template's category, not the origin's (a
    // DNA extract in microlitres derived from tissue in grams); the controller's template check
    // owns that rule.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(List.of(origin(100L, grams("1"))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("DNA extract");
    newSample.setTemplateId(7L);
    newSample.getSubSamples().add(subSampleOf(millilitres("0.5")));
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", RSUnitDef.GRAM.getId()));
    when(sampleApiMgr.createNewApiSample(newSample, user)).thenReturn(newSample);

    manager.performOperation(request, user);

    verify(sampleApiMgr).createNewApiSample(newSample, user);
  }

  @Test
  void locksOriginsInAscendingIdOrderAndReportsErrorsAtTheirRequestIndex() {
    // The row locks taken by the live-state read must be acquired in the same consistent order as
    // the decrements, so two overlapping Pool requests cannot deadlock; the error path still names
    // the origin by its position in the request (code review, finding 1).
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
    request.setOrigins(List.of(origin(300L, millilitres("1")), origin(100L, millilitres("9"))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Pooled material"));
    originHolds(300L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId()));
    originHolds(100L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId()));

    BindException rejection = performExpectingRejection(request);

    InOrder inOrder = inOrder(subSampleApiMgr);
    inOrder.verify(subSampleApiMgr).lockSubSampleForEdit(100L, user);
    inOrder.verify(subSampleApiMgr).lockSubSampleForEdit(300L, user);
    assertEquals(
        "errors.inventory.operation.amountTakenExceedsOrigin",
        rejection.getFieldErrors("origins[1].amountTaken").get(0).getCode());
  }

  @Test
  void locksOriginsAscendingThenTheirParentSamplesAscending() {
    // Decrementing a subsample rewrites its parent sample's denormalised total, so two operations
    // on sibling subsamples of one sample must serialise on the parent row too or one total is
    // written from a stale read (code review, finding 2). The parents are locked after the origins
    // and in id order: every other writer takes the subsample row first and then the sample row, so
    // locking the other way round would invert the order against all of them.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
    request.setOrigins(List.of(origin(300L, millilitres("1")), origin(100L, millilitres("1"))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Pooled material"));
    originHolds(300L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId(), 30L));
    originHolds(100L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId(), 20L));
    when(sampleApiMgr.createNewApiSample(any(ApiSampleWithFullSubSamples.class), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Pooled material"));

    assertDoesNotThrow(() -> manager.performOperation(request, user));

    InOrder inOrder = inOrder(subSampleApiMgr, sampleApiMgr);
    inOrder.verify(subSampleApiMgr).lockSubSampleForEdit(100L, user);
    inOrder.verify(subSampleApiMgr).lockSubSampleForEdit(300L, user);
    inOrder.verify(sampleApiMgr).lockSampleForEdit(20L, user);
    inOrder.verify(sampleApiMgr).lockSampleForEdit(30L, user);
  }

  @Test
  void locksEachParentSampleOnceWhenOriginsAreSiblings() {
    // Two origins under one sample are one row: asking twice is harmless but pointless, and the
    // second ask would be a re-lock of an entity the first decrement has already dirtied.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
    request.setOrigins(List.of(origin(100L, millilitres("1")), origin(300L, millilitres("1"))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Pooled material"));
    originHolds(100L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId(), 20L));
    originHolds(300L, subSampleHolding("5", RSUnitDef.MILLI_LITRE.getId(), 20L));
    when(sampleApiMgr.createNewApiSample(any(ApiSampleWithFullSubSamples.class), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Pooled material"));

    assertDoesNotThrow(() -> manager.performOperation(request, user));

    verify(sampleApiMgr, times(1)).lockSampleForEdit(20L, user);
  }
}
