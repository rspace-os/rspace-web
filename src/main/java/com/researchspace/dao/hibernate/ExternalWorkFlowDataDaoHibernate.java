package com.researchspace.dao.hibernate;

import com.researchspace.dao.ExternalWorkFlowDataDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.externalWorkflows.ExternalWorkFlowData;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository("externalWorkFlowDataDao")
public class ExternalWorkFlowDataDaoHibernate
    extends GenericDaoHibernate<ExternalWorkFlowData, Long> implements ExternalWorkFlowDataDao {

  public ExternalWorkFlowDataDaoHibernate() {
    super(ExternalWorkFlowData.class);
  }

  @Override
  public List<ExternalWorkFlowData> findWorkFlowDataByRSpaceContainerIdAndServiceType(
      long rspaceContainerId, ExternalWorkFlowData.ExternalService type) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from ExternalWorkFlowData efd left join fetch efd.externalWorkflowInvocations ewfi"
                + " left join fetch ewfi.externalWorkFlowData where efd.rspacecontainerid ="
                + " (:rspaceContainerId) and efd.externalService = (:type) ",
            ExternalWorkFlowData.class)
        .setParameter("rspaceContainerId", rspaceContainerId)
        .setParameter("type", type)
        .list();
  }
}
