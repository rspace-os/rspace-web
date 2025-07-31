package com.researchspace.dao;

import com.researchspace.model.Stoichiometry;

public interface StoichiometryDao extends GenericDao<Stoichiometry, Long> {

  Stoichiometry findByParentReactionId(Long parentReactionId);
}
