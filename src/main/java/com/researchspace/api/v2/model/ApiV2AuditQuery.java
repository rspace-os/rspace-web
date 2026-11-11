package com.researchspace.api.v2.model;

import com.researchspace.model.audittrail.AuditAction;
import jakarta.validation.constraints.Size;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;

/** Filters for a capped resource audit log. */
@Data
public final class ApiV2AuditQuery {

  @DateTimeFormat(iso = ISO.DATE_TIME)
  private Date start;

  @DateTimeFormat(iso = ISO.DATE_TIME)
  private Date end;

  private Set<AuditAction> actions = new HashSet<>();

  @Size(max = 255)
  private String q;
}
