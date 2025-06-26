package com.researchspace.service;

import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import java.util.List;

public interface ExternalWorkFlowDataManager {
  void save(ExternalWorkFlowData data);

  List<ExternalWorkFlowData> findWorkFlowDataByRSpaceContainerIdAndServiceType(
      long containerId, ExternalWorkFlowData.ExternalService type);

  List<ExternalWorkFlowInvocation> findInvocationsByRSpaceContainerIdAndServiceType(
      long containerId, ExternalWorkFlowData.ExternalService type);

  void save(ExternalWorkFlowInvocation invocation);

  void saveExternalWorkfFlowInvocation(
      String workflowId,
      String workflowName,
      String invocationId,
      List<ExternalWorkFlowData> allMatchingDataForThisInvocation,
      String state);
}
