package com.researchspace.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkDTO;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryLinkQuantityUpdateRequest;
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
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class StoichiometryInventoryLinkManagerTest extends SpringTransactionalTest {

  @Autowired private StoichiometryInventoryLinkManager linkManager;
  @Autowired private StoichiometryManager stoichiometryManager;
  @Autowired private RSChemElementManager rsChemElementManager;
  @Autowired private SubSampleApiManager subSampleApiMgr;

  @Test
  public void createUpdateDeleteLink() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "some text");
    Field field = doc.getFields().get(0);
    RSChemElement reaction = addReactionToField(field, user);

    Stoichiometry stoich =
        stoichiometryManager.createFromAnalysis(createSimpleAnalysis(), reaction, user);
    StoichiometryMolecule molecule = stoich.getMolecules().get(0);

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setStoichiometryMoleculeId(molecule.getId());
    req.setInventoryItemGlobalId(sample.getGlobalId());
    req.setQuantity(BigDecimal.valueOf(1.5));
    req.setUnitId(RSUnitDef.MILLI_LITRE.getId());
    req.setReducesStock(false);

    // Create
    StoichiometryInventoryLinkDTO createdLink = linkManager.createLink(req, user);
    assertNotNull(createdLink.getId());
    assertEquals(molecule.getId(), createdLink.getStoichiometryMoleculeId());
    assertEquals(sample.getGlobalId(), createdLink.getInventoryItemGlobalId());
    assertEquals(BigDecimal.valueOf(1.5), createdLink.getQuantity().getNumericValue());

    // Retrieve
    StoichiometryInventoryLinkDTO retrieved = linkManager.getById(createdLink.getId(), user);
    assertEquals(createdLink.getId(), retrieved.getId());
    assertEquals(molecule.getId(), retrieved.getStoichiometryMoleculeId());
    assertEquals(sample.getGlobalId(), retrieved.getInventoryItemGlobalId());
    assertEquals(BigDecimal.valueOf(1.5), retrieved.getQuantity().getNumericValue());

    // Update quantity
    StoichiometryLinkQuantityUpdateRequest update = new StoichiometryLinkQuantityUpdateRequest();
    update.setStoichiometryLinkId(createdLink.getId());
    update.setNewQuantity(
        new ApiQuantityInfo(BigDecimal.valueOf(5), RSUnitDef.MILLI_LITRE.getId()));
    StoichiometryInventoryLinkDTO updated =
        linkManager.updateQuantity(
            update.getStoichiometryLinkId(), update.getNewQuantity(), false, user);
    assertEquals(BigDecimal.valueOf(5), updated.getQuantity().getNumericValue());
    assertEquals(RSUnitDef.MILLI_LITRE.getId(), updated.getQuantity().getUnitId());

    // Delete
    linkManager.deleteLink(createdLink.getId(), user);
    Long id = createdLink.getId();
    assertThrows(NotFoundException.class, () -> linkManager.getById(id, user));
  }

  @Test
  public void createLinkToSubSampleReducesStock() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "some text");
    Field field = doc.getFields().get(0);
    RSChemElement reaction = addReactionToField(field, user);

    Stoichiometry stoich =
        stoichiometryManager.createFromAnalysis(createSimpleAnalysis(), reaction, user);
    StoichiometryMolecule molecule = stoich.getMolecules().get(0);

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    ApiSubSampleInfo subInfo = sample.getSubSamples().get(0);

    ApiSubSample sub = subSampleApiMgr.getApiSubSampleById(subInfo.getId(), user);
    assertEquals("5 g", sub.getQuantity().toQuantityInfo().toPlainString());

    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setStoichiometryMoleculeId(molecule.getId());
    req.setInventoryItemGlobalId(subInfo.getGlobalId());
    req.setQuantity(BigDecimal.TEN);
    req.setUnitId(RSUnitDef.MILLI_GRAM.getId());
    req.setReducesStock(true);

    StoichiometryInventoryLinkDTO createdLink = linkManager.createLink(req, user);
    assertNotNull(createdLink.getId());

    ApiSubSample after = subSampleApiMgr.getApiSubSampleById(subInfo.getId(), user);
    // 5 g - 10 mg = 4.99 g
    assertEquals("4.99 g", after.getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void updateQuantityReducesStock() throws Exception {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "some text");
    Field field = doc.getFields().get(0);
    RSChemElement reaction = addReactionToField(field, user);

    Stoichiometry stoich =
        stoichiometryManager.createFromAnalysis(createSimpleAnalysis(), reaction, user);
    StoichiometryMolecule molecule = stoich.getMolecules().get(0);

    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    ApiSubSample subInfo = sample.getSubSamples().get(0);

    ApiSubSample subBefore = subSampleApiMgr.getApiSubSampleById(subInfo.getId(), user);
    assertEquals("5 g", subBefore.getQuantity().toQuantityInfo().toPlainString());

    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setStoichiometryMoleculeId(molecule.getId());
    req.setInventoryItemGlobalId(subInfo.getGlobalId());
    req.setQuantity(BigDecimal.TEN);
    req.setUnitId(RSUnitDef.MILLI_GRAM.getId());
    req.setReducesStock(true);
    StoichiometryInventoryLinkDTO link = linkManager.createLink(req, user);

    StoichiometryLinkQuantityUpdateRequest upd = new StoichiometryLinkQuantityUpdateRequest();
    upd.setStoichiometryLinkId(link.getId());
    upd.setNewQuantity(new ApiQuantityInfo(BigDecimal.valueOf(20), RSUnitDef.MILLI_GRAM.getId()));
    linkManager.updateQuantity(upd.getStoichiometryLinkId(), upd.getNewQuantity(), true, user);

    ApiSubSample after = subSampleApiMgr.getApiSubSampleById(subInfo.getId(), user);
    assertEquals("4.97 g", after.getQuantity().toQuantityInfo().toPlainString());
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
