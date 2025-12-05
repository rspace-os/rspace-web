package com.researchspace.service.impl;

import com.researchspace.dao.ExternalWorkFlowDao;
import com.researchspace.dao.ExternalWorkFlowDataDao;
import com.researchspace.dao.ExternalWorkFlowInvocationDao;
import com.researchspace.model.externalWorkflows.ExternalWorkFlow;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData.ExternalService;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import com.researchspace.service.ExternalWorkFlowDataManager;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Implementation of ExternalStorageManager interface.
 *
 * @author nico
 */
@Service
public class ExternalWorkFlowDataManagerImpl implements ExternalWorkFlowDataManager {

  @Autowired private ExternalWorkFlowDataDao externalWorkFlowDataDao;
  @Autowired private ExternalWorkFlowInvocationDao externalWorkFlowInvocationDao;
  @Autowired private ExternalWorkFlowDao externalWorkFlowDao;

  @Override
  public void save(ExternalWorkFlowData data) {
    externalWorkFlowDataDao.save(data);
  }

  @Override
  public Set<ExternalWorkFlowData> findWorkFlowDataByRSpaceContainerIdAndServiceType(
      long containerId, ExternalService type) {
    return externalWorkFlowDataDao.findWorkFlowDataByRSpaceContainerIdAndServiceType(
        containerId, type);
  }

  @Override
  public void save(ExternalWorkFlowInvocation invocation) {
    externalWorkFlowInvocationDao.save(invocation);
  }

  @Override
  public ExternalWorkFlowInvocation saveExternalWorkfFlowInvocation(
      String workflowId,
      String workflowName,
      String invocationId,
      List<ExternalWorkFlowData> allMatchingDataForThisInvocation,
      String state) {
    return externalWorkFlowInvocationDao.saveExternalWorkfFlowInvocation(
        workflowId, workflowName, invocationId, allMatchingDataForThisInvocation, state);
  }

  @Override
  public ExternalWorkFlow findWorkFlowByExtIdAndName(String extId, String name) {
    return externalWorkFlowDao.findWorkFlowByExtIdAndName(extId, name);
  }
}
