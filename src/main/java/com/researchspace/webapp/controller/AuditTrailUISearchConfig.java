package com.researchspace.webapp.controller;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.service.audit.search.IAuditTrailSearchConfig;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class AuditTrailUISearchConfig implements IAuditTrailSearchConfig {
  // all are optional
  @DateTimeFormat(pattern = "yyyy-MM-dd")
  private Date dateFrom;

  @DateTimeFormat(pattern = "yyyy-MM-dd")
  private Date dateTo;

  private Set<AuditDomain> domains = new HashSet<>();
  private Set<AuditAction> actions = new HashSet<>();
  private String users;
  private String groups;
  private String communities;
  private String oid;
  private Set<String> usernames = new HashSet<>();

  public void addUsernames(Collection<String> usernames) {
    this.usernames.addAll(usernames);
  }
}
