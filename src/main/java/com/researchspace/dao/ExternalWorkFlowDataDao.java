package com.researchspace.dao;

import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalService;
import com.researchspace.model.field.Field;
import java.util.List;
import java.util.Set;

public interface ExternalWorkFlowDataDao extends GenericDao<ExternalWorkFlowData, Long> {

  Set<ExternalWorkFlowData> findWorkFlowDataByRSpaceContainerIdAndServiceType(
      long rspaceContainerId, ExternalService type);

  Set<ExternalWorkFlowData> findAllExternalWorkFlowDataForFieldsAndServiceType(ExternalService externalService, List<Long> ids);
}
