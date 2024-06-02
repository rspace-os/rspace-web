package com.researchspace.service.inventory.csvimport.exception;

import com.researchspace.api.v1.model.ApiInventoryImportResult;
import lombok.Getter;

@Getter
public class InventoryImportPrevalidationException extends InventoryImportException {

  public InventoryImportPrevalidationException(ApiInventoryImportResult result) {
    super("import input not valid", result);
  }
}
