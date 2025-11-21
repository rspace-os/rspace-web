package com.researchspace.api.v1.controller.stoichiometry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.api.v1.controller.API_VERSION;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
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
import com.researchspace.service.AuditManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.StoichiometryManager;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

public class StoichiometryInventoryLinkControllerMVCIT extends API_MVC_TestBase {
  @Autowired private StoichiometryManager stoichiometryManager;
  @Autowired private RSChemElementManager rsChemElementManager;

  private User user;
  private String apiKey;

  private final ObjectMapper mapper = new ObjectMapper();
  @Autowired private AuditManager auditManager;

  @Before
  public void setup() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    apiKey = createNewApiKeyForUser(user);
  }

  @Test
  public void createLinkSuccess() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich link");
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setStoichiometryMoleculeId(molecule.getId());
    req.setInventoryItemGlobalId(sample.getGlobalId());
    req.setQuantityUsed(1.23);

    // creation should create a new Stoichiometry revision
    long revisionBeforeCreateLink = getLatestStoichiometryRevisionId(molecule);

    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/stoichiometry/link", user)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(req)))
            .andExpect(status().isOk())
            .andReturn();

    StoichiometryInventoryLinkDTO created =
        getFromJsonResponseBody(createResult, StoichiometryInventoryLinkDTO.class);
    assertEquals(molecule.getId(), created.getStoichiometryMoleculeId());
    assertEquals(Double.valueOf(1.23), created.getQuantityUsed());

    long latestRevision = getLatestStoichiometryRevisionId(molecule);
    assertTrue(latestRevision > revisionBeforeCreateLink);
  }

  private long getLatestStoichiometryRevisionId(StoichiometryMolecule molecule) {
    return auditManager
        .getNewestRevisionForEntity(Stoichiometry.class, molecule.getStoichiometry().getId())
        .getRevision()
        .longValue();
  }

  @Test
  public void getLinkSuccess() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich get");
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setStoichiometryMoleculeId(molecule.getId());
    req.setInventoryItemGlobalId(sample.getGlobalId());
    req.setQuantityUsed(2.0);

    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/stoichiometry/link", user)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(req)))
            .andExpect(status().isOk())
            .andReturn();

    StoichiometryInventoryLinkDTO created =
        getFromJsonResponseBody(createResult, StoichiometryInventoryLinkDTO.class);

    MvcResult getResult =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/stoichiometry/link/{id}", user, created.getId()))
            .andExpect(status().isOk())
            .andReturn();
    StoichiometryInventoryLinkDTO retrieved =
        getFromJsonResponseBody(getResult, StoichiometryInventoryLinkDTO.class);
    assertEquals(created.getId(), retrieved.getId());
    assertEquals(created.getQuantityUsed(), retrieved.getQuantityUsed());
    assertEquals(created.getStoichiometryMoleculeId(), retrieved.getStoichiometryMoleculeId());
  }

  @Test
  public void getNonExistentLinkThrows404() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/stoichiometry/link/{id}", user, 987654321L))
            .andExpect(status().isNotFound())
            .andReturn();
    String body = res.getResponse().getContentAsString();
    assertTrue(body.contains("not found"));
  }

  @Test
  public void updateQuantityUsedSuccess() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich update");
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);

    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setStoichiometryMoleculeId(molecule.getId());
    req.setInventoryItemGlobalId(sample.getGlobalId());
    req.setQuantityUsed(1.0);

    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, apiKey, "/stoichiometry/link", user)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(req)))
            .andExpect(status().isOk())
            .andReturn();
    StoichiometryInventoryLinkDTO created =
        getFromJsonResponseBody(createResult, StoichiometryInventoryLinkDTO.class);

    StoichiometryLinkQuantityUpdateRequest upd = new StoichiometryLinkQuantityUpdateRequest();
    upd.setStoichiometryLinkId(created.getId());
    upd.setNewQuantity(9.5);

    MvcResult updateResult =
        mockMvc
            .perform(
                createBuilderForPut(API_VERSION.ONE, apiKey, "/stoichiometry/link", user)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(upd)))
            .andExpect(status().isOk())
            .andReturn();
    StoichiometryInventoryLinkDTO updated =
        getFromJsonResponseBody(updateResult, StoichiometryInventoryLinkDTO.class);
    assertEquals(Double.valueOf(9.5), updated.getQuantityUsed());
  }

  @Test
  public void updateQuantityUsedWithoutWritePermissionsThrows401() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich update 401");
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    StoichiometryInventoryLinkDTO created = createLink(user, apiKey, molecule, sample, 1.0);

    User otherUser = createInitAndLoginAnyUser();
    String otherUserKey = createNewApiKeyForUser(otherUser);

    StoichiometryLinkQuantityUpdateRequest upd = new StoichiometryLinkQuantityUpdateRequest();
    upd.setStoichiometryLinkId(created.getId());
    upd.setNewQuantity(2.0);

    mockMvc
        .perform(
            createBuilderForPut(API_VERSION.ONE, otherUserKey, "/stoichiometry/link", otherUser)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(upd)))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(createBuilderForDelete(apiKey, "/stoichiometry/link/{id}", user, created.getId()))
        .andExpect(status().isOk());
  }

  @Test
  public void deleteSuccess() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich delete");
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    StoichiometryInventoryLinkDTO created = createLink(user, apiKey, molecule, sample, 4.0);

    mockMvc
        .perform(createBuilderForDelete(apiKey, "/stoichiometry/link/{id}", user, created.getId()))
        .andExpect(status().isOk());

    MvcResult afterDelete =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/stoichiometry/link/{id}", user, created.getId()))
            .andExpect(status().isNotFound())
            .andReturn();
    assertTrue(afterDelete.getResponse().getContentAsString().contains("not found"));
  }

  @Test
  public void deleteWithoutWritePermissionsThrows401() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich delete 401");
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    StoichiometryInventoryLinkDTO created = createLink(user, apiKey, molecule, sample, 1.0);

    User attacker = createInitAndLoginAnyUser();
    String attackerKey = createNewApiKeyForUser(attacker);

    mockMvc
        .perform(
            createBuilderForDelete(
                attackerKey, "/stoichiometry/link/{id}", attacker, created.getId()))
        .andExpect(status().isNotFound());
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

  private StoichiometryMolecule createSingleMoleculeStoichiometry(User user, String docTitle)
      throws Exception {
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, docTitle);
    Field field = doc.getFields().get(0);
    RSChemElement reaction = addReactionToField(field, user);

    Stoichiometry stoich =
        stoichiometryManager.createFromAnalysis(createSimpleAnalysis(), reaction, user);
    return stoich.getMolecules().get(0);
  }

  private StoichiometryInventoryLinkDTO createLink(
      User owner,
      String ownerApiKey,
      StoichiometryMolecule molecule,
      ApiSampleWithFullSubSamples sample,
      double quantity)
      throws Exception {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setStoichiometryMoleculeId(molecule.getId());
    req.setInventoryItemGlobalId(sample.getGlobalId());
    req.setQuantityUsed(quantity);

    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPost(API_VERSION.ONE, ownerApiKey, "/stoichiometry/link", owner)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(req)))
            .andExpect(status().isOk())
            .andReturn();
    return getFromJsonResponseBody(createResult, StoichiometryInventoryLinkDTO.class);
  }
}
