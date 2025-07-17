package com.researchspace.service;

import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalService;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import java.util.List;
import java.util.Set;

public interface ExternalWorkFlowDataManager {
  void save(ExternalWorkFlowData data);

  Set<ExternalWorkFlowData> findWorkFlowDataByRSpaceContainerIdAndServiceType(
      long containerId, ExternalService type);

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
