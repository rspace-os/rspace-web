package com.researchspace.service.inventory.impl;

import static com.researchspace.service.inventory.InventoryOperationManager.InTransactionValidation.NONE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import com.researchspace.api.v1.model.ApiInventoryOperationAmountMode;
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
import com.researchspace.service.inventory.InventoryOperationManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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

  private InventoryOperationManagerImpl manager;
  private final User user = new User("anyUser");
  private long nextParentSampleId = 900L;

  private static ApiInventoryOperationOriginUpdate origin(Long id, ApiQuantityInfo amountTaken) {
    ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
    origin.setId(id);
    origin.setAmountTaken(amountTaken);
    return origin;
  }

  /** An origin whose amount is a claim on the origin's whole quantity ("take all"). */
  private static ApiInventoryOperationOriginUpdate takeAll(Long id, ApiQuantityInfo amountTaken) {
    ApiInventoryOperationOriginUpdate origin = origin(id, amountTaken);
    origin.setAmountMode(ApiInventoryOperationAmountMode.ALL);
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
    // Lenient: only the server-built path's parentFields(subSample.getSample()) reads the
    // parent, so most callers of this helper never touch it.
    lenient().when(parent.getId()).thenReturn(sampleId);
    lenient().when(subSample.getSample()).thenReturn(parent);
    return subSample;
  }

  private void originHolds(long originId, SubSample subSample) {
    when(subSampleApiMgr.assertUserCanEditSubSample(originId, user)).thenReturn(subSample);
    when(subSampleApiMgr.getIfExists(originId)).thenReturn(subSample);
    // The server-built overload maps each origin again once the operation is done. Lenient because
    // most tests here assert on the mutation rather than the envelope and never reach it; a test
    // that cares stubs its own.
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

    ApiSampleWithFullSubSamples result = manager.performOperation(request, user, NONE);

    // the created sample is returned unchanged
    assertSame(created, result);
    // permission on the origin is asserted, and the sample is created exactly once
    verify(subSampleApiMgr).assertUserCanEditSubSample(100L, user);
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

    manager.performOperation(request, user, NONE);

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
        .assertUserCanEditSubSample(100L, user);

    assertThrows(RuntimeException.class, () -> manager.performOperation(request, user, NONE));

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
    // Origin 100's permission check must succeed, but nothing past it ever runs, so it needs no
    // quantity stub (the permission loop rejects origin 200 before the quantity loop starts).
    when(subSampleApiMgr.assertUserCanEditSubSample(100L, user)).thenReturn(mock(SubSample.class));
    doThrow(new RuntimeException("no permission"))
        .when(subSampleApiMgr)
        .assertUserCanEditSubSample(200L, user);
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
    request.setOrigins(
        List.of(
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3)),
            origin(200L, new ApiQuantityInfo(new BigDecimal("1.5"), 3))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));

    assertThrows(RuntimeException.class, () -> manager.performOperation(request, user, NONE));

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

    ApiSampleWithFullSubSamples result = manager.performOperation(request, user, NONE);

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
    // The manager mutates origins sorted by id, not in request order.
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

    manager.performOperation(request, user, NONE);

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

    manager.performOperation(request, user, NONE);

    // both origins are permission-checked and each is reduced by its own amount
    verify(subSampleApiMgr).assertUserCanEditSubSample(100L, user);
    verify(subSampleApiMgr).assertUserCanEditSubSample(200L, user);
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
        assertThrows(BindException.class, () -> manager.performOperation(request, user, NONE));
    verifyNoMutation();
    return rejection;
  }

  /** Nothing was written: every rejection must land before the first mutating collaborator call. */
  private void verifyNoMutation() {
    verify(subSampleApiMgr, never()).registerApiSubSampleUsage(any(), any(), any());
    verify(subSampleApiMgr, never()).updateApiSubSample(any(), any());
    verify(sampleApiMgr, never()).createNewApiSample(any(), any());
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
  void acceptsTakeAllWhoseAmountStillMatchesTheOrigin() throws Exception {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(List.of(takeAll(100L, new ApiQuantityInfo(new BigDecimal("5"), 3))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.performOperation(request, user, NONE));
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void acceptsTakeAllSubmittedInADifferentUnitOfTheSameCategory() throws Exception {
    // The guard is numeric equality after unit conversion, not unit-literal equality: 0.01 l is the
    // whole of a 10 ml origin, and a client is free to submit either.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(
        List.of(
            takeAll(100L, new ApiQuantityInfo(new BigDecimal("0.01"), RSUnitDef.LITRE.getId()))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("10", RSUnitDef.MILLI_LITRE.getId()));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.performOperation(request, user, NONE));
  }

  @Test
  void doesNotCompareAnExplicitAmountAgainstTheWholeOrigin() throws Exception {
    // An explicit amount is what the user typed; it is not a claim about the origin's total, so
    // taking less than the origin holds is the ordinary case and must not conflict.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    ApiInventoryOperationOriginUpdate origin =
        origin(100L, new ApiQuantityInfo(new BigDecimal("3"), 3));
    origin.setAmountMode(ApiInventoryOperationAmountMode.EXPLICIT);
    request.setOrigins(List.of(origin));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.performOperation(request, user, NONE));
  }

  @Test
  void acceptsAnAbsentAmountModeOnAnEmptyingOperationThatDoesTakeEverything() throws Exception {
    // Backward compatibility: a Destroy request predating amountMode carries no mode at all, and
    // one that takes exactly what the origin holds is still a valid request.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("destroy");
    ApiInventoryOperationOriginUpdate origin =
        origin(100L, new ApiQuantityInfo(new BigDecimal("5"), 3));
    assertNull(origin.getAmountMode());
    request.setOrigins(List.of(origin));
    originHolds(100L, subSampleHolding("5", 3));

    assertDoesNotThrow(() -> manager.performOperation(request, user, NONE));
  }

  @Test
  void rejectsAnAbsentAmountModeOnAnEmptyingOperationAsA400() {
    // A client predating amountMode asking Destroy for PART of an origin is making a malformed
    // request: nothing has changed, so it gets the same field error it got before the mode
    // existed.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("destroy");
    ApiInventoryOperationOriginUpdate origin =
        origin(100L, new ApiQuantityInfo(new BigDecimal("3"), 3));
    assertNull(origin.getAmountMode());
    request.setOrigins(List.of(origin));
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

    manager.performOperation(request, user, NONE);

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

    manager.performOperation(request, user, NONE);

    verify(sampleApiMgr).createNewApiSample(newSample, user);
  }

  @Test
  void readsOriginsInAscendingIdOrderAndReportsErrorsAtTheirRequestIndex() {
    // Origins are read in ascending id order regardless of request order; the error path still
    // names the origin by its position in the request (code review, finding 1).
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("pool");
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
    // 0.001 ul from a 1 l origin leaves 999999.999 ul, which DECIMAL(19,3) holds exactly. This was
    // rejected on the grounds that the operation would create its output without decrementing the
    // origin at all, because 0.999999999 l rounds back to 1 l (Copilot review, PR #1090). True of
    // the value expressed in litres; not true of the value, which is why the remedy is to store it
    // in the unit that holds it rather than to refuse the operation (review 2026-09-14, Q1b).
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(
        List.of(
            origin(
                100L,
                new ApiQuantityInfo(new BigDecimal("0.001"), RSUnitDef.MICRO_LITRE.getId()))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("1", RSUnitDef.LITRE.getId()));
    when(sampleApiMgr.createNewApiSample(any(ApiSampleWithFullSubSamples.class), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.performOperation(request, user, NONE));

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void aWithdrawalWhoseRemainderNeedsASmallerUnitThanEitherOperandIsAccepted() throws Exception {
    // 1500.4 ml less 0.5 l. QuantitySummingVisitor works in the LARGEST unit of the operands, so
    // the remainder arises as 1.0004 l, which does not fit 3dp and used to be rejected as silently
    // losing an extra 0.4 ml (Codex review, PR #1090). It is 1000.4 ml, which fits, so the
    // subtraction now steps down to the unit that holds it and no stock is lost either way.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(
        List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.5"), RSUnitDef.LITRE.getId()))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("1500.4", RSUnitDef.MILLI_LITRE.getId()));
    when(sampleApiMgr.createNewApiSample(any(ApiSampleWithFullSubSamples.class), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.performOperation(request, user, NONE));

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void aWithdrawalThatRedenominatesTheOriginIsAccepted() throws Exception {
    // Live-run finding F3, revisited. 0.001 ul from a 1 ml origin came back as 999.999 ul, and the
    // recorded complaint was that the origin's unit changed underneath the user. That result was
    // EXACT: no stock was lost. The complaint is a UX one and it was answered by rejecting the
    // arithmetic, which also made 2.5 mg from 5 g impossible. Accepted again, and the user-facing
    // half of F3 - telling the caller the unit was re-denominated - is Q1c and still open.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(
        List.of(
            origin(
                100L,
                new ApiQuantityInfo(new BigDecimal("0.001"), RSUnitDef.MICRO_LITRE.getId()))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("1", RSUnitDef.MILLI_LITRE.getId()));
    when(sampleApiMgr.createNewApiSample(any(ApiSampleWithFullSubSamples.class), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.performOperation(request, user, NONE));

    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
  }

  @Test
  void aSubUnitWithdrawalIsAccepted() throws Exception {
    // 2.5 mg from a 5 g origin leaves 4.9975 g, which needs four decimal places and so used to be
    // refused as unsubtractable. The remainder is not unrepresentable, only unrepresentable IN
    // GRAMS: it is 4997.5 mg, which the same DECIMAL(19,3) column holds exactly. The column stores
    // a number and a unit id, so the fix is to store the remainder in the finer unit rather than
    // to reject ordinary lab work (review 2026-09-14, Q1a/Q1b).
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(
        List.of(
            origin(
                100L, new ApiQuantityInfo(new BigDecimal("2.5"), RSUnitDef.MILLI_GRAM.getId()))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("5", RSUnitDef.GRAM.getId()));
    when(sampleApiMgr.createNewApiSample(any(ApiSampleWithFullSubSamples.class), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.performOperation(request, user, NONE));

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
  void anUnreadableDocumentationTargetIsAFieldErrorNamingTheFieldTheCallerSent() {
    // The target was only resolved while the built sample's link was being created, deep inside
    // the transaction, where it became a bare 422 carrying no field path at all: every other 4xx
    // this API answers names the field the caller sent (live test 2026-09-13, F4). Checked with
    // the inputs, before any origin is read, so nothing is locked for a request already known bad.
    when(linkTargetResolver.targetExistsAndIsReadable(any(), eq(user))).thenReturn(false);

    BindException rejection =
        assertThrows(
            BindException.class,
            () ->
                manager.performOperation(
                    "aliquot",
                    List.of(
                        origin(100L, new ApiQuantityInfo(BigDecimal.ONE, RSUnitDef.GRAM.getId()))),
                    Map.of(
                        "sampleName",
                        "Aliquots",
                        "count",
                        1,
                        "eachAmount",
                        new ApiQuantityInfo(new BigDecimal("0.5"), RSUnitDef.GRAM.getId())),
                    null,
                    "SD999999999",
                    user));

    assertEquals(
        "errors.inventory.field.linkTargetNotFound",
        rejection.getFieldErrors("documentedByGlobalId").get(0).getCode());
    verifyNoInteractions(sampleApiMgr);
  }

  @Test
  void acceptsACrossUnitAmountTakenWhoseSubtractionIsExact() throws Exception {
    // The counterpart of the row above: 0.5 l from a 1500 ml origin leaves exactly 1 l, which is
    // storable, so the guard must not reject a legitimate cross-unit take.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(
        List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.5"), RSUnitDef.LITRE.getId()))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("1500", RSUnitDef.MILLI_LITRE.getId()));
    when(sampleApiMgr.createNewApiSample(any(), any()))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    assertDoesNotThrow(() -> manager.performOperation(request, user, NONE));
  }

  // --- the caller-supplied in-transaction validation (template conformance) ---

  @Test
  void runsTheInTransactionValidationBeforeAnyOriginRead() throws Exception {
    // The controller's template-conformance check used to run in its own transaction, so a template
    // changed between it and the operation could still fail mid-mutation. The check is now handed
    // in and run HERE, inside the operation's transaction, before any origin is read or locked
    // (Copilot review, PR #1090).
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", 3));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));
    InventoryOperationManager.InTransactionValidation check =
        mock(InventoryOperationManager.InTransactionValidation.class);

    manager.performOperation(request, user, check);

    InOrder inOrder = inOrder(check, subSampleApiMgr);
    inOrder.verify(check).validate();
    inOrder.verify(subSampleApiMgr).assertUserCanEditSubSample(100L, user);
  }

  // --- the server-built path, as the typed facades drive it (M6) ---

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
    // the field names resolve to their default (the key) rather than a real catalog
    org.springframework.context.MessageSource messages =
        mock(org.springframework.context.MessageSource.class);
    when(messages.getMessage(any(String.class), any(), any(String.class), any()))
        .thenAnswer(invocation -> invocation.getArgument(2));
    ReflectionTestUtils.setField(manager, "messageSource", messages);
    // The template-conformance check moved out of the controller into service.inventory (parallel
    // review, L1); these built-path cases are about the builder, not conformance, so it is a mock
    // that accepts whatever the builder produced.
    ReflectionTestUtils.setField(
        manager,
        "templateConformance",
        mock(com.researchspace.service.inventory.OperationTemplateConformanceValidator.class));
  }

  /** A facade origin element: an id, optionally an amount, no mode. */
  private static ApiInventoryOperationOriginUpdate facadeOrigin(long id, ApiQuantityInfo amount) {
    return origin(id, amount);
  }

  private static java.util.Map<String, Object> creatingInputs(String name, Integer count) {
    java.util.Map<String, Object> inputs = new java.util.LinkedHashMap<>();
    inputs.put("sampleName", name);
    if (count != null) {
      inputs.put("count", count);
    }
    inputs.put("eachAmount", millilitres("0.5"));
    return inputs;
  }

  private ApiSampleWithFullSubSamples createdSample() {
    ArgumentCaptor<ApiSampleWithFullSubSamples> built =
        ArgumentCaptor.forClass(ApiSampleWithFullSubSamples.class);
    verify(sampleApiMgr).createNewApiSample(built.capture(), eq(user));
    return built.getValue();
  }

  @Test
  void aFacadeOriginWithoutAnAmountTakesNothingOnAnOperationThatTakesNothing() throws Exception {
    // Passage: the facade sends no amount (M0 shape). The builder's zero, in the origin's unit,
    // must reach the core rather than a null the decrement would dereference.
    serverBuiltOriginHolds(100L, "5");

    manager.performOperation(
        "passage",
        List.of(facadeOrigin(100L, null)),
        creatingInputs("HeLa p3", 1),
        null,
        null,
        user);

    ArgumentCaptor<QuantityInfo> taken = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), taken.capture(), eq(user));
    assertEquals(0, taken.getValue().getNumericValue().signum());
    assertEquals(ML, taken.getValue().getUnitId());
  }

  @Test
  void aFacadeOriginWithoutAnAmountEmptiesTheOriginOnADestroy() throws Exception {
    // Destroy: no amount, no expected quantity means "take whatever is there" (M0 D5). The
    // builder substitutes the origin's live snapshot as the amount taken.
    serverBuiltOriginHolds(100L, "5");

    assertNull(
        manager
            .performOperation(
                "destroy", List.of(facadeOrigin(100L, null)), java.util.Map.of(), null, null, user)
            .sample(),
        "a terminal operation creates no sample, but still reports its origins (A14)");

    ArgumentCaptor<QuantityInfo> taken = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), taken.capture(), eq(user));
    assertEquals(0, new BigDecimal("5").compareTo(taken.getValue().getNumericValue()));
    verify(subSampleApiMgr).updateApiSubSample(any(), eq(user));
  }

  @Test
  void anAbsentCountDefaultsToOneSubsample() throws Exception {
    // M0 D7: count is optional on the typed facades with a server default of 1; without the
    // default the builder refuses a null count and the request would be a 500.
    serverBuiltOriginHolds(100L, "5");
    when(sampleApiMgr.createNewApiSample(any(), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Aliquots"));

    manager.performOperation(
        "aliquot",
        List.of(facadeOrigin(100L, millilitres("1"))),
        creatingInputs("Aliquots", null),
        null,
        null,
        user);

    assertEquals(1, createdSample().getSubSamples().size());
  }

  @Test
  void anAbsentReviveStorageTempDefaultsToFourCelsius() throws Exception {
    serverBuiltOriginHolds(100L, "5");
    when(sampleApiMgr.createNewApiSample(any(), eq(user)))
        .thenReturn(new ApiSampleWithFullSubSamples("Revived"));

    manager.performOperation(
        "revive",
        List.of(facadeOrigin(100L, millilitres("1"))),
        creatingInputs("Revived", 1),
        null,
        null,
        user);

    ApiSampleWithFullSubSamples created = createdSample();
    assertEquals(
        new ApiQuantityInfo(new BigDecimal("4"), RSUnitDef.CELSIUS), created.getStorageTempMin());
    assertEquals(created.getStorageTempMin(), created.getStorageTempMax());
  }

  @Test
  void anInTransactionValidationRejectionPreventsAllReadsAndMutations() throws Exception {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    InventoryOperationManager.InTransactionValidation check =
        mock(InventoryOperationManager.InTransactionValidation.class);
    doThrow(new BindException(request, "apiInventoryOperationPost")).when(check).validate();

    assertThrows(BindException.class, () -> manager.performOperation(request, user, check));

    verifyNoInteractions(subSampleApiMgr, sampleApiMgr);
  }

  // --- zero amounts: what "take nothing" means to each kind of operation ---

  @Test
  void aPassageWithAnExplicitZeroAmountDecrementsNothingAndStillCreatesTheSample()
      throws Exception {
    // The wizard's Passage sends amountTaken 0 in the origin's unit under an explicit mode. Zero
    // passes every live rule, reaches the usage register as a no-op, and the sample is created.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("passage");
    ApiInventoryOperationOriginUpdate origin = origin(100L, millilitres("0"));
    origin.setAmountMode(ApiInventoryOperationAmountMode.EXPLICIT);
    request.setOrigins(List.of(origin));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("HeLa p3");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", ML));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("HeLa p3"));

    manager.performOperation(request, user, NONE);

    ArgumentCaptor<QuantityInfo> used = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), used.capture(), eq(user));
    assertEquals(0, used.getValue().getNumericValue().signum(), "nothing is taken");
    assertEquals(ML, used.getValue().getUnitId());
    verify(sampleApiMgr).createNewApiSample(newSample, user);
  }

  @Test
  void aPassageOnAnOriginThatHoldsNothingIsStillRejectedAsEmpty() {
    // Taking nothing from nothing is not allowed either: every operation needs an origin that
    // currently holds something (DevDocs/adr/0007), so the zero amount earns no exemption.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("passage");
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
    // Destroy asked to take 0 of a 5 ml origin, with no whole-origin claim: a 400 on the amount,
    // never a partial take.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("destroy");
    request.setOrigins(List.of(origin(100L, millilitres("0"))));
    originHolds(100L, subSampleHolding("5", ML));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.mustEmptyOrigin",
        rejection.getFieldErrors("origins[0].amountTaken").get(0).getCode());
  }

  @Test
  void anExplicitAmountEqualToTheOriginEmptiesIt() throws Exception {
    // An Aliquot taking exactly what the origin holds is neither over-removal nor a whole-origin
    // claim, so it proceeds and the register receives the full 5 ml, leaving the origin at zero.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("aliquot");
    ApiInventoryOperationOriginUpdate origin = origin(100L, millilitres("5"));
    origin.setAmountMode(ApiInventoryOperationAmountMode.EXPLICIT);
    request.setOrigins(List.of(origin));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Aliquots");
    request.setNewSample(newSample);
    originHolds(100L, subSampleHolding("5", ML));
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Aliquots"));

    manager.performOperation(request, user, NONE);

    ArgumentCaptor<QuantityInfo> used = ArgumentCaptor.forClass(QuantityInfo.class);
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), used.capture(), eq(user));
    assertEquals(0, new BigDecimal("5").compareTo(used.getValue().getNumericValue()));
  }

  // --- expectedQuantity (M0 D5) at the edges ---

  @Test
  void anExpectedQuantityOfZeroAgainstAnEmptyOriginIsStillTheEmptyOriginRule() {
    // The caller correctly believes the origin is empty. There is still nothing to operate on.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    ApiInventoryOperationOriginUpdate origin = origin(100L, millilitres("0.6"));
    origin.setExpectedQuantity(millilitres("0"));
    request.setOrigins(List.of(origin));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("0", ML));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.originEmpty",
        rejection.getFieldErrors("origins[0].id").get(0).getCode());
  }

  @Test
  void aMatchingExpectedQuantityEarnsNoPassOnOverRemoval() {
    // The caller read the origin correctly (5 ml) and still asked for 6 ml: that is the ordinary
    // over-removal 400.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOperationType("derive");
    ApiInventoryOperationOriginUpdate origin = origin(100L, millilitres("6"));
    origin.setExpectedQuantity(millilitres("5"));
    request.setOrigins(List.of(origin));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    originHolds(100L, subSampleHolding("5", ML));

    BindException rejection = performExpectingRejection(request);
    assertEquals(
        "errors.inventory.operation.amountTakenExceedsOrigin",
        rejection.getFieldErrors("origins[0].amountTaken").get(0).getCode());
  }

  @Test
  void theOriginsAfterComeBackInRequestOrderRegardlessOfProcessingOrder() throws Exception {
    // A Pool facade over origins 200 then 100: the outcome lists them as the caller gave them
    // (M0 D2), even though the core processes origins ascending by id (see
    // readsOriginsInAscendingIdOrderAndReportsErrorsAtTheirRequestIndex).
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

    InventoryOperationManager.OperationOutcome outcome =
        manager.performOperation(
            "pool",
            List.of(facadeOrigin(200L, millilitres("1")), facadeOrigin(100L, millilitres("1"))),
            creatingInputs("Pooled", 1),
            null,
            null,
            user);

    assertEquals(
        List.of(200L, 100L), outcome.originsAfter().stream().map(ApiSubSample::getId).toList());
  }

  // --- the input rules run before any origin is read (server-built path) ---

  @Test
  void aCountAboveTheDeclaredMaximumIsRejectedBeforeAnyOriginIsRead() {
    BindException rejection =
        assertThrows(
            BindException.class,
            () ->
                manager.performOperation(
                    "aliquot",
                    List.of(facadeOrigin(100L, millilitres("1"))),
                    creatingInputs("Aliquots", 101),
                    null,
                    null,
                    user));

    assertEquals(
        "errors.inventory.operation.inputAboveMaximum",
        rejection.getFieldErrors("count").get(0).getCode());
    verifyNoInteractions(subSampleApiMgr, sampleApiMgr);
  }

  @Test
  void aZeroCreatedAmountIsRejectedBeforeAnyOriginIsRead() {
    java.util.Map<String, Object> inputs = creatingInputs("Aliquots", 1);
    inputs.put("eachAmount", millilitres("0"));

    BindException rejection =
        assertThrows(
            BindException.class,
            () ->
                manager.performOperation(
                    "aliquot",
                    List.of(facadeOrigin(100L, millilitres("1"))),
                    inputs,
                    null,
                    null,
                    user));

    assertEquals(
        "errors.inventory.operation.createdAmountNotPositive",
        rejection.getFieldErrors("eachAmount").get(0).getCode());
    verifyNoInteractions(subSampleApiMgr, sampleApiMgr);
  }

  /**
   * Text inputs land in varchar columns: the built sample's name in EditInfo.name (255) and a text
   * field's content in EditInfo.description (250). Today the only length check is the samples
   * validator run over the BUILT sample inside the transaction, after the origins were read, and it
   * reports {@code newSample.name}, a field the caller never sent; a text field's content has no
   * check at all and fails at the INSERT as a 500 after the locks. The input rule must bound both
   * at the door, on the input's own key, before anything is read.
   *
   * <p>No origin is stubbed on purpose: under the current code the builder runs and dereferences
   * the mock's null origin, which is the observable proof that a read happened.
   */
  @Test
  void aSampleNameLongerThanTheRecordNameLimitIsRejectedOnSampleNameBeforeAnyRead() {
    Throwable thrown =
        assertThrows(
            Throwable.class,
            () ->
                manager.performOperation(
                    "aliquot",
                    List.of(facadeOrigin(100L, millilitres("1"))),
                    creatingInputs("x".repeat(256), 1),
                    null,
                    null,
                    user));

    assertInstanceOf(
        BindException.class,
        thrown,
        "a 256-character name must be a field-scoped 400 before any origin read; anything else"
            + " means the builder ran");
    assertEquals(
        "errors.inventory.operation.inputTooLong",
        ((BindException) thrown).getFieldErrors("sampleName").get(0).getCode());
    verifyNoInteractions(subSampleApiMgr, sampleApiMgr);
  }

  @Test
  void aCryomediumLongerThanTheFieldContentLimitIsRejectedOnCryomediumBeforeAnyRead() {
    java.util.Map<String, Object> inputs = creatingInputs("Frozen", 1);
    inputs.put("cryomedium", "m".repeat(251));
    inputs.put("storageTemp", new ApiQuantityInfo(new BigDecimal("-80"), RSUnitDef.CELSIUS));

    Throwable thrown =
        assertThrows(
            Throwable.class,
            () ->
                manager.performOperation(
                    "cryopreserve",
                    List.of(facadeOrigin(100L, millilitres("1"))),
                    inputs,
                    null,
                    null,
                    user));

    assertInstanceOf(
        BindException.class,
        thrown,
        "251 characters of cryomedium must be a field-scoped 400 before any origin read");
    assertEquals(
        "errors.inventory.operation.inputTooLong",
        ((BindException) thrown).getFieldErrors("cryomedium").get(0).getCode());
    verifyNoInteractions(subSampleApiMgr, sampleApiMgr);
  }
}
