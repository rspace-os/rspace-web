package com.researchspace.service.inventory.operations;

import static com.researchspace.service.inventory.operations.OperationTestFixtures.millilitres;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.originState;
import static com.researchspace.service.inventory.operations.OperationTestFixtures.requestOrigin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class DestroyOperationTest {

  private static final DestroyOperation DESTROY = new DestroyOperation();

  private static ApiInventoryOperationRequests.Destroy request() {
    ApiInventoryOperationRequests.Destroy request = new ApiInventoryOperationRequests.Destroy();
    request.setOrigin(requestOrigin(100, null));
    return request;
  }

  @Test
  void takesTheOriginsWholeLiveQuantityAndCreatesNothing() {
    ApiInventoryOperationPost built =
        DESTROY.build(
            request(),
            List.of(originState(100, "7.5")),
            OperationTestFixtures.KEYS,
            LocalDate.parse("2026-08-20"));

    assertNull(built.getNewSample(), "Destroy creates no sample");
    assertEquals(1, built.getOrigins().size());
    assertEquals(100L, built.getOrigins().get(0).getId());
    assertEquals(millilitres("7.5"), built.getOrigins().get(0).getAmountTaken());
    assertTrue(built.isEmptiesOrigin());
  }

  @Test
  void recordsTheCallersDateOnTheOriginItself() {
    ApiInventoryOperationPost built =
        DESTROY.build(
            request(),
            List.of(originState(100, "7.5")),
            OperationTestFixtures.KEYS,
            LocalDate.parse("2026-08-20"));

    List<ApiExtraField> originFields = built.getOrigins().get(0).getExtraFields();
    assertEquals(1, originFields.size());
    ApiExtraField disposed = originFields.get(0);
    assertEquals("operations.destroy.disposedField", disposed.getOperationFieldKey());
    assertEquals("2026-08-20", disposed.getContent());
    assertEquals(ApiExtraField.ExtraFieldTypeEnum.TEXT, disposed.getType());
    assertTrue(disposed.isNewFieldRequest());
  }

  @Test
  void takesNoChosenAmount() {
    ApiInventoryOperationRequests.Destroy request = request();
    assertTrue(DESTROY.emptiesOrigin(request));
    assertEquals(false, DESTROY.takesAmount(request));
    assertEquals("destroy", DESTROY.key());
  }
}
