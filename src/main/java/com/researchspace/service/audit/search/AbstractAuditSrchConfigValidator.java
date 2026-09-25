package com.researchspace.service.audit.search;

import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.core.GlobalIdentifier;
import java.util.Date;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;

/** Default validation for audit trail search config. */
public abstract class AbstractAuditSrchConfigValidator implements Validator {

  /** Pattern accepted by the API search configuration's {@code oid} bean-validation constraint. */
  public static final String AUDIT_RESOURCE_ID_PATTERN =
      "(?:"
          + GlobalIdentifier.OID_PATTERN_STRING
          + "|"
          + AuditDomain.BOOKING_IDENTIFIER_PATTERN
          + ")";

  @Override
  public void validate(Object target, Errors errors) {
    IAuditTrailSearchConfig config = (IAuditTrailSearchConfig) target;
    Date from = config.getDateFrom();
    Date to = config.getDateTo();
    String oid = config.getOid();
    if (from != null && to != null && from.after(to)) {
      errors.rejectValue("dateFrom", "errors.minDateLaterThanMaxDate");
    } else if (oid != null && !isValidAuditResourceId(oid)) {
      errors.rejectValue("oid", "errors.invalid", new Object[] {oid}, null);
    }
  }

  static boolean isValidAuditResourceId(String identifier) {
    return GlobalIdentifier.isValid(identifier) || AuditDomain.isBookingIdentifier(identifier);
  }
}
