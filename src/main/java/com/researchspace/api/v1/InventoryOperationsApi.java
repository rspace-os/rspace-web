package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Runs a configured Inventory operation (see the frontend {@code operations_config.json}). A single
 * generic, atomic endpoint: it creates one new sample (with its subsamples, custom fields and
 * relation links) and sets the origin subsamples' quantities, all in one transaction. There is no
 * per-operation endpoint or logic; a new operation is a new frontend config entry. See adr/0001.
 */
@RequestMapping("/api/inventory/v1/operations")
public interface InventoryOperationsApi {

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ApiSampleWithFullSubSamples performOperation(
      @RequestBody @Valid ApiInventoryOperationPost request, BindingResult errors, User user)
      throws BindException;
}
