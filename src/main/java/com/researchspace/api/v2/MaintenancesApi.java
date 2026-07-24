package com.researchspace.api.v2;

import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.api.v2.model.ApiV2Maintenance;
import com.researchspace.api.v2.model.ApiV2PaginationCriteria;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v2/maintenances")
public interface MaintenancesApi {

  /** Lists active or future maintenance windows in the standard v2 paginated envelope. */
  @GetMapping
  ApiV2ListResult<ApiV2Maintenance> listMaintenances(
      @ModelAttribute ApiV2PaginationCriteria pagination, BindingResult errors)
      throws BindException;
}
