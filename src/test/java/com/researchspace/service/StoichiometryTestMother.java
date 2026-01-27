package com.researchspace.service;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.record.Record;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import java.util.ArrayList;

public class StoichiometryTestMother {
  public static Stoichiometry createStoichiometry(Long parentReactionId, Record record) {
    RSChemElement parentReaction = createRSChemElement(parentReactionId);
    Stoichiometry stoichiometry =
        Stoichiometry.builder()
            .parentReaction(parentReaction)
            .record(record)
            .molecules(new ArrayList<>())
            .build();

    StoichiometryMolecule molecule =
        StoichiometryMolecule.builder()
            .id(2L)
            .stoichiometry(stoichiometry)
            .rsChemElement(createRSChemElement(3L))
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .molecularWeight(46.07)
            .build();
    stoichiometry.addMolecule(molecule);

    return stoichiometry;
  }

  public static RSChemElement createRSChemElement(Long id) {
    return RSChemElement.builder().id(id).chemElements("CCO").build();
  }
}
