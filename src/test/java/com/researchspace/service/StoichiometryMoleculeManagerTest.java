package com.researchspace.service;

import static com.researchspace.testutils.RSpaceTestUtils.getChemImage;
import static org.junit.Assert.assertEquals;

import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class StoichiometryMoleculeManagerTest extends SpringTransactionalTest {

  @Autowired private StoichiometryManager stoichiometryManager;
  @Autowired private RSChemElementManager rsChemElementManager;
  @Autowired private StoichiometryMoleculeManager stoichiometryMoleculeManager;

  private User user;

  @Before
  public void init() throws Exception {
    user = createInitAndLoginAnyUser();
  }

  @Test
  public void getMoleculeByIdReturnsMolecule() throws Exception {
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "some text");
    RSChemElement reaction = addReactionToField(doc.getFields().get(0), user);
    Stoichiometry stoich =
        stoichiometryManager.createFromAnalysis(createSimpleAnalysis(), reaction, user);
    Long createdId = stoich.getMolecules().get(0).getId();
    StoichiometryMolecule retrieved = stoichiometryMoleculeManager.getById(createdId);
    assertEquals(createdId, retrieved.getId());
  }

  @Test
  public void getDocContainingMoleculeReturnsOwningDocument() throws Exception {
    StructuredDocument createdDoc = createBasicDocumentInRootFolderWithText(user, "some text");
    RSChemElement reaction = addReactionToField(createdDoc.getFields().get(0), user);

    Stoichiometry stoich =
        stoichiometryManager.createFromAnalysis(createSimpleAnalysis(), reaction, user);
    StoichiometryMolecule molecule = stoich.getMolecules().get(0);
    BaseRecord retrievedDoc = stoichiometryMoleculeManager.getDocContainingMolecule(molecule);
    assertEquals(createdDoc.getId(), retrievedDoc.getId());
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
    String imageBytes = getChemImage();
    ChemicalDataDTO chemicalData =
        ChemicalDataDTO.builder()
            .chemElements(reactionString)
            .fieldId(field.getId())
            .imageBase64(imageBytes)
            .fieldId(field.getId())
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
