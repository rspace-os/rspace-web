package com.researchspace.service.audit.search;

import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import java.util.Date;
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
