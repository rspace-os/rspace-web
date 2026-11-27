package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.ContainersApiController;
import com.researchspace.api.v1.controller.InstrumentsApiController;
import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiContainer;
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
  @Mock private ContainersApiController containersApiController;
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
  void transfersLockInTypeAndIdOrderButReportInRequestOrder() throws BindException {
    User subject = new User("subject");
    ApiInstrument instrument30 = instrument(30L);
    ApiContainer container50 = container(50L);
    ApiInstrument instrument10 = instrument(10L);
    ApiContainer container20 = container(20L);
    for (ApiInstrument instrument : List.of(instrument30, instrument10)) {
      when(instrumentsApiController.changeInstrumentOwner(
              eq(instrument.getId()), same(instrument), any(), same(subject), same(subject)))
          .thenReturn(instrument);
    }
    for (ApiContainer container : List.of(container50, container20)) {
      when(containersApiController.changeContainerOwner(
              eq(container.getId()), same(container), any(), same(subject), same(subject)))
          .thenReturn(container);
    }

    ApiInventoryBulkOperationResult result =
        handler.runBulkOperation(
            new InventoryBulkOperationConfig(
                BulkApiOperationType.CHANGE_OWNER,
                List.of(instrument30, container50, instrument10, container20),
                true,
                subject));

    // CONTAINER precedes INSTRUMENT in ApiInventoryRecordType
    InOrder lockOrder = inOrder(containersApiController, instrumentsApiController);
    lockOrder
        .verify(containersApiController)
        .changeContainerOwner(eq(20L), any(), any(), same(subject), same(subject));
    lockOrder
        .verify(containersApiController)
        .changeContainerOwner(eq(50L), any(), any(), same(subject), same(subject));
    lockOrder
        .verify(instrumentsApiController)
        .changeInstrumentOwner(eq(10L), any(), any(), same(subject), same(subject));
    lockOrder
        .verify(instrumentsApiController)
        .changeInstrumentOwner(eq(30L), any(), any(), same(subject), same(subject));
    assertEquals(
        List.of(30L, 50L, 10L, 20L),
        result.getResults().stream().map(r -> r.getRecord().getId()).toList());
  }

  private static ApiContainer container(Long id) {
    ApiContainer container = new ApiContainer();
    container.setId(id);
    return container;
  }

  private static ApiInstrument instrument(Long id) {
    ApiInstrument instrument = new ApiInstrument();
    instrument.setId(id);
    return instrument;
  }
}
