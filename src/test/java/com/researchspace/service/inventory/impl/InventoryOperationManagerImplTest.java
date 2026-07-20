package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
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
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
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

@ExtendWith(MockitoExtension.class)
class InventoryOperationManagerImplTest {

  @Mock private SampleApiManager sampleApiMgr;
  @Mock private SubSampleApiManager subSampleApiMgr;

  private InventoryOperationManagerImpl manager;
  private final User user = new User("anyUser");

  private static ApiInventoryOperationOriginUpdate origin(Long id, ApiQuantityInfo amountTaken) {
    ApiInventoryOperationOriginUpdate origin = new ApiInventoryOperationOriginUpdate();
    origin.setId(id);
    origin.setAmountTaken(amountTaken);
    return origin;
  }

  @BeforeEach
  void setUp() {
    manager = new InventoryOperationManagerImpl();
    ReflectionTestUtils.setField(manager, "sampleApiMgr", sampleApiMgr);
    ReflectionTestUtils.setField(manager, "subSampleApiMgr", subSampleApiMgr);
  }

  @Test
  void performOperationCreatesNewSampleAndReducesOriginByAmountTaken() {
    ApiQuantityInfo amountTaken = new ApiQuantityInfo(new BigDecimal("0.6"), 3);
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, amountTaken)));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);

    ApiSampleWithFullSubSamples created = new ApiSampleWithFullSubSamples("Derived material");
    when(sampleApiMgr.createNewApiSample(newSample, user)).thenReturn(created);

    ApiSampleWithFullSubSamples result = manager.performOperation(request, user);

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
  void decrementsOriginBeforeCreatingTheNewSample() {
    // The new subsample must end up most-recently-modified, so the origin is decremented (which
    // stamps its modification date) BEFORE the new sample + subsample are created (adr/0005).
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    manager.performOperation(request, user);

    InOrder inOrder = inOrder(subSampleApiMgr, sampleApiMgr);
    inOrder.verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    inOrder.verify(sampleApiMgr).createNewApiSample(newSample, user);
  }

  @Test
  void abortsBeforeAnyMutationWhenAnOriginIsNotEditable() {
    // Validate-before-mutate (adr/0001): if the permission check on any origin fails, nothing must
    // be written - neither the new sample created nor any origin reduced.
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3))));
    request.setNewSample(new ApiSampleWithFullSubSamples("Derived material"));
    doThrow(new RuntimeException("no permission"))
        .when(subSampleApiMgr)
        .assertUserCanEditSubSample(100L, user);

    assertThrows(RuntimeException.class, () -> manager.performOperation(request, user));

    verify(sampleApiMgr, never()).createNewApiSample(any(), any());
    verify(subSampleApiMgr, never()).registerApiSubSampleUsage(any(), any(), any());
  }

  @Test
  void terminalOperationAddsOriginFieldsAndCreatesNoSample() {
    // Destroy (noOutput): no new sample is sent, and the operation adds a custom field to the
    // origin
    // itself. The manager must create no sample, return null, and apply the origin's extra fields
    // via
    // the subsample-edit path (adr/0008).
    ApiExtraField disposed = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    disposed.setName("disposed");
    disposed.setContent("2026-07-20");
    disposed.setNewFieldRequest(true);
    ApiInventoryOperationOriginUpdate origin =
        origin(100L, new ApiQuantityInfo(new BigDecimal("2"), 3));
    origin.setExtraFields(List.of(disposed));
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(List.of(origin));
    request.setNewSample(null);

    ApiSampleWithFullSubSamples result = manager.performOperation(request, user);

    assertNull(result);
    verify(sampleApiMgr, never()).createNewApiSample(any(), any());
    verify(subSampleApiMgr).registerApiSubSampleUsage(eq(100L), any(), eq(user));
    ArgumentCaptor<ApiSubSample> update = ArgumentCaptor.forClass(ApiSubSample.class);
    verify(subSampleApiMgr).updateApiSubSample(update.capture(), eq(user));
    assertEquals(Long.valueOf(100L), update.getValue().getId());
    assertEquals("disposed", update.getValue().getExtraFields().get(0).getName());
  }

  @Test
  void reducesEveryOriginByItsOwnAmountTaken() {
    ApiInventoryOperationPost request = new ApiInventoryOperationPost();
    request.setOrigins(
        List.of(
            origin(100L, new ApiQuantityInfo(new BigDecimal("0.6"), 3)),
            origin(200L, new ApiQuantityInfo(new BigDecimal("1.5"), 3))));
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples("Derived material");
    request.setNewSample(newSample);
    when(sampleApiMgr.createNewApiSample(newSample, user))
        .thenReturn(new ApiSampleWithFullSubSamples("Derived material"));

    manager.performOperation(request, user);

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
}
