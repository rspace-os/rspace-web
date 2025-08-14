package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;

public interface StoichiometryService {

  Stoichiometry getByParentChemical(long chemId, Integer revision, User user);

  Stoichiometry create(long chemId, Integer revision, User user);

  Stoichiometry update(
      long stoichiometryId, StoichiometryUpdateDTO stoichiometryUpdateDTO, User user);

  void delete(long stoichiometryId, User user);

  StoichiometryMolecule getMoleculeInfo(String smiles);
}
