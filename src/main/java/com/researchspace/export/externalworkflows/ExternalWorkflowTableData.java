package com.researchspace.export.externalworkflows;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExternalWorkflowTableData {
  private final List<ExternalWorkflowDataLink> dataLinks;
  private final String historyName;
  private final String historyHref;
  private final String invocationName;
  private final String invocationHref;
  private final String invocationStatus;

  public boolean hasHistoryHref() {
    return historyHref != null && !historyHref.isEmpty();
  }

  public boolean hasInvocationHref() {
    return invocationHref != null && !invocationHref.isEmpty();
  }
}
