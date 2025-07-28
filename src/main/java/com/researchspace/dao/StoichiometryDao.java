package com.researchspace.dao;

import com.researchspace.model.Stoichiometry;

/** Data Access Object interface for Stoichiometry entities. */
public interface StoichiometryDao extends GenericDao<Stoichiometry, Long> {

  /**
   * Find a stoichiometry by parent reaction ID.
   *
   * @param parentReactionId the ID of the parent reaction
   * @return the stoichiometry for the parent reaction, or null if none exists
   */
  Stoichiometry findByParentReactionId(Long parentReactionId);
}
