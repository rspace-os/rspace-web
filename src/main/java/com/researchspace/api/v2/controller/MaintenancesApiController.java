package com.researchspace.api.v2.controller;

import com.researchspace.api.v1.controller.ApiController;
import com.researchspace.api.v1.controller.BaseApiController;
import com.researchspace.api.v2.MaintenancesApi;
import com.researchspace.api.v2.model.ApiV2ListResult;
import com.researchspace.api.v2.model.ApiV2Maintenance;
import com.researchspace.api.v2.model.ApiV2PaginationCriteria;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.User;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestAttribute;

@ApiController
public class MaintenancesApiController extends BaseApiController implements MaintenancesApi {

  @Autowired private MaintenanceManager maintenanceManager;

  private final ApiV2PaginationCriteriaValidator paginationValidator =
      new ApiV2PaginationCriteriaValidator();

  @Override
  public ApiV2ListResult<ApiV2Maintenance> listMaintenances(
      @ModelAttribute ApiV2PaginationCriteria pagination,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    inputValidator.validate(pagination, paginationValidator, errors);
    throwBindExceptionIfErrors(errors);

    List<ScheduledMaintenance> all = maintenanceManager.getAllFutureMaintenances();
    return ApiV2ListResult.paginate(all, pagination, ApiV2Maintenance::new);
  }
}
