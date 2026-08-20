package com.researchspace.model.externalWorkflows;

import java.util.HashSet;
import java.util.Set;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Entity
@Getter
@EqualsAndHashCode(of = { "extId","externalWorkFlow"})
public class ExternalWorkFlowInvocation {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  /**
   * The ID provided by the external service for this invocation
   */
  @NotNull
  private String extId;
  @ManyToMany
  @JoinTable(name = "ExtInvoc_ExtData", joinColumns = {
      @JoinColumn(name = "invocation_id") }, inverseJoinColumns = @JoinColumn(name = "data_id"))
  @NotNull
  private Set<ExternalWorkFlowData> externalWorkFlowData = new HashSet<>();
  @Setter
  @NotNull
  private String status;

  @ManyToOne
  @JoinColumn(name = "external_work_flow_id")
  private ExternalWorkFlow externalWorkFlow;
  @Builder()
  public ExternalWorkFlowInvocation(@NonNull String extId, @NonNull Set<ExternalWorkFlowData> externalWorkFlowData,
      @NonNull String status, @NonNull ExternalWorkFlow externalWorkFlow) {
    this.extId=extId;
    this.externalWorkFlowData = externalWorkFlowData;
    this.status = status;
    this.externalWorkFlow = externalWorkFlow;
    for(ExternalWorkFlowData data : externalWorkFlowData) {
     data.getExternalWorkflowInvocations().add(this);
    }
    externalWorkFlow.getExternalWorkflowInvocations().add(this);
  }
  protected ExternalWorkFlowInvocation() {
  }

}
