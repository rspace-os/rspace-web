package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.stoichiometry.StockDeductionResult;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkDTO;
import com.researchspace.api.v1.model.stoichiometry.StoichiometryInventoryLinkRequest;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.MoleculeInfoDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeUpdateDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
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
    req.setInventoryItemGlobalId(sample.getGlobalId());

    // creation should create a new Stoichiometry revision
    long revisionBeforeCreateLink = getLatestStoichiometryRevisionId(molecule);

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(molecule.getStoichiometry().getId());
    StoichiometryMoleculeUpdateDTO molUpdate =
        com.researchspace.model.dtos.chemistry.StoichiometryMapper.toUpdateDTO(
            com.researchspace.model.dtos.chemistry.StoichiometryMapper.moleculeToDTO(molecule));
    molUpdate.setInventoryLink(req);
    updateDTO.setMolecules(List.of(molUpdate));

    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPut(API_VERSION.ONE, apiKey, "/stoichiometry", user)
                    .param("stoichiometryId", molecule.getStoichiometry().getId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(updateDTO)))
            .andExpect(status().isOk())
            .andReturn();

    StoichiometryDTO stoich = getFromJsonResponseBody(createResult, StoichiometryDTO.class);
    StoichiometryInventoryLinkDTO created = stoich.getMolecules().get(0).getInventoryLink();
    assertNotNull(created);

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

    StoichiometryInventoryLinkDTO created = createLink(user, apiKey, molecule, sample);

    MvcResult getResult =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/stoichiometry", user)
                    .param("stoichiometryId", molecule.getStoichiometry().getId().toString()))
            .andExpect(status().isOk())
            .andReturn();
    StoichiometryDTO retrievedStoich = getFromJsonResponseBody(getResult, StoichiometryDTO.class);
    StoichiometryInventoryLinkDTO retrieved =
        retrievedStoich.getMolecules().stream()
            .filter(m -> m.getId().equals(molecule.getId()))
            .findFirst()
            .get()
            .getInventoryLink();

    assertEquals(created.getId(), retrieved.getId());
  }

  @Test
  public void getNonExistentStoichiometryThrows404() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/stoichiometry", user)
                    .param("stoichiometryId", "987654321"))
            .andExpect(status().isInternalServerError())
            .andReturn();
  }

  @Test
  public void updateLinkTargetSuccess() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich update");
    ApiSampleWithFullSubSamples sample1 = createBasicSampleForUser(user);
    StoichiometryInventoryLinkDTO created = createLink(user, apiKey, molecule, sample1);

    ApiSampleWithFullSubSamples sample2 = createBasicSampleForUser(user);
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId(sample2.getGlobalId());

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(molecule.getStoichiometry().getId());
    StoichiometryMoleculeUpdateDTO molUpdate =
        com.researchspace.model.dtos.chemistry.StoichiometryMapper.toUpdateDTO(
            com.researchspace.model.dtos.chemistry.StoichiometryMapper.moleculeToDTO(molecule));
    molUpdate.setInventoryLink(req);
    updateDTO.setMolecules(List.of(molUpdate));

    MvcResult updateResult =
        mockMvc
            .perform(
                createBuilderForPut(API_VERSION.ONE, apiKey, "/stoichiometry", user)
                    .param("stoichiometryId", molecule.getStoichiometry().getId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(updateDTO)))
            .andExpect(status().isOk())
            .andReturn();
    StoichiometryDTO updatedStoich = getFromJsonResponseBody(updateResult, StoichiometryDTO.class);
    StoichiometryInventoryLinkDTO updated = updatedStoich.getMolecules().get(0).getInventoryLink();
    assertEquals(sample2.getGlobalId(), updated.getInventoryItemGlobalId());
  }

  @Test
  public void updateLinkWithoutWritePermissionsThrows401() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich update 401");
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    StoichiometryInventoryLinkDTO created = createLink(user, apiKey, molecule, sample);

    User otherUser = createInitAndLoginAnyUser();
    String otherUserKey = createNewApiKeyForUser(otherUser);

    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId(sample.getGlobalId());

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(molecule.getStoichiometry().getId());
    StoichiometryMoleculeUpdateDTO molUpdate =
        com.researchspace.model.dtos.chemistry.StoichiometryMapper.toUpdateDTO(
            com.researchspace.model.dtos.chemistry.StoichiometryMapper.moleculeToDTO(molecule));
    molUpdate.setInventoryLink(req);
    updateDTO.setMolecules(List.of(molUpdate));

    mockMvc
        .perform(
            createBuilderForPut(API_VERSION.ONE, otherUserKey, "/stoichiometry", otherUser)
                .param("stoichiometryId", molecule.getStoichiometry().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(updateDTO)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  public void deleteSuccess() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich delete");
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    StoichiometryInventoryLinkDTO created = createLink(user, apiKey, molecule, sample);

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(molecule.getStoichiometry().getId());
    StoichiometryMoleculeUpdateDTO molUpdate =
        com.researchspace.model.dtos.chemistry.StoichiometryMapper.toUpdateDTO(
            com.researchspace.model.dtos.chemistry.StoichiometryMapper.moleculeToDTO(molecule));
    molUpdate.setInventoryLink(null);
    updateDTO.setMolecules(List.of(molUpdate));

    mockMvc
        .perform(
            createBuilderForPut(API_VERSION.ONE, apiKey, "/stoichiometry", user)
                .param("stoichiometryId", molecule.getStoichiometry().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(updateDTO)))
        .andExpect(status().isOk());

    MvcResult afterDelete =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/stoichiometry", user)
                    .param("stoichiometryId", molecule.getStoichiometry().getId().toString()))
            .andExpect(status().isOk())
            .andReturn();
    StoichiometryDTO retrievedStoich = getFromJsonResponseBody(afterDelete, StoichiometryDTO.class);
    assertNull(retrievedStoich.getMolecules().get(0).getInventoryLink());
  }

  @Test
  public void deleteWithoutWritePermissionsThrows401() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich delete 401");
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    StoichiometryInventoryLinkDTO created = createLink(user, apiKey, molecule, sample);

    User attacker = createInitAndLoginAnyUser();
    String attackerKey = createNewApiKeyForUser(attacker);

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(molecule.getStoichiometry().getId());
    StoichiometryMoleculeUpdateDTO molUpdate =
        com.researchspace.model.dtos.chemistry.StoichiometryMapper.toUpdateDTO(
            com.researchspace.model.dtos.chemistry.StoichiometryMapper.moleculeToDTO(molecule));
    molUpdate.setInventoryLink(null);
    updateDTO.setMolecules(List.of(molUpdate));

    mockMvc
        .perform(
            createBuilderForPut(API_VERSION.ONE, attackerKey, "/stoichiometry", attacker)
                .param("stoichiometryId", molecule.getStoichiometry().getId().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsBytes(updateDTO)))
        .andExpect(status().isUnauthorized());
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

  private StoichiometryMolecule createSingleMoleculeStoichiometry(User user, String docTitle)
      throws Exception {
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, docTitle);
    Field field = doc.getFields().get(0);
    RSChemElement reaction = addReactionToField(field, user);

    Stoichiometry stoich =
        stoichiometryManager.createFromAnalysis(createSimpleAnalysis(), reaction, doc, user);
    return stoich.getMolecules().get(0);
  }

  private StoichiometryInventoryLinkDTO createLink(
      User owner,
      String ownerApiKey,
      StoichiometryMolecule molecule,
      ApiSampleWithFullSubSamples sample)
      throws Exception {
    StoichiometryInventoryLinkRequest req = new StoichiometryInventoryLinkRequest();
    req.setInventoryItemGlobalId(sample.getGlobalId());

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(molecule.getStoichiometry().getId());
    StoichiometryMoleculeUpdateDTO molUpdate =
        com.researchspace.model.dtos.chemistry.StoichiometryMapper.toUpdateDTO(
            com.researchspace.model.dtos.chemistry.StoichiometryMapper.moleculeToDTO(molecule));
    molUpdate.setInventoryLink(req);
    updateDTO.setMolecules(List.of(molUpdate));

    MvcResult createResult =
        mockMvc
            .perform(
                createBuilderForPut(API_VERSION.ONE, ownerApiKey, "/stoichiometry", owner)
                    .param("stoichiometryId", molecule.getStoichiometry().getId().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(updateDTO)))
            .andExpect(status().isOk())
            .andReturn();
    StoichiometryDTO stoich = getFromJsonResponseBody(createResult, StoichiometryDTO.class);
    return stoich.getMolecules().stream()
        .filter(m -> m.getId().equals(molecule.getId()))
        .findFirst()
        .get()
        .getInventoryLink();
  }

  @Test
  public void deductStockSuccess() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich deduct");
    molecule.setActualAmount(1.0);
    stoichiometryManager.save(molecule.getStoichiometry());
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    StoichiometryInventoryLinkDTO link = createLink(user, apiKey, molecule, sample);

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                        API_VERSION.ONE, apiKey, "/stoichiometry/link/deductStock", user)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(List.of(link.getId()))))
            .andExpect(status().isOk())
            .andReturn();

    StockDeductionResult reductionResult =
        getFromJsonResponseBody(result, StockDeductionResult.class);
    assertEquals(1, reductionResult.getResults().size());
    assertTrue(reductionResult.getResults().get(0).isSuccess());
    assertEquals(link.getId(), reductionResult.getResults().get(0).getLinkId());

    MvcResult getResult =
        mockMvc
            .perform(
                createBuilderForGet(API_VERSION.ONE, apiKey, "/stoichiometry", user)
                    .param("stoichiometryId", molecule.getStoichiometry().getId().toString()))
            .andExpect(status().isOk())
            .andReturn();
    StoichiometryDTO retrievedStoich = getFromJsonResponseBody(getResult, StoichiometryDTO.class);
    StoichiometryInventoryLinkDTO retrieved =
        retrievedStoich.getMolecules().get(0).getInventoryLink();
    assertTrue(retrieved.isStockDeducted());
  }

  @Test
  public void deductStockPartialSuccess() throws Exception {
    StoichiometryMolecule molecule = createSingleMoleculeStoichiometry(user, "stoich partial");
    molecule.setActualAmount(1.0);
    stoichiometryManager.save(molecule.getStoichiometry());
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    StoichiometryInventoryLinkDTO link = createLink(user, apiKey, molecule, sample);

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                        API_VERSION.ONE, apiKey, "/stoichiometry/link/deductStock", user)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsBytes(List.of(link.getId(), 999999L))))
            .andExpect(status().isOk())
            .andReturn();

    StockDeductionResult reductionResult =
        getFromJsonResponseBody(result, StockDeductionResult.class);
    assertEquals(2, reductionResult.getResults().size());
    assertTrue(
        reductionResult.getResults().stream()
            .anyMatch(r -> r.getLinkId().equals(link.getId()) && r.isSuccess()));
    assertTrue(
        reductionResult.getResults().stream()
            .anyMatch(r -> r.getLinkId().equals(999999L) && !r.isSuccess()));
  }
}
