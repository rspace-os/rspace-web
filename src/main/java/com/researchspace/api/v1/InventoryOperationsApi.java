package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInventoryOperationPost;
import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiInventoryOperationResult;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * The configured Inventory operations. GET /config serves the operation definitions (the backend's
 * {@code operations_config.json}, the single authoritative copy the wizard renders from). Each
 * operation runs atomically: from the values the client typed and the operation definition the
 * server builds one new sample (with its subsamples, custom fields and relation links) and sets the
 * origin subsamples' quantities, all in one transaction. See DevDocs/adr/0007.
 *
 * <p>Two ways in. The generic {@code POST /operations} is the wizard's, internal and unpublished:
 * the operation is named in the body and the inputs travel as a map. The seven typed {@code POST
 * /operations/<key>} endpoints are the public API (DevDocs/adr/0007 M6, shapes frozen in
 * DevDocs/adr/0007): one request body per operation whose fields are that definition's input keys,
 * origins by global id, and one response envelope for all seven, the created sample (null for
 * Destroy) with each origin as it stands afterwards. Both run the same validation and the same
 * transactional core; a new operation is still a new config entry, plus one typed shape here once
 * it is public.
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

  // The seven typed endpoints. A creating operation answers 201 with a Location header pointing at
  // the new sample; Destroy answers 200 (it creates nothing). Same JSON-only guard as above.

  @PostMapping(value = "/aliquot", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ApiInventoryOperationResult> aliquot(
      @RequestBody @Valid ApiInventoryOperationRequests.Aliquot request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/passage", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ApiInventoryOperationResult> passage(
      @RequestBody @Valid ApiInventoryOperationRequests.Passage request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/pool", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ApiInventoryOperationResult> pool(
      @RequestBody @Valid ApiInventoryOperationRequests.Pool request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/derive", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ApiInventoryOperationResult> derive(
      @RequestBody @Valid ApiInventoryOperationRequests.Derive request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/cryopreserve", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ApiInventoryOperationResult> cryopreserve(
      @RequestBody @Valid ApiInventoryOperationRequests.Cryopreserve request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/revive", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ApiInventoryOperationResult> revive(
      @RequestBody @Valid ApiInventoryOperationRequests.Revive request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/destroy", consumes = MediaType.APPLICATION_JSON_VALUE)
  ResponseEntity<ApiInventoryOperationResult> destroy(
      @RequestBody @Valid ApiInventoryOperationRequests.Destroy request,
      BindingResult errors,
      User user)
      throws BindException;
}
