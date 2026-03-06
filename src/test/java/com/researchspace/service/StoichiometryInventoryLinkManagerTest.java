package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

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
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class StoichiometryInventoryLinkManagerTest extends SpringTransactionalTest {

  @Autowired private StoichiometryInventoryLinkManager linkManager;
  @Autowired private StoichiometryManager stoichiometryManager;
  @Autowired private RSChemElementManager rsChemElementManager;
  @Autowired private SubSampleApiManager subSampleApiMgr;

  @Test
  public void createLinkTest() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "some text");
    Field field = doc.getFields().get(0);
    RSChemElement reaction = addReactionToField(field, user);

    Stoichiometry stoich =
        stoichiometryManager.createFromAnalysis(createSimpleAnalysis(), reaction, doc, user);
    StoichiometryMolecule molecule = stoich.getMolecules().get(0);

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId(sample.getGlobalId());

    // Create
    StoichiometryInventoryLink createdLink = linkManager.createLink(molecule.getId(), req, user);
    assertNotNull(createdLink.getId());
    assertEquals(molecule.getId(), createdLink.getStoichiometryMolecule().getId());
    assertEquals(sample.getGlobalId(), createdLink.getInventoryRecord().getOid().getIdString());
  }

  @Test
  public void createLinkToSubSampleReducesStock() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "some text");
    Field field = doc.getFields().get(0);
    RSChemElement reaction = addReactionToField(field, user);

    Stoichiometry stoich =
        stoichiometryManager.createFromAnalysis(createSimpleAnalysis(), reaction, doc, user);
    StoichiometryMolecule molecule = stoich.getMolecules().get(0);
    molecule.setActualAmount(0.01); // 10 mg
    stoichiometryManager.save(stoich);

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    ApiSubSampleInfo subInfo = sample.getSubSamples().get(0);

    ApiSubSample sub = subSampleApiMgr.getApiSubSampleById(subInfo.getId(), user);
    assertEquals("5 g", sub.getQuantity().toQuantityInfo().toPlainString());

    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId(subInfo.getGlobalId());

    StoichiometryInventoryLink createdLink = linkManager.createLink(molecule.getId(), req, user);
    assertNotNull(createdLink.getId());

    linkManager.deductStock(List.of(createdLink.getId()), user);

    ApiSubSample after = subSampleApiMgr.getApiSubSampleById(subInfo.getId(), user);
    // 5 g - 10 mg = 4.99 g
    assertEquals("4.99 g", after.getQuantity().toQuantityInfo().toPlainString());
  }

  private ElementalAnalysisDTO createSimpleAnalysis() {
    MoleculeInfoDTO molecule =
        MoleculeInfoDTO.builder()
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .mass(46.07)
            .build();
    return ElementalAnalysisDTO.builder()
        .moleculeInfo(List.of(molecule))
        .formula("C2H6O")
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
