package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInventoryOperationRequests;
import com.researchspace.api.v1.model.ApiInventoryOperationResult;
import com.researchspace.model.User;
import jakarta.validation.Valid;
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
 */
@RequestMapping("/api/inventory/v1/operations")
public interface InventoryOperationsApi {

  // A creating operation answers 201 with a Location header pointing at the new sample; Destroy
  // answers 200 (it creates nothing).

  @PostMapping(value = "/aliquot")
  ResponseEntity<ApiInventoryOperationResult> aliquot(
      @RequestBody @Valid ApiInventoryOperationRequests.Aliquot request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/passage")
  ResponseEntity<ApiInventoryOperationResult> passage(
      @RequestBody @Valid ApiInventoryOperationRequests.Passage request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/pool")
  ResponseEntity<ApiInventoryOperationResult> pool(
      @RequestBody @Valid ApiInventoryOperationRequests.Pool request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/derive")
  ResponseEntity<ApiInventoryOperationResult> derive(
      @RequestBody @Valid ApiInventoryOperationRequests.Derive request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/cryopreserve")
  ResponseEntity<ApiInventoryOperationResult> cryopreserve(
      @RequestBody @Valid ApiInventoryOperationRequests.Cryopreserve request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/revive")
  ResponseEntity<ApiInventoryOperationResult> revive(
      @RequestBody @Valid ApiInventoryOperationRequests.Revive request,
      BindingResult errors,
      User user)
      throws BindException;

  @PostMapping(value = "/destroy")
  ResponseEntity<ApiInventoryOperationResult> destroy(
      @RequestBody @Valid ApiInventoryOperationRequests.Destroy request,
      BindingResult errors,
      User user)
      throws BindException;
}
