package com.researchspace.service.impl;

import com.researchspace.dao.OrganisationDao;
import com.researchspace.model.Organisation;
import com.researchspace.model.User;
import com.researchspace.service.OrganisationManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("organisationManager")
public class OrganisationManagerImpl implements OrganisationManager {

  @Autowired private OrganisationDao organisationDao;

  @Override
  public List<Organisation> getApprovedOrganisations(String term) {
    return organisationDao.getApprovedOrganisations(term);
  }

  @Override
  public boolean organisationExists(String title) {
    return organisationDao.getBySimpleNaturalId(title).isPresent();
  }

  @Override
  public void checkAndSaveNonApprovedOrganisation(User user) {
    String title = user.getAffiliation();
    if (!organisationExists(title)) {
      Organisation nonApprovedOrganisation = new Organisation(title, false);
      organisationDao.save(nonApprovedOrganisation);
    }
  }
}
