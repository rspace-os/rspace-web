package com.researchspace.service.audit.search;

import java.util.Date;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;

import lombok.Data;

@Data
 class LogLine {
	 Date date;
	 AuditDomain domain;
	 AuditAction action;
	 String data;
	 String username;
	 String fullname;
	 String description;
}
