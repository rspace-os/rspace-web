package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.model.User;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InventoryBulkOperationHandlerGuardTest {

  @Test
  void undeletableSampleInBulkDeleteProducesConstraintError() {
    InventoryBulkOperationHandler handler = new InventoryBulkOperationHandler();

    ApiSample undeletable = mock(ApiSample.class);
    when(undeletable.getCanBeDeleted()).thenReturn(false);

    ApiInventoryRecordInfo inputRecord = mock(ApiInventoryRecordInfo.class);
    InventoryBulkOperationConfig config = mock(InventoryBulkOperationConfig.class);
    when(config.getRecords()).thenReturn(List.of(inputRecord));
    when(config.getUser()).thenReturn(new User("u"));
    when(config.isOnErrorStopWithException()).thenReturn(true);

    BiFunction<ApiInventoryRecordInfo, User, ApiInventoryRecordInfo> op =
        (rec, user) -> undeletable;

    ApiInventoryBulkOperationResult result =
        ReflectionTestUtils.invokeMethod(
            handler, "runOperationForEachRecordFromBulkList", config, op);

    assertEquals(1, result.getErrorCount(), "guard should record one constraint error");
    assertEquals(0, result.getSuccessCount(), "undeletable sample must not be a success");
  }
}
