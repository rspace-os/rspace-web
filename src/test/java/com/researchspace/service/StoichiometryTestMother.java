package com.researchspace.service;

import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.record.Record;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class StoichiometryTestMother {
  public static Stoichiometry createStoichiometry(Long id, Long parentReactionId, Record record) {
    RSChemElement parentReaction = createRSChemElement(parentReactionId);
    Stoichiometry stoichiometry =
        Stoichiometry.builder()
            .id(id)
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

  public static StoichiometryInventoryLinkRequest createStoichiometryInventoryLinkRequest(
      Long stoichiometryMoleculeID, String sampleGlobalId) {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setStoichiometryMoleculeId(stoichiometryMoleculeID);
    req.setInventoryItemGlobalId(sampleGlobalId);
    req.setQuantity(BigDecimal.valueOf(2.5));
    req.setUnitId(RSUnitDef.MILLI_LITRE.getId());
    req.setReducesStock(false);
    return req;
  }

  public static ElementalAnalysisDTO createAnalysisDTOWithMultipleReactants() {
    MoleculeInfoDTO reactant1 =
        MoleculeInfoDTO.builder()
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .mass(46.07)
            .build();

    MoleculeInfoDTO reactant2 =
        MoleculeInfoDTO.builder()
            .role(MoleculeRole.REACTANT)
            .formula("O2")
            .name("Oxygen")
            .smiles("O=O")
            .mass(32.00)
            .build();

    MoleculeInfoDTO product =
        MoleculeInfoDTO.builder()
            .role(MoleculeRole.PRODUCT)
            .formula("CH3COOH")
            .name("Acetic Acid")
            .smiles("CC(=O)O")
            .mass(60.05)
            .build();

    List<MoleculeInfoDTO> molecules = List.of(reactant1, reactant2, product);
    ElementalAnalysisDTO analysisDTO =
        ElementalAnalysisDTO.builder()
            .moleculeInfo(molecules)
            .formula("CH3COOH + H2O")
            .isReaction(true)
            .build();
    return analysisDTO;
  }

  public static RSChemElement createRSChemElement(Long id) {
    return RSChemElement.builder().id(id).chemElements("CCO").build();
  }
}
