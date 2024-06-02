/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryApiPaginationCriteria;
import com.researchspace.api.v1.controller.InventoryApiSearchConfig;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.model.User;
import javax.validation.Valid;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/inventory/v1/search")
public interface InventorySearchApi {

  @GetMapping
  ApiInventorySearchResult searchInventoryRecords(
      @Valid InventoryApiPaginationCriteria pgCrit,
      @Valid InventoryApiSearchConfig srchConfig,
      BindingResult errors,
      User user)
      throws BindException;
}
