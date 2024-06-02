package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {"username", "fullName", "domain", "action", "timestamp", "payload", "_links"})
public class ApiActivity extends LinkableApiObject {

  private Map<String, Object> payload;
  private String username;
  private String fullName;
  private AuditDomain domain;
  private AuditAction action;

  @JsonProperty("timestamp")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long timestampMillis;

  public ApiActivity(AuditTrailSearchResult searchResult) {
    HistoricData event = searchResult.getEvent();
    this.payload = event.getData().getData();
    this.username = event.getSubject();
    this.fullName = event.getFullName();
    this.domain = event.getDomain();
    this.action = event.getAction();
    this.timestampMillis = searchResult.getTimestamp();
  }
}
