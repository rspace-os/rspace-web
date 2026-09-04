package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The configured Inventory operations: GET /config serves the operation definitions (the backend's
 * {@code operations_config.json}, the single authoritative copy the wizard renders from), and POST
 * runs one. The POST is a single generic, atomic endpoint: it creates one new sample (with its
 * subsamples, custom fields and relation links) and sets the origin subsamples' quantities, all in
 * one transaction. There is no per-operation endpoint or logic; a new operation is a new config
 * entry. See DevDocs/adr/0007.
 */
@RequestMapping("/api/inventory/v1/operations")
public interface InventoryOperationsApi {

  /**
   * The operation definitions, verbatim from the backend's authoritative {@code
   * operations_config.json} (DevDocs/adr/0007). Served as the raw file rather than a
   * re-serialisation because the wizard reads presentational fields (labels, icons, steps) the
   * backend's validation model does not bind.
   */
  @GetMapping(value = "/config", produces = MediaType.APPLICATION_JSON_VALUE)
  String getOperationsConfig();

  // JSON only: the app registers a global YAML converter (WebConfig), and without this consumes
  // guard the endpoint would accept YAML bodies with laxer parsing (duplicate keys, alternate
  // numeric forms) than the JSON contract this API validates against.
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  ApiSampleWithFullSubSamples performOperation(
      @RequestBody @Valid ApiInventoryOperationPost request, BindingResult errors, User user)
      throws BindException;
}
