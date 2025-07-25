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
}
