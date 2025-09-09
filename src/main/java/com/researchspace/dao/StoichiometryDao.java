package com.researchspace.dao;

import com.researchspace.model.stoichiometry.Stoichiometry;
import java.util.Optional;

public interface StoichiometryDao extends GenericDao<Stoichiometry, Long> {

  Optional<Stoichiometry> findByParentReactionId(Long parentReactionId);
}
