package com.researchspace.service.impl;

import com.researchspace.dao.StoichiometryMoleculeDao;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.service.StoichiometryMoleculeManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StoichiometryMoleculeManagerImpl implements StoichiometryMoleculeManager {
  private final StoichiometryMoleculeDao stoichiometryMoleculeDao;

  @Autowired
  public StoichiometryMoleculeManagerImpl(StoichiometryMoleculeDao stoichiometryMoleculeDao) {
    this.stoichiometryMoleculeDao = stoichiometryMoleculeDao;
  }

  @Override
  public StoichiometryMolecule getById(Long id) {
    return stoichiometryMoleculeDao.get(id);
  }

  @Override
  public StructuredDocument getDocContainingMolecule(StoichiometryMolecule molecule) {
    BaseRecord record = stoichiometryMoleculeDao.getDocContainingMolecule(molecule);
    return record.asStrucDoc();
  }
}
