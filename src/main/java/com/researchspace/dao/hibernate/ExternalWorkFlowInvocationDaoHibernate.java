package com.researchspace.dao.hibernate;

import com.researchspace.dao.ExternalWorkFlowDao;
import com.researchspace.dao.ExternalWorkFlowInvocationDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.externalWorkflows.ExternalWorkFlow;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowInvocation;
import java.util.HashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository("externalWorkFlowInvocationDao")
public class ExternalWorkFlowInvocationDaoHibernate
    extends GenericDaoHibernate<ExternalWorkFlowInvocation, Long>
    implements ExternalWorkFlowInvocationDao {
  @Autowired private ExternalWorkFlowDao externalWorkFlowDao;

  public ExternalWorkFlowInvocationDaoHibernate() {
    super(ExternalWorkFlowInvocation.class);
  }

  @Override
  public void saveExternalWorkfFlowInvocation(
      String workflowId,
      String workflowName,
      String invocationId,
      List<ExternalWorkFlowData> allMatchingDataForThisInvocation,
      String state) {
    ExternalWorkFlow externalWorkFlow = new ExternalWorkFlow(workflowId, workflowName, "");
    externalWorkFlow = externalWorkFlowDao.save(externalWorkFlow);
    ExternalWorkFlowInvocation externalWorkFlowInvocation =
        new ExternalWorkFlowInvocation(
            invocationId, new HashSet(allMatchingDataForThisInvocation), state, externalWorkFlow);
    save(externalWorkFlowInvocation);
  }

  //  @Override
  //  public List<ExternalWorkFlowData> findWorkFlowDataByRSpaceContainerIdAndServiceType(
  //      long rspaceContainerId, ExternalWorkFlowData.ExternalService type) {
  //    return sessionFactory
  //        .getCurrentSession()
  //        .createQuery(
  //            "from ExternalWorkFlowData where rspacecontainerid = (:rspaceContainerId) and"
  //                + " externalService = (:type) ",
  //            ExternalWorkFlowData.class)
  //        .setParameter("rspaceContainerId", rspaceContainerId)
  //        .setParameter("type", type)
  //        .list();
  //  }
}
