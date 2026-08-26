package com.researchspace.service.audit.search;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import java.util.Date;
import java.util.Set;

public interface IAuditTrailSearchConfig {

  Date getDateFrom();

  void setDateFrom(Date from);

  Date getDateTo();

  void setDateTo(Date to);

  Set<AuditDomain> getDomains();

  Set<AuditAction> getActions();

  String getOid();

  Set<String> getUsernames();
}
