package com.researchspace.webapp.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.controller.API_MVC_TestBase;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMapper;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeUpdateDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
@TestPropertySource(
    properties = {"chemistry.service.url=http://localhost:8090", "chemistry.provider=indigo"})
@Ignore(
    "Requires chemistry service to run. See"
        + " https://documentation.researchspace.com/article/1jbygguzoa")
public class StoichiometryControllerIT extends API_MVC_TestBase {

  private Principal principal;
  private User user;
  private StructuredDocument doc1;
  private String apiKey;

  private static final String URL = "/api/v1/stoichiometry";

  @Before
  public void setup() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    principal = new MockPrincipal(user.getUsername());
    apiKey = createNewApiKeyForUser(user);
  }

  @Test
  public void testGetMoleculeInfoBySmiles() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post(URL + "/molecule/info")
                    .content("{\"chemical\": \"CCC\"}")
                    .contentType(APPLICATION_JSON)
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();

    StoichiometryMoleculeDTO molecule =
        getFromJsonResponseBody(result, StoichiometryMoleculeDTO.class);
    assertEquals("CCC", molecule.getSmiles());
    assertEquals("C3 H8", molecule.getFormula());
    assertEquals(44.1, molecule.getMolecularWeight(), 0.01);
    // should be null as entities haven't been saved yet
    assertNull(molecule.getId());
    assertNull(molecule.getRsChemElementId());
  }

  @Test
  public void testGetStoichiometry() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);

    RSChemElement reaction = addReactionToField(docField, user);

    MvcResult createResult = createStoichiometry(reaction);

    StoichiometryDTO createdStoichiometry =
        getFromJsonResponseBody(createResult, StoichiometryDTO.class);
    assertNotNull(createdStoichiometry.getId());
    assertEquals(reaction.getId(), createdStoichiometry.getParentReactionId());

    MvcResult getResult =
        mockMvc
            .perform(
                get(URL)
                    .param("chemId", reaction.getId().toString())
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();

    StoichiometryDTO retrievedStoichiometry =
        getFromJsonResponseBody(getResult, StoichiometryDTO.class);
    assertEquals(createdStoichiometry.getId(), retrievedStoichiometry.getId());
    assertEquals(reaction.getId(), retrievedStoichiometry.getParentReactionId());
  }

  @Test
  public void testSaveStoichiometryAlreadyExists() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);

    RSChemElement reaction = addReactionToField(docField, user);

    createStoichiometry(reaction);
    // attempt to create stoichiometry again for the same reaction in the same field
    MvcResult failResult = createStoichiometry(reaction);

    String responseContent = failResult.getResponse().getContentAsString();
    assertEquals(HttpStatus.BAD_REQUEST.value(), failResult.getResponse().getStatus());
    assertTrue(responseContent.contains("Stoichiometry already exists for reaction chemId="));
  }

  @Test
  public void testUpdateStoichiometry() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);

    RSChemElement reaction = addReactionToField(docField, user);

    MvcResult createResult = createStoichiometry(reaction);

    StoichiometryDTO createdStoichiometry =
        getFromJsonResponseBody(createResult, StoichiometryDTO.class);

    StoichiometryMoleculeDTO molecule = createdStoichiometry.getMolecules().get(0);

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(createdStoichiometry.getId());

    StoichiometryMoleculeUpdateDTO updatedMolecule = new StoichiometryMoleculeUpdateDTO();
    updatedMolecule.setId(molecule.getId());
    updatedMolecule.setCoefficient(2.0);
    updatedMolecule.setMass(100.0);
    updatedMolecule.setActualAmount(200.0);
    updatedMolecule.setActualYield(90.0);
    updatedMolecule.setLimitingReagent(true);
    updatedMolecule.setNotes("Updated notes");
    updatedMolecule.setRole(MoleculeRole.REACTANT);

    updateDTO.setMolecules(List.of(updatedMolecule));

    MvcResult updateResult =
        mockMvc
            .perform(
                put(URL)
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateDTO))
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();

    StoichiometryDTO updatedStoichiometry =
        getFromJsonResponseBody(updateResult, StoichiometryDTO.class);
    assertEquals(createdStoichiometry.getId(), updatedStoichiometry.getId());

    Optional<StoichiometryMoleculeDTO> foundMolecule =
        updatedStoichiometry.getMolecules().stream()
            .filter(m -> m.getId().equals(molecule.getId()))
            .findFirst();

    StoichiometryMoleculeDTO updatedMol = foundMolecule.get();

    assertEquals(2.0, updatedMol.getCoefficient());
    assertEquals(100.0, updatedMol.getMass());
    assertEquals(200.0, updatedMol.getActualAmount());
    assertEquals(90.0, updatedMol.getActualYield());
    assertTrue(updatedMol.getLimitingReagent());
    assertEquals("Updated notes", updatedMol.getNotes());
  }

  @Test
  public void testDeleteStoichiometry() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);

    RSChemElement reaction = addReactionToField(docField, user);

    MvcResult createResult = createStoichiometry(reaction);

    StoichiometryDTO createdStoichiometry =
        getFromJsonResponseBody(createResult, StoichiometryDTO.class);

    MvcResult deleteResult =
        mockMvc
            .perform(
                delete(URL)
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    boolean deleteSuccess = getFromJsonResponseBody(deleteResult, Boolean.class);
    assertTrue(deleteSuccess);

    // check it no longer exists
    MvcResult getResult =
        mockMvc
            .perform(
                get(URL)
                    .param("chemId", reaction.getId().toString())
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isNotFound())
            .andReturn();

    String getResponseContent = getResult.getResponse().getContentAsString();
    assertTrue(getResponseContent.contains("No stoichiometry found"));
  }

  @Test
  public void addAgentToExistingStoichiometry_addsAgent() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);

    RSChemElement reaction = addReactionToField(docField, user);

    // create initial stoichiometry
    MvcResult createResult = createStoichiometry(reaction);
    StoichiometryDTO createdStoichiometry =
        getFromJsonResponseBody(createResult, StoichiometryDTO.class);

    // add an agent
    StoichiometryMoleculeUpdateDTO newAgent = new StoichiometryMoleculeUpdateDTO();
    newAgent.setRole(com.researchspace.model.stoichiometry.MoleculeRole.AGENT);
    newAgent.setSmiles("CCO");
    newAgent.setName("Ethanol");
    newAgent.setCoefficient(1.0);

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(createdStoichiometry.getId());
    List<StoichiometryMoleculeUpdateDTO> existingMols =
        com.researchspace.model.dtos.chemistry.StoichiometryMapper.toUpdateDTOs(
            createdStoichiometry.getMolecules());
    existingMols.add(newAgent);
    updateDTO.setMolecules(existingMols);

    MvcResult updateResult =
        mockMvc
            .perform(
                put(URL)
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateDTO))
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();

    // check agent added
    StoichiometryDTO updatedAfterAdd =
        getFromJsonResponseBody(updateResult, StoichiometryDTO.class);
    assertEquals(4, updatedAfterAdd.getMolecules().size());
    StoichiometryMoleculeDTO agent =
        updatedAfterAdd.getMolecules().stream()
            .filter(m -> "CCO".equals(m.getSmiles()))
            .findFirst()
            .get();
    assertEquals("Ethanol", agent.getName());
    assertEquals(MoleculeRole.AGENT, agent.getRole());
  }

  @Test
  public void removeAgentFromExistingStoichiometry_removesAgent() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);

    RSChemElement reaction = addReactionToField(docField, user);

    MvcResult createResult = createStoichiometry(reaction);
    StoichiometryDTO createdStoichiometry =
        getFromJsonResponseBody(createResult, StoichiometryDTO.class);

    int originalCount = createdStoichiometry.getMolecules().size();
    StoichiometryMoleculeUpdateDTO newAgent = new StoichiometryMoleculeUpdateDTO();
    newAgent.setRole(com.researchspace.model.stoichiometry.MoleculeRole.AGENT);
    newAgent.setSmiles("CCO");
    newAgent.setName("Ethanol");
    newAgent.setCoefficient(1.0);

    StoichiometryUpdateDTO addDTO = new StoichiometryUpdateDTO();
    addDTO.setId(createdStoichiometry.getId());
    List<StoichiometryMoleculeUpdateDTO> existingMols =
        StoichiometryMapper.toUpdateDTOs(createdStoichiometry.getMolecules());
    existingMols.add(newAgent);
    addDTO.setMolecules(existingMols);

    // add new agent
    MvcResult addResult =
        mockMvc
            .perform(
                put(URL)
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(addDTO))
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();

    StoichiometryDTO afterAdd = getFromJsonResponseBody(addResult, StoichiometryDTO.class);
    assertEquals(originalCount + 1, afterAdd.getMolecules().size());

    // Remove the added agent by keeping only the original molecule(s)
    StoichiometryUpdateDTO deleteAgentDTO = new StoichiometryUpdateDTO();
    deleteAgentDTO.setId(createdStoichiometry.getId());
    List<StoichiometryMoleculeUpdateDTO> keepMolecules =
        com.researchspace.model.dtos.chemistry.StoichiometryMapper.toUpdateDTOs(
                afterAdd.getMolecules())
            .stream()
            .filter(m -> !"CCO".equals(m.getSmiles()))
            .collect(java.util.stream.Collectors.toList());
    deleteAgentDTO.setMolecules(keepMolecules);

    MvcResult removeAgentResult =
        mockMvc
            .perform(
                put(URL)
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(deleteAgentDTO))
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();

    StoichiometryDTO updatedAfterDelete =
        getFromJsonResponseBody(removeAgentResult, StoichiometryDTO.class);
    assertEquals(originalCount, updatedAfterDelete.getMolecules().size());
    assertFalse(
        updatedAfterDelete.getMolecules().stream().anyMatch(m -> "CCO".equals(m.getSmiles())));
  }

  @Test
  public void updateStoichiometry_withUnknownMoleculeId_returnsError() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);
    RSChemElement reaction = addReactionToField(docField, user);
    MvcResult createResult = createStoichiometry(reaction);
    StoichiometryDTO createdStoichiometry =
        getFromJsonResponseBody(createResult, StoichiometryDTO.class);

    StoichiometryMoleculeUpdateDTO bogusUpdate = new StoichiometryMoleculeUpdateDTO();
    bogusUpdate.setId(-999L);
    bogusUpdate.setCoefficient(1.0);

    List<StoichiometryMoleculeUpdateDTO> updates =
        StoichiometryMapper.toUpdateDTOs(createdStoichiometry.getMolecules());
    updates.add(bogusUpdate);

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(createdStoichiometry.getId());
    updateDTO.setMolecules(updates);

    MvcResult result =
        mockMvc
            .perform(
                put(URL)
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateDTO))
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isBadRequest())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("Molecule ID -999 not found in existing stoichiometry molecules"));
  }

  @Test
  public void updateStoichiometry_addNonAgentWithoutId_addsMoleculeWhenInfoProvided()
      throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);
    RSChemElement reaction = addReactionToField(docField, user);
    MvcResult createResult = createStoichiometry(reaction);
    StoichiometryDTO created = getFromJsonResponseBody(createResult, StoichiometryDTO.class);

    StoichiometryMoleculeUpdateDTO newNonAgent = new StoichiometryMoleculeUpdateDTO();
    newNonAgent.setRole(MoleculeRole.REACTANT);
    newNonAgent.setSmiles("CC");
    newNonAgent.setName("Ethane");
    newNonAgent.setCoefficient(1.0);
    newNonAgent.setMolecularWeight(30.07);
    newNonAgent.setFormula("C2H6");

    List<StoichiometryMoleculeUpdateDTO> updates =
        StoichiometryMapper.toUpdateDTOs(created.getMolecules());
    updates.add(newNonAgent);

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(created.getId());
    updateDTO.setMolecules(updates);

    MvcResult result =
        mockMvc
            .perform(
                put(URL)
                    .param("stoichiometryId", created.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateDTO))
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isOk())
            .andReturn();

    StoichiometryDTO afterUpdate = getFromJsonResponseBody(result, StoichiometryDTO.class);
    assertTrue(afterUpdate.getMolecules().stream().anyMatch(m -> "CC".equals(m.getSmiles())));
  }

  @Test
  public void updateStoichiometry_addAgentWithoutSmiles_returnsError() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);
    RSChemElement reaction = addReactionToField(docField, user);
    MvcResult createResult = createStoichiometry(reaction);
    StoichiometryDTO created = getFromJsonResponseBody(createResult, StoichiometryDTO.class);

    StoichiometryMoleculeUpdateDTO agentMissingSmiles = new StoichiometryMoleculeUpdateDTO();
    agentMissingSmiles.setRole(MoleculeRole.AGENT);
    agentMissingSmiles.setName("Some Agent");

    List<StoichiometryMoleculeUpdateDTO> updates =
        StoichiometryMapper.toUpdateDTOs(created.getMolecules());
    updates.add(agentMissingSmiles);

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(created.getId());
    updateDTO.setMolecules(updates);

    MvcResult result =
        mockMvc
            .perform(
                put(URL)
                    .param("stoichiometryId", created.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateDTO))
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isBadRequest())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("New molecule requires a SMILES string"));
  }

  @Test
  public void deleteStoichiometry_withNonexistentId_returnsError() throws Exception {
    String missingId = String.valueOf(-999L);
    MvcResult result =
        mockMvc
            .perform(
                delete(URL)
                    .param("stoichiometryId", missingId)
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andExpect(status().isInternalServerError())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("Object of class [com.researchspace.model.stoichiometry.Stoichiometry] with identifier [-999]: not found"));
  }

  @Test
  public void updateStoichiometry_withInvalidStoichiometryId_returnsBadRequest() throws Exception {
    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(-999L);
    updateDTO.setMolecules(List.of());

    mockMvc
        .perform(
            put(URL)
                .param("stoichiometryId", "-999")
                .contentType(APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(updateDTO))
                .principal(principal)
                .header("apiKey", apiKey))
        .andExpect(status().isInternalServerError());
  }

  @Test
  public void createStoichiometry_withInvalidChemId_returnsBadRequest() throws Exception {
    MvcResult result = mockMvc
        .perform(post(URL).param("chemId", "-999").principal(principal).header("apiKey", apiKey))
        .andExpect(status().isInternalServerError())
        .andReturn();

    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("Object of class [com.researchspace.model.RSChemElement] with identifier [-999]: not found"));
  }

  private MvcResult createStoichiometry(RSChemElement reaction) throws Exception {
    return
        mockMvc
            .perform(
                post(URL)
                    .param("chemId", reaction.getId().toString())
                    .principal(principal)
                    .header("apiKey", apiKey))
            .andReturn();
  }

  private RSChemElement addReactionToField(Field field, User owner) throws IOException {
    String reactionString = "C1C=CC=CC=1.C1C=CC=C1>>C1CCCCC1";
    String imageBytes = RSpaceTestUtils.getChemImage();
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
    recordMgr.save(field.getStructuredDocument(), user);

    return chem;
  }
}
