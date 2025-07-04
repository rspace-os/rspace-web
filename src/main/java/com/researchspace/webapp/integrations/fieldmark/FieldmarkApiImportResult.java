package com.researchspace.webapp.integrations.fieldmark;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode
@JsonPropertyOrder(
    value = {"containerGlobalId", "containerName", "sampleTemplateGlobalId", "sampleGlobalIds"})
@NoArgsConstructor
public class FieldmarkApiImportResult {

  private String containerGlobalId;
  private Long containerId;
  private String containerName;
  private String sampleTemplateGlobalId;
  private Long sampleTemplateId;
  private Set<String> sampleGlobalIds;
  private Set<Long> sampleIds;

  public FieldmarkApiImportResult(ApiContainer container, ApiSampleTemplate sampleTemplate) {
    this.containerGlobalId = container.getGlobalId();
    this.containerId = container.getId();
    this.containerName = container.getName();
    this.sampleTemplateGlobalId = sampleTemplate.getGlobalId();
    this.sampleTemplateId = sampleTemplate.getId();
    this.sampleGlobalIds = new HashSet<>();
    this.sampleIds = new HashSet<>();
  }

  public boolean addSample(ApiSampleWithFullSubSamples sample) {
    return this.sampleIds.add(sample.getId()) && this.sampleGlobalIds.add(sample.getGlobalId());
  }

  public Set<String> getSampleGlobalIds() {
    return Collections.unmodifiableSet(sampleGlobalIds);
  }

  public Set<Long> getSampleIds() {
    return Collections.unmodifiableSet(sampleIds);
  }
}
