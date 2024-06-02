/**
 * RSpace Inventory API Access your RSpace Inventory programmatically. All requests require
 * authentication.
 */
package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.InventoryApiPaginationCriteria;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerSearchResult;
import com.researchspace.model.User;
import javax.validation.Valid;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/inventory/v1/workbenches")
public interface WorkbenchApi {

  @GetMapping
  ApiContainerSearchResult getWorkbenchesForUser(
      @Valid InventoryApiPaginationCriteria pgCrit, String ownedBy, BindingResult errors, User user)
      throws BindException;

  @GetMapping(value = "/{id}")
  ApiContainer getWorkbenchById(Long id, Boolean includeContent, User user);
}
