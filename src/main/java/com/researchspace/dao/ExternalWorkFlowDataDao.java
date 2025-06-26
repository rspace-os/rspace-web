package com.researchspace.dao;

import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import java.util.List;

public interface ExternalWorkFlowDataDao extends GenericDao<ExternalWorkFlowData, Long> {

  List<ExternalWorkFlowData> findWorkFlowDataByRSpaceContainerIdAndServiceType(
      long rspaceContainerId, ExternalWorkFlowData.ExternalService type);
}
