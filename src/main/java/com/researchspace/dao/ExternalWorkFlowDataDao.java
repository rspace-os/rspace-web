package com.researchspace.dao;

import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalService;
import java.util.Set;

public interface ExternalWorkFlowDataDao extends GenericDao<ExternalWorkFlowData, Long> {

  Set<ExternalWorkFlowData> findWorkFlowDataByRSpaceContainerIdAndServiceType(
      long rspaceContainerId, ExternalService type);
}
