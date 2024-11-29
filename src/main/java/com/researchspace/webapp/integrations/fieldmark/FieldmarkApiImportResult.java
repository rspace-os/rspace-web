package com.researchspace.webapp.integrations.fieldmark;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
@JsonPropertyOrder(
    value = {"containerGlobalId", "containerName", "sampleTemplateGlobalId", "sampleGlobalIds"})
public class FieldmarkApiImportResult {

  private final String containerGlobalId;
  private final String containerName;
  private final String sampleTemplateGlobalId;
  private final Set<String> sampleGlobalIds;

  public FieldmarkApiImportResult(
      String containerGlobalId, String containerName, String sampleTemplateGlobalId) {
    this.containerGlobalId = containerGlobalId;
    this.containerName = containerName;
    this.sampleTemplateGlobalId = sampleTemplateGlobalId;
    this.sampleGlobalIds = new HashSet<>();
  }

  public boolean addSampleGlobalId(String sampleGlobalId) {
    return this.sampleGlobalIds.add(sampleGlobalId);
  }

  public Set<String> getSampleGlobalIds() {
    return Collections.unmodifiableSet(sampleGlobalIds);
  }
}
