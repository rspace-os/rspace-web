package com.researchspace.dao;

import com.researchspace.model.externalWorkflows.ExternalWorkFlow;

public interface ExternalWorkFlowDao extends GenericDao<ExternalWorkFlow, Long> {

  ExternalWorkFlow findWorkFlowByExtIdAndName(String extId, String name);
}
