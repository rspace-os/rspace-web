package com.researchspace.dao;

import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;

public interface StoichiometryMoleculeDao extends GenericDao<StoichiometryMolecule, Long> {
  BaseRecord getDocContainingMolecule(StoichiometryMolecule molecule);
}
