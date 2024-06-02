package com.researchspace.api.v1.controller;

import com.researchspace.core.util.DateRangeAdjustable;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.service.audit.search.IAuditTrailSearchConfig;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Data
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@NoArgsConstructor
public class ApiActivitySrchConfig extends ApiSearchConfig
    implements IAuditTrailSearchConfig, DateRangeAdjustable {
  static final String YYYY_MM_DD_ISO8601 = "yyyy-MM-dd";

  // all are optional
  @DateTimeFormat(pattern = YYYY_MM_DD_ISO8601)
  private Date dateFrom;

  @DateTimeFormat(pattern = YYYY_MM_DD_ISO8601)
  private Date dateTo;

  private Set<AuditDomain> domains = new HashSet<>();
  private Set<AuditAction> actions = new HashSet<>();
  private Set<String> usernames = new HashSet<>();

  @Pattern(regexp = GlobalIdentifier.OID_PATTERN_STRING)
  private String oid;

  @Override
  public MultiValueMap<String, String> toMap() {
    LinkedMultiValueMap<String, String> rc = new LinkedMultiValueMap<>();
    SimpleDateFormat dateF = new SimpleDateFormat(YYYY_MM_DD_ISO8601);

    if (dateTo != null) {
      rc.add("dateTo", dateF.format(dateTo));
    }
    if (dateFrom != null) {
      rc.add("dateFrom", dateF.format(dateFrom));
    }
    if (!StringUtils.isBlank(oid)) {
      rc.add("oid", oid);
    }
    actions.stream().forEach(a -> rc.add("actions", a.name()));
    domains.stream().forEach(d -> rc.add("domains", d.name()));
    usernames.stream().forEach(u -> rc.add("usernames", u));
    return rc;
  }
}
