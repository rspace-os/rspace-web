package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryOperationOriginUpdate;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
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
}
