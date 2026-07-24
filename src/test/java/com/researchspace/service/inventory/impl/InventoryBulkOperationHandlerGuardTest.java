package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.model.User;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InventoryBulkOperationHandlerGuardTest {

  private final BiFunction<ApiInventoryRecordInfo, User, ApiInventoryRecordInfo>
      returnsUndeletable =
          (rec, user) -> {
            ApiSample undeletable = mock(ApiSample.class);
            lenient().when(undeletable.getCanBeDeleted()).thenReturn(false);
            return undeletable;
          };

  private InventoryBulkOperationConfig configFor(BulkApiOperationType operationType) {
    InventoryBulkOperationConfig config = mock(InventoryBulkOperationConfig.class);
    when(config.getRecords()).thenReturn(List.of(mock(ApiInventoryRecordInfo.class)));
    when(config.getUser()).thenReturn(new User("u"));
    when(config.isOnErrorStopWithException()).thenReturn(true);
    when(config.getOperationType()).thenReturn(operationType);
    return config;
  }

  private ApiInventoryBulkOperationResult run(BulkApiOperationType operationType) {
    InventoryBulkOperationHandler handler = new InventoryBulkOperationHandler();
    ReflectionTestUtils.setField(
        handler, "messages", new MessageSourceUtils(new JsonMessageSource()));
    return ReflectionTestUtils.invokeMethod(
        handler,
        "runOperationForEachRecordFromBulkList",
        configFor(operationType),
        returnsUndeletable);
  }

  @Test
  void undeletableSampleInBulkDeleteProducesConstraintError() {
    ApiInventoryBulkOperationResult result = run(BulkApiOperationType.DELETE);

    assertEquals(1, result.getErrorCount(), "guard should record one constraint error");
    assertEquals(0, result.getSuccessCount(), "undeletable sample must not be a success");
  }

  @Test
  void undeletableSampleInBulkUpdateIsNotFailedByDeleteConstraint() {
    ApiInventoryBulkOperationResult result = run(BulkApiOperationType.UPDATE);

    assertEquals(
        0, result.getErrorCount(), "delete-only constraint must not fail a successful update");
    assertEquals(
        1, result.getSuccessCount(), "an already-committed update must be reported as a success");
  }
}
