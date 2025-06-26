package com.researchspace.service.impl;

import com.researchspace.dao.ExternalWorkFlowDataDao;
import com.researchspace.dao.ExternalWorkFlowInvocationDao;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import com.researchspace.service.ExternalWorkFlowDataManager;
import java.util.List;
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

  @Override
  public void save(ExternalWorkFlowData data) {
    externalWorkFlowDataDao.save(data);
  }

  @Override
  public List<ExternalWorkFlowData> findWorkFlowDataByRSpaceContainerIdAndServiceType(
      long containerId, ExternalWorkFlowData.ExternalService type) {
    return externalWorkFlowDataDao.findWorkFlowDataByRSpaceContainerIdAndServiceType(
        containerId, type);
  }

  @Override
  public List<ExternalWorkFlowInvocation> findInvocationsByRSpaceContainerIdAndServiceType(
      long containerId, ExternalWorkFlowData.ExternalService type) {
    return List.of(); // TODO
  }

  @Override
  public void save(ExternalWorkFlowInvocation invocation) {
    externalWorkFlowInvocationDao.save(invocation);
  }

  @Override
  public void saveExternalWorkfFlowInvocation(
      String workflowId,
      String workflowName,
      String invocationId,
      List<ExternalWorkFlowData> allMatchingDataForThisInvocation,
      String state) {
    externalWorkFlowInvocationDao.saveExternalWorkfFlowInvocation(
        workflowId, workflowName, invocationId, allMatchingDataForThisInvocation, state);
  }
}
