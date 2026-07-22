package com.researchspace.service.inventory.csvimport.exception;

import static com.researchspace.service.ListFormatUtils.formatList;

import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.apiutils.ApiError;
import lombok.Getter;

@Getter
public class InventoryImportException extends RuntimeException {

  ApiInventoryImportResult result;

  public InventoryImportException(String message, ApiInventoryImportResult result) {
    super(message);
    this.result = result;
  }

  public InventoryImportException(ApiError apiError, ApiInventoryImportResult result) {
    this(formatList(apiError.getErrors()), result);
  }
}
