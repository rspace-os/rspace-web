package com.researchspace.api.v2.controller;

import com.researchspace.api.v1.controller.ApiController;
import com.researchspace.api.v1.controller.BaseApiController;
import com.researchspace.api.v1.controller.PublicApi;
import com.researchspace.api.v2.MaintenancesApi;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.api.v2.model.ApiV2Maintenance;
import com.researchspace.api.v2.model.ApiV2PaginationCriteria;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.PaginationCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;

@ApiController
@PublicApi
public class MaintenancesApiController extends BaseApiController implements MaintenancesApi {

  @Autowired private MaintenanceManager maintenanceManager;

  private final ApiV2PaginationCriteriaValidator paginationValidator =
      new ApiV2PaginationCriteriaValidator();

  @Override
  public ApiV2ListResult<ApiV2Maintenance> listMaintenances(
      @ModelAttribute ApiV2PaginationCriteria pagination, BindingResult errors)
      throws BindException {

    inputValidator.validate(pagination, paginationValidator, errors);
    throwBindExceptionIfErrors(errors);

    PaginationCriteria<ScheduledMaintenance> databasePagination = new PaginationCriteria<>();
    databasePagination.setPageNumber((long) pagination.getPage() - 1);
    databasePagination.setResultsPerPage(pagination.getLimit());
    ISearchResults<ScheduledMaintenance> page =
        maintenanceManager.getFutureMaintenances(databasePagination);
    return ApiV2ListResult.of(
        page.getResults().stream().map(ApiV2Maintenance::new).toList(),
        page.getTotalHits(),
        pagination.getLimit(),
        pagination.getPage());
  }
}
