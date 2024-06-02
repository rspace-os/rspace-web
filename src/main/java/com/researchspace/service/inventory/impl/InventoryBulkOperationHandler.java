package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.controller.ApiControllerAdvice;
import com.researchspace.api.v1.controller.ContainersApiController;
import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.controller.SampleTemplatesApiController;
import com.researchspace.api.v1.controller.SamplesApiController;
import com.researchspace.api.v1.controller.SubSamplesApiController;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiSampleInfo;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryMoveHelper;
import java.util.List;
import java.util.function.BiFunction;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.SmartValidator;

/** Non-transactional component handling bulk operations. */
@Component("inventoryBulkOperationHandler")
public class InventoryBulkOperationHandler {

  private @Autowired SamplesApiController samplesApiController;
  private @Autowired ContainersApiController containersApiController;
  private @Autowired SubSamplesApiController subSamplesApiController;
  private @Autowired SampleTemplatesApiController templatesApiController;

  private @Autowired InventoryMoveHelper inventoryMoveHelper;

  private @Autowired SmartValidator mvcValidator;
  private @Autowired ApiControllerAdvice apiControllerAdvice;

  @Getter
  public static class InventoryBulkOperationException extends RuntimeException {
    private static final long serialVersionUID = -703442594194862182L;

    ApiInventoryBulkOperationResult partialResult;

    public InventoryBulkOperationException(
        ApiError apiError, ApiInventoryBulkOperationResult partialResult) {
      super(StringUtils.join(apiError.getErrors(), "; "));
      this.partialResult = partialResult;
    }

    public InventoryBulkOperationException(
        String message, ApiInventoryBulkOperationResult partialResult) {
      super(message);
      this.partialResult = partialResult;
    }
  }

  /**
   * Executes bulk operation by iterating over records in request and delegating each record to
   * correct inventory controller and action. The aim is for bulk operation to replicate a series of
   * individual calls to inventory endpoints.
   *
   * <p>Depending on request.rollbackOnError flag the bulk operation either stops and throws an
   * exception when encountering the error, or continues with errors being collected in
   * result.errors.
   *
   * @param bulkApiRequest request configuration
   * @result processed records and encountered errors
   */
  public ApiInventoryBulkOperationResult runBulkOperation(
      InventoryBulkOperationConfig bulkOpConfig) {
    BulkApiOperationType operationType = bulkOpConfig.getOperationType();
    switch (operationType) {
      case CREATE:
        return runOperationForEachRecordFromBulkList(bulkOpConfig, this::createInventoryRecord);
      case UPDATE:
        return runOperationForEachRecordFromBulkList(bulkOpConfig, this::updateInventoryRecord);
      case DELETE:
        return runOperationForEachRecordFromBulkList(bulkOpConfig, this::deleteInventoryRecord);
      case RESTORE:
        return runOperationForEachRecordFromBulkList(bulkOpConfig, this::restoreInventoryRecord);
      case DUPLICATE:
        return runOperationForEachRecordFromBulkList(bulkOpConfig, this::duplicateInventoryRecord);
      case UPDATE_TO_LATEST_TEMPLATE_VERSION:
        return runOperationForEachRecordFromBulkList(
            bulkOpConfig, this::updateToLatestTemplateVersion);
      case MOVE:
        return runOperationForWholeBulkList(bulkOpConfig, this::moveAllRecordsFromList);
      case CHANGE_OWNER:
        return runOperationForEachRecordFromBulkList(
            bulkOpConfig, this::changeInventoryRecordOwner);
      default:
        throw new IllegalStateException("unhandled operation for type: " + operationType);
    }
  }

  private ApiInventoryBulkOperationResult runOperationForEachRecordFromBulkList(
      InventoryBulkOperationConfig bulkOpConfig,
      BiFunction<ApiInventoryRecordInfo, User, ApiInventoryRecordInfo> operation) {

    ApiInventoryBulkOperationResult result = new ApiInventoryBulkOperationResult();
    List<ApiInventoryRecordInfo> records = bulkOpConfig.getRecords();
    User user = bulkOpConfig.getUser();
    boolean onErrorStopWithException = bulkOpConfig.isOnErrorStopWithException();
    if (records != null) {
      for (ApiInventoryRecordInfo recInfo : records) {
        try {
          if (!onErrorStopWithException) {
            validateRecordWithMvcValidator(recInfo);
          }
          ApiInventoryRecordInfo operationResult = operation.apply(recInfo, user);
          if (operationResult != null) {
            result.addSuccessResult(operationResult);
          }
        } catch (Exception e) {
          ApiError error = convertExceptionToApiError(e);
          result.addError(error);
          if (onErrorStopWithException) {
            throw new InventoryBulkOperationException(error, result);
          }
        }
      }
    }
    return result;
  }

  private void validateRecordWithMvcValidator(ApiInventoryRecordInfo recInfo) {
    BindException errors = new BindException(recInfo, "record");
    mvcValidator.validate(recInfo, errors);
    if (errors.hasErrors()) {
      throw new IllegalArgumentException("Validation failed for provided record", errors);
    }
  }

  public ApiError convertExceptionToApiError(Exception e) {
    Throwable cause = e.getCause() == null ? e : e.getCause();
    if (cause instanceof BindException) {
      return apiControllerAdvice.getApiErrorFromBindException((BindException) cause);
    }
    return new ApiError(
        HttpStatus.BAD_REQUEST,
        ApiErrorCodes.INVALID_FIELD.getCode(),
        "Errors detected : 1",
        e.getMessage());
  }

  private ApiInventoryRecordInfo createInventoryRecord(ApiInventoryRecordInfo recInfo, User user) {
    ApiInventoryRecordType recInfoType = recInfo.getType();
    BindException errors = new BindException(recInfo, "record");
    try {
      switch (recInfoType) {
        case SAMPLE:
          return samplesApiController.createNewSample(
              (ApiSampleWithFullSubSamples) recInfo, errors, user);
        case CONTAINER:
          return containersApiController.createNewContainer((ApiContainer) recInfo, errors, user);
        default:
          throw new IllegalArgumentException(
              "bulk creation only supports record with "
                  + "'type' field set to 'SAMPLE' or 'CONTAINER', was: "
                  + recInfoType);
      }
    } catch (BindException be) {
      throw new IllegalArgumentException(be);
    }
  }

  private ApiInventoryRecordInfo updateInventoryRecord(ApiInventoryRecordInfo recInfo, User user) {
    ApiInventoryRecordType recInfoType = recInfo.getType();
    BindException errors = new BindException(recInfo, "record");
    try {
      switch (recInfoType) {
        case SAMPLE:
          return samplesApiController.updateSample(
              recInfo.getId(), (ApiSampleWithFullSubSamples) recInfo, errors, user);
        case SAMPLE_TEMPLATE:
          return templatesApiController.updateSampleTemplate(
              recInfo.getId(), (ApiSampleTemplate) recInfo, errors, user);
        case CONTAINER:
          return containersApiController.updateContainer(
              recInfo.getId(), (ApiContainer) recInfo, errors, user);
        case SUBSAMPLE:
          return subSamplesApiController.updateSubSample(
              recInfo.getId(), (ApiSubSample) recInfo, errors, user);
        default:
          throw new IllegalArgumentException(
              "bulk update doesn't support records of type: " + recInfo.getType());
      }
    } catch (BindException be) {
      throw new IllegalArgumentException(be);
    }
  }

  private ApiInventoryRecordInfo deleteInventoryRecord(ApiInventoryRecordInfo recInfo, User user) {
    ApiInventoryRecordType recInfoType = recInfo.getType();
    switch (recInfoType) {
      case SAMPLE:
        return samplesApiController.deleteSample(
            recInfo.getId(), ((ApiSampleInfo) recInfo).isForceDelete(), user);
      case SAMPLE_TEMPLATE:
        return templatesApiController.deleteSampleTemplate(recInfo.getId(), user);
      case CONTAINER:
        return containersApiController.deleteContainer(recInfo.getId(), user);
      case SUBSAMPLE:
        return subSamplesApiController.deleteSubSample(recInfo.getId(), user);
      default:
        throw new IllegalArgumentException(
            "bulk delete doesn't support records of type: " + recInfo.getType());
    }
  }

  private ApiInventoryRecordInfo restoreInventoryRecord(ApiInventoryRecordInfo recInfo, User user) {
    ApiInventoryRecordType recInfoType = recInfo.getType();
    switch (recInfoType) {
      case SAMPLE:
        return samplesApiController.restoreDeletedSample(recInfo.getId(), user);
      case SAMPLE_TEMPLATE:
        return templatesApiController.restoreDeletedSampleTemplate(recInfo.getId(), user);
      case CONTAINER:
        return containersApiController.restoreDeletedContainer(recInfo.getId(), user);
      case SUBSAMPLE:
        return subSamplesApiController.restoreDeletedSubSample(recInfo.getId(), user);
      default:
        throw new IllegalArgumentException(
            "bulk restore doesn't support records of type: " + recInfo.getType());
    }
  }

  private ApiInventoryRecordInfo duplicateInventoryRecord(
      ApiInventoryRecordInfo recInfo, User user) {
    ApiInventoryRecordType recInfoType = recInfo.getType();
    switch (recInfoType) {
      case SAMPLE:
        return samplesApiController.duplicate(recInfo.getId(), user);
      case SAMPLE_TEMPLATE:
        return templatesApiController.duplicate(recInfo.getId(), user);
      case CONTAINER:
        return containersApiController.duplicate(recInfo.getId(), user);
      case SUBSAMPLE:
        return subSamplesApiController.duplicate(recInfo.getId(), user);
      default:
        throw new IllegalArgumentException(
            "bulk duplicate doesn't support records of type: " + recInfo.getType());
    }
  }

  private ApiInventoryRecordInfo updateToLatestTemplateVersion(
      ApiInventoryRecordInfo recInfo, User user) {
    ApiInventoryRecordType recInfoType = recInfo.getType();
    switch (recInfoType) {
      case SAMPLE:
        return samplesApiController.updateToLatestTemplateVersion(recInfo.getId(), user);
      default:
        throw new IllegalArgumentException(
            "update to latest template doesn't support records of type: " + recInfo.getType());
    }
  }

  private ApiInventoryRecordInfo changeInventoryRecordOwner(
      ApiInventoryRecordInfo recInfo, User user) {
    ApiInventoryRecordType recInfoType = recInfo.getType();
    BindException errors = new BindException(recInfo, "record");

    try {
      switch (recInfoType) {
        case SAMPLE:
          return samplesApiController.changeSampleOwner(
              recInfo.getId(), (ApiSampleWithFullSubSamples) recInfo, errors, user);
        case SAMPLE_TEMPLATE:
          return templatesApiController.changeSampleTemplateOwner(
              recInfo.getId(), (ApiSampleTemplate) recInfo, errors, user);
        case CONTAINER:
          return containersApiController.changeContainerOwner(
              recInfo.getId(), (ApiContainer) recInfo, errors, user);
        default:
          throw new IllegalArgumentException(
              "bulk owner change doesn't support records of type: " + recInfo.getType());
      }
    } catch (BindException be) {
      throw new IllegalArgumentException(be);
    }
  }

  private ApiInventoryBulkOperationResult runOperationForWholeBulkList(
      InventoryBulkOperationConfig bulkOpConfig,
      BiFunction<List<ApiInventoryRecordInfo>, User, List<ApiInventoryRecordInfo>> operation) {

    boolean onErrorStopWithException = bulkOpConfig.isOnErrorStopWithException();
    if (!onErrorStopWithException) {
      throw new IllegalArgumentException(
          "operationType: " + bulkOpConfig.getOperationType() + " doesn't allow rollback option");
    }

    ApiInventoryBulkOperationResult result = new ApiInventoryBulkOperationResult();
    List<ApiInventoryRecordInfo> records = bulkOpConfig.getRecords();
    User user = bulkOpConfig.getUser();
    if (records != null) {
      try {
        List<ApiInventoryRecordInfo> operationResult = operation.apply(records, user);
        if (operationResult != null) {
          result.addAllSuccessResult(operationResult);
        }
      } catch (Exception e) {
        ApiError error = convertExceptionToApiError(e);
        result.addError(error);
        throw new InventoryBulkOperationException(error, result);
      }
    }
    return result;
  }

  private List<ApiInventoryRecordInfo> moveAllRecordsFromList(
      List<ApiInventoryRecordInfo> recordsToMove, User user) {
    return inventoryMoveHelper.runBulkRecordMove(recordsToMove, user);
  }
}
