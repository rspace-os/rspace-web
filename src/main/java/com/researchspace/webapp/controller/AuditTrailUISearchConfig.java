package com.researchspace.webapp.controller;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.service.audit.search.IAuditTrailSearchConfig;
import jakarta.validation.constraints.Size;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

@Data
public class AuditTrailUISearchConfig implements IAuditTrailSearchConfig {

  /** Upper bound for the autocomplete lists, well above what a search form produces. */
  private static final int MAX_LIST_LENGTH = 4096;

  // all are optional
  @DateTimeFormat(pattern = "yyyy-MM-dd")
  private Date dateFrom;

  @DateTimeFormat(pattern = "yyyy-MM-dd")
  private Date dateTo;

  private Set<AuditDomain> domains = new HashSet<>();
  private Set<AuditAction> actions = new HashSet<>();

  @Size(max = MAX_LIST_LENGTH, message = "{audit.errors.listTooLong}")
  private String users;

  @Size(max = MAX_LIST_LENGTH, message = "{audit.errors.listTooLong}")
  private String groups;

  @Size(max = MAX_LIST_LENGTH, message = "{audit.errors.listTooLong}")
  private String communities;

  private String oid;
  private Set<String> usernames = new HashSet<>();

  public void addUsernames(Collection<String> usernames) {
    this.usernames.addAll(usernames);
  }
}
