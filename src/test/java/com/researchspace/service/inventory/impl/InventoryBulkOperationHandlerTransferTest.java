package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.InstrumentsApiController;
import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.model.User;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BindException;

@ExtendWith(MockitoExtension.class)
class InventoryBulkOperationHandlerTransferTest {

  @Mock private InstrumentsApiController instrumentsApiController;
  @InjectMocks private InventoryBulkOperationHandler handler;

  @Test
  void instrumentTransferRunsForTheSubjectAndAuditsTheActor() throws BindException {
    User subject = new User("operated.as");
    User actor = new User("sysadmin");
    ApiInstrument instrument = new ApiInstrument();
    instrument.setId(7L);
    when(instrumentsApiController.changeInstrumentOwner(
            eq(7L), same(instrument), any(), same(subject), same(actor)))
        .thenReturn(instrument);

    ApiInventoryBulkOperationResult result =
        handler.runBulkOperation(
            new InventoryBulkOperationConfig(
                BulkApiOperationType.CHANGE_OWNER, List.of(instrument), true, subject, actor));

    assertEquals(1, result.getSuccessCount());
    verify(instrumentsApiController)
        .changeInstrumentOwner(eq(7L), same(instrument), any(), same(subject), same(actor));
  }

  @Test
  void instrumentTransfersLockInIdOrderButReportInRequestOrder() throws BindException {
    User subject = new User("subject");
    List<ApiInstrument> requested = List.of(instrument(30L), instrument(10L), instrument(20L));
    for (ApiInstrument instrument : requested) {
      when(instrumentsApiController.changeInstrumentOwner(
              eq(instrument.getId()), same(instrument), any(), same(subject), same(subject)))
          .thenReturn(instrument);
    }

    ApiInventoryBulkOperationResult result =
        handler.runBulkOperation(
            new InventoryBulkOperationConfig(
                BulkApiOperationType.CHANGE_OWNER, List.copyOf(requested), true, subject));

    InOrder lockOrder = inOrder(instrumentsApiController);
    for (long id : new long[] {10L, 20L, 30L}) {
      lockOrder
          .verify(instrumentsApiController)
          .changeInstrumentOwner(eq(id), any(), any(), same(subject), same(subject));
    }
    assertEquals(
        List.of(30L, 10L, 20L),
        result.getResults().stream().map(r -> r.getRecord().getId()).toList());
  }

  private static ApiInstrument instrument(Long id) {
    ApiInstrument instrument = new ApiInstrument();
    instrument.setId(id);
    return instrument;
  }
}
