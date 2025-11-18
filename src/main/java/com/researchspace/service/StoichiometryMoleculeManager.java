package com.researchspace.service;

import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;

public interface StoichiometryMoleculeManager {
  StoichiometryMolecule getById(Long id);

  BaseRecord getDocContainingMolecule(StoichiometryMolecule molecule);
}
