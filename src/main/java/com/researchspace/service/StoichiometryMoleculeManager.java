package com.researchspace.service;

import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;

public interface StoichiometryMoleculeManager {
  StoichiometryMolecule getById(Long id);

  StructuredDocument getDocContainingMolecule(StoichiometryMolecule molecule);
}
