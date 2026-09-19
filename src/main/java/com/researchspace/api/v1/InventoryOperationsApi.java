package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiInventoryOperationResult;
import com.researchspace.model.User;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * The Inventory operations. Each runs atomically: from the values the client sent the server builds
 * one new sample (with its subsamples, custom fields and relation links) and sets the origin
 * subsamples' quantities, all in one transaction.
 *
 * <p>One endpoint per operation, with a request body whose fields are that operation's own, origins
 * by global id, and one response envelope for all seven: the created sample (null for Destroy) with
 * each origin as it stands afterwards. A new operation is a new Java class plus one shape here.
 */
@RequestMapping("/api/inventory/v1/operations")
public interface InventoryOperationsApi {

  // A creating operation answers 201 with a Location header pointing at the new sample; Destroy
  // answers 200 (it creates nothing).
  //
  // JSON only: the app registers a global YAML converter (WebConfig), and without this consumes
  // guard the endpoints would accept YAML bodies with laxer parsing (duplicate keys, alternate
  // numeric forms) than the JSON contract this API validates against.

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
