package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.ActivityApi;
import com.researchspace.api.v1.model.ApiActivity;
import com.researchspace.core.util.DateRangeRestrictor;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.service.audit.search.AuditTrailHandler;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import javax.validation.Valid;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;

/** Only active in 'run' mode while dev */
@ApiController
public class ActivityApiController extends BaseApiController implements ActivityApi {

  private @Autowired AuditTrailHandler auditTrailHandler;
  private DateRangeRestrictor dateRangeRestrictor = new DateRangeRestrictor();

  private final Duration defaultMaxAuditSearchRange = Duration.of(183, ChronoUnit.DAYS);

  private Duration maxAuditSearchRange = defaultMaxAuditSearchRange;

  public ApiActivitySearchResult search(
      @Valid ActivityApiPaginationCriteria apiPgCrit,
      @Valid ApiActivitySrchConfig apiSrchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    // set this default if not
    if (StringUtils.isBlank(apiPgCrit.getOrderBy())
        || !apiPgCrit.getOrderBy().trim().startsWith("date")) {
      apiPgCrit.setOrderBy("date desc");
    }
    // additional validation
    inputValidator.validate(apiSrchConfig, new ApiActivitySrchConfigValidator(), errors);
    throwBindExceptionIfErrors(errors);
    adjustDateRangeLimits(apiSrchConfig);

    PaginationCriteria<AuditTrailSearchResult> internalPgCrit =
        getPaginationCriteriaForApiSearch(apiPgCrit, AuditTrailSearchResult.class);
    ISearchResults<AuditTrailSearchResult> auditTrail =
        auditTrailHandler.searchAuditTrail(apiSrchConfig, internalPgCrit, user);
    ApiActivitySearchResult apDashboardSearchResults = new ApiActivitySearchResult();
    // todo get intersection of viewable users and specified users.
    List<ApiActivity> auditEventList = new ArrayList<>();
    convertISearchResults(
        apiPgCrit,
        apiSrchConfig,
        user,
        auditTrail,
        apDashboardSearchResults,
        auditEventList,
        searchResult -> new ApiActivity(searchResult),
        auditEvent -> {}); // NOOP
    return apDashboardSearchResults;
  }

  void setMaxAuditSearchRange(Duration maxAuditSearchRange) {
    this.maxAuditSearchRange = maxAuditSearchRange;
  }

  private void adjustDateRangeLimits(ApiActivitySrchConfig apiSrchConfig) {
    dateRangeRestrictor.restrictDateRange(apiSrchConfig, maxAuditSearchRange);
    log.info(
        "Date range to be searched is from: {} to: {}",
        apiSrchConfig.getDateFrom(),
        (apiSrchConfig.getDateTo() == null) ? "unbounded" : apiSrchConfig.getDateTo());
  }
}
