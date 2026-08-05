package com.researchspace.maintenance.api.v2;

import com.researchspace.api.v2.controller.ApiV2CrudController;
import com.researchspace.api.v2.controller.ApiV2Problem;
import com.researchspace.maintenance.service.MaintenanceOperationException;
import com.researchspace.service.MessageSourceUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/** Maps maintenance collection failures to public REST API v2 errors. */
@ControllerAdvice(assignableTypes = ApiV2CrudController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MaintenanceApiV2ControllerAdvice {

  private final MessageSourceUtils messages;

  public MaintenanceApiV2ControllerAdvice(MessageSourceUtils messages) {
    this.messages = messages;
  }

  @ExceptionHandler(MaintenanceOperationException.class)
  public ResponseEntity<ApiV2Problem> handleMaintenanceOperation() {
    String key = "errors.api.v2.maintenance.window";
    String detail = messages.getMessage(key);
    return ApiV2Problem.response(HttpStatus.BAD_REQUEST, detail, key, detail);
  }
}
