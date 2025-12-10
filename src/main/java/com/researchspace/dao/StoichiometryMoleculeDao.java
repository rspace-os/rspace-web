package com.researchspace.dao;

import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;

public interface StoichiometryMoleculeDao extends GenericDao<StoichiometryMolecule, Long> {
  StructuredDocument getDocContainingMolecule(StoichiometryMolecule molecule);
}
