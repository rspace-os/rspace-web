package com.researchspace.api.v2.model;

import com.researchspace.core.util.DateRangeAdjustable;
import com.researchspace.model.audittrail.AuditAction;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/** Filters and pagination for a resource audit log. */
@Data
@EqualsAndHashCode(callSuper = true)
public final class ApiV2AuditQuery extends ApiV2PaginationCriteria implements DateRangeAdjustable {

  @DateTimeFormat(iso = ISO.DATE_TIME)
  private Date dateFrom;

  @DateTimeFormat(iso = ISO.DATE_TIME)
  private Date dateTo;

  private Set<AuditAction> actions = new HashSet<>();

  @Override
  public Date getDateFrom() {
    return dateFrom == null ? null : new Date(dateFrom.getTime());
  }

  @Override
  public void setDateFrom(Date dateFrom) {
    this.dateFrom = dateFrom == null ? null : new Date(dateFrom.getTime());
  }

  @Override
  public Date getDateTo() {
    return dateTo == null ? null : new Date(dateTo.getTime());
  }

  @Override
  public void setDateTo(Date dateTo) {
    this.dateTo = dateTo == null ? null : new Date(dateTo.getTime());
  }
}
