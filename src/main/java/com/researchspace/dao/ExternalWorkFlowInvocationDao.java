package com.researchspace.dao;

import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import java.util.List;

public interface ExternalWorkFlowInvocationDao
    extends GenericDao<ExternalWorkFlowInvocation, Long> {

  void saveExternalWorkfFlowInvocation(
      String workflowId,
      String workflowName,
      String invocationId,
      List<ExternalWorkFlowData> allMatchingDataForThisInvocation,
      String state);
}
