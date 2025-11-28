package com.researchspace.dao.hibernate;

import com.researchspace.dao.ExternalWorkFlowDao;
import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.model.externalWorkflows.ExternalWorkFlow;
import org.springframework.stereotype.Repository;

@Repository("externalWorkFlowDao")
public class ExternalWorkFlowDaoHibernate extends GenericDaoHibernate<ExternalWorkFlow, Long>
    implements ExternalWorkFlowDao {

  public ExternalWorkFlowDaoHibernate() {
    super(ExternalWorkFlow.class);
  }

  @Override
  public ExternalWorkFlow findWorkFlowByExtIdAndName(String extId, String name) {
    return sessionFactory
        .getCurrentSession()
        .createQuery(
            "from ExternalWorkFlow ewf join fetch ewf.externalWorkflowInvocations"
                + " where ewf.extId ="
                + " (:extId) and ewf.name = (:name) ",
            ExternalWorkFlow.class)
        .setParameter("extId", extId)
        .setParameter("name", name)
        .getSingleResult();
  }
}
