package com.researchspace.api.v1.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryBulkOperationApiManager;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler.InventoryBulkOperationException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.validation.SmartValidator;

/**
 * The external PIDINST push has to happen out here, after a single-transaction batch has committed.
 * The controller methods the batch re-enters push from inside its transaction, which
 * InventoryIdentifierExternalUpdateService declines, and the web interface transfers instruments
 * only this way - Search.transferRecords sends rollbackOnError true. Without a push at this seam
 * the transfer half of RSDEV-1251 never reached a provider from the UI.
 */
@ExtendWith(MockitoExtension.class)
public class InventoryBulkOperationsApiControllerPushTest {

  @Mock private InventoryBulkOperationApiManager bulkOperationManager;
  @Mock private InventoryBulkOperationHandler bulkOperationHandler;
  @Mock private InstrumentsApiController instrumentsApiController;
  @Mock private SmartValidator mvcValidator;
  @Mock private ApiControllerAdvice apiControllerAdvice;

  @InjectMocks private InventoryBulkOperationsApiController controller;

  private final User user = new User("bulk.user");
  private final ApiInstrument instrument = instrument(11L);

  private static ApiInstrument instrument(Long id) {
    ApiInstrument apiInstrument = new ApiInstrument();
    apiInstrument.setId(id);
    return apiInstrument;
  }

  private ApiInventoryBulkOperationResult resultOf(ApiInventoryRecordInfo... records) {
    ApiInventoryBulkOperationResult result = new ApiInventoryBulkOperationResult();
    result.addAllSuccessResult(List.of(records));
    return result;
  }

  private ApiInventoryBulkOperationResult run(BulkApiOperationType operationType)
      throws BindException {
    ApiInventoryBulkOperationPost request = new ApiInventoryBulkOperationPost();
    request.setOperationType(operationType);
    request.setRecords(List.of(instrument));
    request.setRollbackOnError(true);
    return controller.executeBulkOperation(
        request, new BeanPropertyBindingResult(request, "request"), user);
  }

  @Test
  public void aCommittedTransferPushesEachInstrumentItChanged() throws BindException {
    when(bulkOperationManager.runBulkOperation(any(InventoryBulkOperationConfig.class)))
        .thenReturn(resultOf(instrument, new ApiSampleWithFullSubSamples("a sample")));

    run(BulkApiOperationType.CHANGE_OWNER);

    verify(instrumentsApiController).pushExternalMetadataUpdates(instrument, user);
  }

  @Test
  public void anOrdinaryBulkEditPushesToo() throws BindException {
    when(bulkOperationManager.runBulkOperation(any(InventoryBulkOperationConfig.class)))
        .thenReturn(resultOf(instrument));

    run(BulkApiOperationType.UPDATE);

    verify(instrumentsApiController).pushExternalMetadataUpdates(instrument, user);
  }

  @Test
  public void aRevertedBatchPushesNothing() throws BindException {
    // the provider must not be left holding an owner the rollback took away again
    when(bulkOperationManager.runBulkOperation(any(InventoryBulkOperationConfig.class)))
        .thenThrow(new InventoryBulkOperationException("one record failed", resultOf(instrument)));

    run(BulkApiOperationType.CHANGE_OWNER);

    verify(instrumentsApiController, never()).pushExternalMetadataUpdates(any(), any());
  }

  @Test
  public void anOperationThatChangesNoProviderMetadataPushesNothing() throws BindException {
    when(bulkOperationManager.runBulkOperation(any(InventoryBulkOperationConfig.class)))
        .thenReturn(resultOf(instrument));

    run(BulkApiOperationType.RESTORE);

    verify(instrumentsApiController, never()).pushExternalMetadataUpdates(any(), any());
  }
}
