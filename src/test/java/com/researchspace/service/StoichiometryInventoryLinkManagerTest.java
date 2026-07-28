package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class StoichiometryInventoryLinkManagerTest extends SpringTransactionalTest {

  @Autowired private StoichiometryInventoryLinkManager linkManager;
  @Autowired private StoichiometryManager stoichiometryManager;
  @Autowired private RSChemElementManager rsChemElementManager;
  @Autowired private SubSampleApiManager subSampleApiMgr;

  @Test
  public void createLinkTest() throws Exception {
    User user = createInitAndLoginAnyUser();
    Stoichiometry stoich = createStoichiometry(user, analysisOf(ethanol()));
    StoichiometryMolecule molecule = stoich.getMolecules().get(0);

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    StoichiometryInventoryLink createdLink =
        linkManager.createLink(molecule.getId(), linkRequestFor(sample.getGlobalId()), user);

    assertNotNull(createdLink.getId());
    assertEquals(molecule.getId(), createdLink.getStoichiometryMolecule().getId());
    assertEquals(sample.getGlobalId(), createdLink.getInventoryRecord().getOid().getIdString());
  }

  @Test
  public void createLinkToSubSampleReducesStock() throws Exception {
    User user = createInitAndLoginAnyUser();
    Stoichiometry stoich = createStoichiometry(user, analysisOf(ethanol()));
    StoichiometryMolecule molecule = stoich.getMolecules().get(0);
    molecule.setActualAmount(0.01); // 10 mg
    stoichiometryManager.save(stoich);

    ApiSubSampleInfo subInfo = createBasicSampleForUser(user).getSubSamples().get(0);
    assertEquals(
        "5 g",
        subSampleApiMgr
            .getApiSubSampleById(subInfo.getId(), user)
            .getQuantity()
            .toQuantityInfo()
            .toPlainString());

    StoichiometryInventoryLink createdLink =
        linkManager.createLink(molecule.getId(), linkRequestFor(subInfo.getGlobalId()), user);
    assertNotNull(createdLink.getId());

    linkManager.deductStock(stoich.getId(), List.of(createdLink.getId()), user);

    ApiSubSample after = subSampleApiMgr.getApiSubSampleById(subInfo.getId(), user);
    // 5 g - 10 mg = 4.99 g
    assertEquals("4.99 g", after.getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void twoMoleculesLinkedToSameSubSampleBumpVersionOnce() throws Exception {
    User user = createInitAndLoginAnyUser();
    Stoichiometry stoich =
        createStoichiometry(user, analysisOf(ethanol(), molecule("Methanol", "CH4O", "CO", 32.04)));
    assertEquals(2, stoich.getMolecules().size());
    stoich.getMolecules().forEach(m -> m.setActualAmount(0.01)); // 10 mg each
    stoichiometryManager.save(stoich);

    ApiSubSampleInfo subInfo = createBasicSampleForUser(user).getSubSamples().get(0);
    assertEquals(
        1L, subSampleApiMgr.getApiSubSampleById(subInfo.getId(), user).getVersion().longValue());

    List<Long> linkIds =
        stoich.getMolecules().stream()
            .map(
                m -> linkManager.createLink(m.getId(), linkRequestFor(subInfo.getGlobalId()), user))
            .map(StoichiometryInventoryLink::getId)
            .toList();

    linkManager.deductStock(stoich.getId(), linkIds, user);

    ApiSubSample after = subSampleApiMgr.getApiSubSampleById(subInfo.getId(), user);
    // both molecules' amounts are deducted: 5 g - 10 mg - 10 mg
    assertEquals("4.98 g", after.getQuantity().toQuantityInfo().toPlainString());
    // Envers writes one revision per entity per transaction, so the version may only advance once,
    // otherwise the skipped version has no revision to resolve to (RSDEV-1319)
    assertEquals(2L, after.getVersion().longValue());
  }

  private Stoichiometry createStoichiometry(User user, ElementalAnalysisDTO analysis)
      throws Exception {
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "some text");
    RSChemElement reaction = addReactionToField(doc.getFields().get(0), user);
    return stoichiometryManager.createFromAnalysis(analysis, reaction, doc, user);
  }

  private StoichiometryInventoryLinkRequest linkRequestFor(String inventoryGlobalId) {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId(inventoryGlobalId);
    return req;
  }

  private MoleculeInfoDTO ethanol() {
    return molecule("Ethanol", "C2H6O", "CCO", 46.07);
  }

  private MoleculeInfoDTO molecule(String name, String formula, String smiles, double mass) {
    return MoleculeInfoDTO.builder()
        .role(MoleculeRole.REACTANT)
        .formula(formula)
        .name(name)
        .smiles(smiles)
        .mass(mass)
        .build();
  }

  private ElementalAnalysisDTO analysisOf(MoleculeInfoDTO... molecules) {
    return ElementalAnalysisDTO.builder()
        .moleculeInfo(List.of(molecules))
        .formula(molecules[0].getFormula())
        .isReaction(false)
        .build();
  }

  private RSChemElement addReactionToField(Field field, User owner) throws Exception {
    String reactionString = "C1C=CC=CC=1.C1C=CC=C1>>C1CCCCC1";
    String imageBytes = com.researchspace.testutils.RSpaceTestUtils.getChemImage();
    ChemicalDataDTO chemicalData =
        ChemicalDataDTO.builder()
            .chemElements(reactionString)
            .fieldId(field.getId())
            .imageBase64(imageBytes)
            .chemElementsFormat(ChemElementsFormat.MOL.getLabel())
            .build();

    RSChemElement chem = rsChemElementManager.saveChemElement(chemicalData, owner);

    String chemLink =
        richTextUpdater.generateURLStringForRSChemElementLink(
            chem.getId(), chem.getParentId(), 50, 50);
    String fieldData = field.getFieldData() + chemLink;
    field.setFieldData(fieldData);
    field.getStructuredDocument().notifyDelta(DeltaType.FIELD_CHG);
    recordMgr.save(field.getStructuredDocument(), owner);

    return chem;
  }
}
