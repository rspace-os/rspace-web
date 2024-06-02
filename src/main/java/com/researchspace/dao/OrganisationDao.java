package com.researchspace.dao;

import com.researchspace.model.Organisation;
import java.util.List;

public interface OrganisationDao extends GenericDao<Organisation, Long> {

  /**
   * @param term
   * @return
   */
  List<Organisation> getApprovedOrganisations(String term);
}
