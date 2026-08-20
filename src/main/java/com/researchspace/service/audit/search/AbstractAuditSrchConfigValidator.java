package com.researchspace.service.audit.search;

import java.util.Date;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import com.researchspace.model.core.GlobalIdentifier;
/**
 * Default validation for audit trail search config.
 */
public abstract class AbstractAuditSrchConfigValidator implements Validator {
	
	@Override
	public void validate(Object target, Errors errors) {
		IAuditTrailSearchConfig config = (IAuditTrailSearchConfig) target;
		Date from = config.getDateFrom();
		Date to = config.getDateTo();
		String oid = config.getOid();
		if (from != null && to != null && from.after(to)) {
			errors.rejectValue("dateFrom", "errors.minDateLaterThanMaxDate");
		} else if (oid != null && !GlobalIdentifier.isValid(oid)) {
			errors.rejectValue("oid", "errors.invalid", new Object[] { oid }, null);
		} 
	}
}
