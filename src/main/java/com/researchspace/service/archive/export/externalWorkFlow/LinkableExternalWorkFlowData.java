package com.researchspace.service.archive.export.externalWorkFlow;

import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;

public class LinkableExternalWorkFlowData implements IFieldLinkableElement {

  private final ExternalWorkFlowData externalWorkflowData;

  public LinkableExternalWorkFlowData(ExternalWorkFlowData extd) {
    this.externalWorkflowData = extd;
  }

  @Override
  public Long getId() {
    return externalWorkflowData.getId();
  }

  @Override
  public GlobalIdentifier getOid() {
    return new GlobalIdentifier(GlobalIdPrefix.SD, externalWorkflowData.getRspacedataid());
  }

  public ExternalWorkFlowData getExternalWorkflowData() {
    return externalWorkflowData;
  }
}
