package com.researchspace.webapp.controller;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.ChemConversionInputDto;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ChemicalSearchRequestDTO;
import com.researchspace.model.dtos.chemistry.ConvertedStructureDto;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMapper;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryMoleculeUpdateDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.service.AuditManager;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import com.researchspace.webapp.controller.RSChemController.ChemSearchResultsPage;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.StopWatch;
import org.hamcrest.Matchers;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

// @Ignore(
//    "Requires chemistry service to run. See"
//        + " https://documentation.researchspace.com/article/1jbygguzoa")
@WebAppConfiguration
@RunWith(ConditionalTestRunner.class)
@TestPropertySource(
    properties = {"chemistry.service.url=http://localhost:8090", "chemistry.provider=indigo"})
public class RSChemControllerMVCIT extends MVCTestBase {

  private Principal principal;
  private User user;
  private StructuredDocument doc1;

  @Autowired private FieldParser parser;
  @Autowired private AuditManager auditMgr;

  @Before
  public void setup() throws Exception {
    super.setUp();
    user = createInitAndLoginAnyUser();
    principal = new MockPrincipal(user.getUsername());
  }

  @Test
  public void testConvert() throws Exception {
    String chemdata = RSpaceTestUtils.getMolString("Amfetamine.mol");
    MvcResult result =
        mockMvc
            .perform(postConvertChem(chemdata, "mol", "mrv"))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    ConvertedStructureDto convertedStructureDto =
        getFromJsonResponseBody(result, ConvertedStructureDto.class);
    assertEquals("mrv", convertedStructureDto.getFormat());
    assertFalse(StringUtils.isBlank(convertedStructureDto.getStructure()));
  }

  /**
   * Simulate complete round trip from tinyMCE to create, save, load and update a chem element.
   *
   * @throws Exception
   */
  @Test
  public void testCRUDRoundTrip() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field fld = doc1.getFields().get(0);

    String chemdata = RSpaceTestUtils.getExampleChemString();
    String chemimg = RSpaceTestUtils.getChemImage();

    // we save a new chem element, created in chem editor for example.
    MvcResult result =
        mockMvc
            .perform(postSaveNewChem(fld, chemdata, chemimg))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    RSChemElement savedChemElement = getFromJsonAjaxReturnObject(result, RSChemElement.class);
    // RSPAC-1752
    assertNotNull(savedChemElement.getId());
    assertNotNull(rsChemElementManager.get(savedChemElement.getId()).getCreationDate());

    assertEquals(chemistryProvider.graphicFormat(), savedChemElement.getChemElementsFormat());
    // now we use this data to generate a Chem element String
    String fldData = generateChemElementImageURl(savedChemElement);
    // ..add it tofield
    String newData = fld.getFieldData() + "</br>" + fldData;
    // save/autsave the field
    doAutosaveAndSaveMVC(fld, newData, user);

    // now we'll load the image (
    assertTrue(parser.hasChemElement(savedChemElement.getId(), newData));
    String imgUrl = Jsoup.parse(newData).getElementsByTag("img").attr("src");

    // and retrieve it, shoud get a byte [] of the image
    result = mockMvc.perform(get(imgUrl).principal(principal)).andReturn();
    assertTrue(result.getResponse().getContentAsByteArray().length > 0);

    // now let's simulate loading chem element to edit:
    result =
        mockMvc
            .perform(
                get("/chemical/ajax/loadChemElements")
                    .param("chemId", savedChemElement.getId() + "")
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    String chemStr =
        getFromJsonAjaxReturnObject(result, ChemEditorInputDto.class).getChemElements();
    assertEquals(chemStr, chemdata);

    // and we'll save it back. this time chem Id won't be null.
    chemdata = chemdata + "NEWDATA";
    result =
        mockMvc
            .perform(postUpdateNewChem(fld, chemdata, chemimg, savedChemElement.getId()))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    // update chem element
    savedChemElement = getFromJsonAjaxReturnObject(result, RSChemElement.class);
    assertNotNull(savedChemElement.getId());

    // and retrieve image again, should get a byte [] of the image
    result = mockMvc.perform(get(imgUrl).principal(principal)).andReturn();
    assertTrue(result.getResponse().getContentAsByteArray().length > 0);

    // now lets check we can get the 1st revision:
    List<AuditedEntity<RSChemElement>> revisions =
        auditMgr.getRevisionsForEntity(RSChemElement.class, savedChemElement.getId());
    int EXPECTED_NUM_REVISIONS = 2;
    assertEquals(EXPECTED_NUM_REVISIONS, revisions.size());
    String rev1 = revisions.get(0).getRevision().intValue() + "";
    result =
        mockMvc
            .perform(
                get("/chemical/ajax/loadChemElements")
                    .param("chemId", savedChemElement.getId() + "")
                    .param("revision", rev1)
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    chemStr = getFromJsonAjaxReturnObject(result, ChemEditorInputDto.class).getChemElements();
    assertFalse(chemStr.contains("NEWDATA"));
  }

  private String generateChemElementImageURl(RSChemElement el) {
    return richTextUpdater.generateURLStringForRSChemElementLink(
        el.getId(), el.getParentId(), 50, 50);
  }

  private MockHttpServletRequestBuilder postConvertChem(
      String chemString, String inputFormat, String outputFormat) throws JsonProcessingException {
    ChemConversionInputDto chemConversionInputDto = new ChemConversionInputDto();
    chemConversionInputDto.setStructure(chemString);
    chemConversionInputDto.setInputFormat(inputFormat);
    chemConversionInputDto.setParameters(outputFormat);
    ObjectMapper mapper = new ObjectMapper();
    String requestContent = mapper.writeValueAsString(chemConversionInputDto);
    return post("/chemical/ajax/convert")
        .contentType(APPLICATION_JSON)
        .content(requestContent)
        .principal(principal);
  }

  private MockHttpServletRequestBuilder postSaveNewChem(Field fld, String data, String img) {
    return post("/chemical/ajax/saveChemElement")
        .param("chemElements", data)
        .param("chemElementsFormat", "mol")
        .param("fieldId", fld.getId() + "")
        .param("chemId", "")
        .param("imageBase64", img)
        .principal(principal);
  }

  private MockHttpServletRequestBuilder postUpdateNewChem(
      Field fld, String data, String img, Long chemId) {
    return post("/chemical/ajax/saveChemElement")
        .param("chemElements", data)
        .param("chemElementsFormat", "mol")
        .param("fieldId", fld.getId() + "")
        .param("rsChemElementId", chemId + "")
        .param("imageBase64", img)
        .principal(principal);
  }

  @Test
  public void testGetInfo() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field fld = doc1.getFields().get(0);
    // we save a new chem element, created in chem editor for example.
    RSChemElement element = addChemStructureToField(fld, user);
    MvcResult result =
        mockMvc
            .perform(
                get("/chemical/ajax/getInfo")
                    .param("chemId", element.getId() + "")
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertThat(getJsonPathValue(result, "$.data.reaction"), Matchers.is(false));
    assertEquals(1, getJsonPathValue(result, "$.data.molecules.size()"));
  }

  @Test
  public void testStructureSearchRoundTrip() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field fld = doc1.getFields().get(0);
    String chemdata = RSpaceTestUtils.getExampleChemString();
    // we save a new chem element, created in chem editor for example.
    addChemStructureToField(fld, user);
    ChemicalSearchRequestDTO request =
        new ChemicalSearchRequestDTO(chemdata, 0, 10, "SUBSTRUCTURE");
    MvcResult result =
        mockMvc
            .perform(
                post("/chemical/search")
                    .content(getAsJsonString(request))
                    .principal(principal)
                    .contentType(APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    ChemSearchResultsPage resultsPage =
        (ChemSearchResultsPage) result.getModelAndView().getModel().get("chemSearchResultsPage");
    assertEquals(1, resultsPage.totalHitCount.intValue());
  }

  @Test
  public void testStructureSearchWithChemFileInGallery() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field fld = doc1.getFields().get(0);
    String chemdata = RSpaceTestUtils.getExampleChemString();
    // we save a new chem element, created in chem editor for example and create a new
    // ecatchemistryfile.
    addChemStructureToFieldWithLinkedChemFile(
        addChemistryFileToGallery("Amfetamine.mol", user), fld, user);
    ChemicalSearchRequestDTO request =
        new ChemicalSearchRequestDTO(chemdata, 0, 10, "SUBSTRUCTURE");
    MvcResult result =
        mockMvc
            .perform(
                post("/chemical/search")
                    .content(getAsJsonString(request))
                    .principal(principal)
                    .contentType(APPLICATION_JSON))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNull(result.getResolvedException());
    ChemSearchResultsPage resultsPage =
        (ChemSearchResultsPage) result.getModelAndView().getModel().get("chemSearchResultsPage");
    assertEquals(2, resultsPage.getTotalHitCount().intValue());
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testChemImageCreationDuplication() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field fld = doc1.getFields().get(0);
    String chemdata = RSpaceTestUtils.getExampleChemString();
    // Create and save new chemistry file in gallery
    EcatChemistryFile chemistryFile = addChemistryFileToGallery("Aminoglutethimide.mol", user);
    // Insert same chemistry file twice into same document
    RSChemElement chemElement1 =
        addChemStructureToFieldWithLinkedChemFile(chemistryFile, fld, user);
    RSChemElement chemElement2 =
        addChemStructureToFieldWithLinkedChemFile(chemistryFile, fld, user);
    // Assert the ImageFileProperty between the two chemical elements are the same, i.e
    // a new image is not created when the same chemistry file is inserted multiple times
    assertEquals(chemElement1.getImageFileProperty(), chemElement2.getImageFileProperty());
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void cachingPerformanceOfChemImages() throws Exception {
    final int NUM_CHEMS = 20;
    // set up
    List<StructuredDocument> created = new ArrayList<>();
    List<RSChemElement> chems = new ArrayList<>();
    List<String> urls = new ArrayList<>();
    System.err.println("setting up");
    for (int i = 0; i < NUM_CHEMS; i++) {
      StructuredDocument sd = createBasicDocumentInRootFolderWithText(user, "any");
      created.add(sd);
      RSChemElement chem = addChemStructureToField(sd.getFields().get(0), user);
      chems.add(chem);
      String html = generateChemElementImageURl(chem);
      String src = Jsoup.parse(html).getElementsByTag("img").attr("src");
      urls.add(src);
    }

    // load all elements for the first time
    System.err.println("starting uncached test");
    // load first element before timer starts as that may be possibly slower independent of caching
    mockMvc.perform(get(urls.get(0)).principal(principal)).andReturn();
    StopWatch sw = new StopWatch();
    sw.start();
    for (int i = 0; i < NUM_CHEMS; i++) {
      mockMvc.perform(get(urls.get(i)).principal(principal)).andReturn();
    }
    sw.stop();
    long uncached = sw.getTime();
    System.err.println("uncached time is " + uncached);

    // load all elements for the second time (should be cached)
    System.err.println("starting cached test");
    sw.reset();
    sw.start();
    for (int i = 0; i < NUM_CHEMS; i++) {
      mockMvc.perform(get(urls.get(i)).principal(principal)).andReturn();
    }
    sw.stop();
    long cached = sw.getTime();
    System.err.println("Cached time is " + cached);

    double SPEEDUP = 1.5; // is around 2 on new jenkins, but 1.5 is conservative estimate
    assertTrue(
        "cache speedup should be min 33%, was: " + cached + "-" + uncached,
        cached * SPEEDUP < uncached);
  }

  @Test
  // RSPAC-1928
  @RunIfSystemPropertyDefined("nightly")
  public void fileUploadSuccess() throws Exception {

    MockMultipartFile mf1 =
        new MockMultipartFile(
            "xfile",
            "adrenaline.smiles",
            TEXT_PLAIN_VALUE,
            getTestResourceFileStream("adrenaline.smiles"));
    MockMultipartFile mf2 =
        new MockMultipartFile(
            "xfile",
            "Aminoglutethimide.mol",
            TEXT_PLAIN_VALUE,
            getTestResourceFileStream("Aminoglutethimide.mol"));
    MockMultipartFile mf3 =
        new MockMultipartFile(
            "xfile",
            "Fluorescein1.cdx",
            APPLICATION_OCTET_STREAM_VALUE,
            getTestResourceFileStream("Fluorescein1.cdx"));
    MockMultipartFile mf4 =
        new MockMultipartFile(
            "xfile",
            "rgroup.cdx",
            APPLICATION_OCTET_STREAM_VALUE,
            getTestResourceFileStream("rgroup.cdx"));

    for (MockMultipartFile mFile : toList(mf1, mf2, mf3, mf4)) {
      MvcResult result =
          mockMvc
              .perform(multipart("/gallery/ajax/uploadFile").file(mFile).principal(principal))
              .andExpect(status().isOk())
              .andReturn();
      RecordInformation recordInformation =
          getFromJsonAjaxReturnObject(result, RecordInformation.class);
      assertNotNull(recordInformation.getChemString());
      assertNotNull(recordInformation.getOid());
      assertEquals(recordInformation.getName(), mFile.getOriginalFilename());
    }
  }

  @Test
  public void testGetStoichiometry() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);

    RSChemElement reaction = addReactionToField(docField, user);

    MvcResult createResult = createStoichiometry(reaction);

    StoichiometryDTO createdStoichiometry =
        getFromJsonAjaxReturnObject(createResult, StoichiometryDTO.class);
    assertNotNull(createdStoichiometry);
    assertNotNull(createdStoichiometry.getId());
    assertEquals(reaction.getId(), createdStoichiometry.getParentReactionId());

    MvcResult getResult =
        mockMvc
            .perform(
                get("/chemical/stoichiometry")
                    .param("chemId", reaction.getId().toString())
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    StoichiometryDTO retrievedStoichiometry =
        getFromJsonAjaxReturnObject(getResult, StoichiometryDTO.class);
    assertNotNull(retrievedStoichiometry);
    assertEquals(createdStoichiometry.getId(), retrievedStoichiometry.getId());
    assertEquals(reaction.getId(), retrievedStoichiometry.getParentReactionId());
  }

  @NotNull
  private MvcResult createStoichiometry(RSChemElement reaction) throws Exception {
    return mockMvc
        .perform(
            post("/chemical/stoichiometry")
                .param("chemId", reaction.getId().toString())
                .principal(principal))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
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
    assertTrue(responseContent.contains("already exists"));
  }

  @Test
  public void testUpdateStoichiometry() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);

    RSChemElement reaction = addReactionToField(docField, user);

    MvcResult createResult = createStoichiometry(reaction);

    StoichiometryDTO createdStoichiometry =
        getFromJsonAjaxReturnObject(createResult, StoichiometryDTO.class);

    StoichiometryMoleculeDTO molecule = createdStoichiometry.getMolecules().get(0);

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(createdStoichiometry.getId());

    StoichiometryMoleculeUpdateDTO updatedMolecule = new StoichiometryMoleculeUpdateDTO();
    updatedMolecule.setId(molecule.getId());
    updatedMolecule.setCoefficient(2.0);
    updatedMolecule.setMass(100.0);
    updatedMolecule.setMoles(0.5);
    updatedMolecule.setExpectedAmount(200.0);
    updatedMolecule.setActualAmount(180.0);
    updatedMolecule.setActualYield(90.0);
    updatedMolecule.setLimitingReagent(true);
    updatedMolecule.setNotes("Updated notes");

    updateDTO.setMolecules(List.of(updatedMolecule));

    MvcResult updateResult =
        mockMvc
            .perform(
                put("/chemical/stoichiometry")
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateDTO))
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    StoichiometryDTO updatedStoichiometry =
        getFromJsonAjaxReturnObject(updateResult, StoichiometryDTO.class);
    assertNotNull(updatedStoichiometry);
    assertEquals(createdStoichiometry.getId(), updatedStoichiometry.getId());

    Optional<StoichiometryMoleculeDTO> foundMolecule =
        updatedStoichiometry.getMolecules().stream()
            .filter(m -> m.getId().equals(molecule.getId()))
            .findFirst();

    assertTrue(foundMolecule.isPresent());
    StoichiometryMoleculeDTO updatedMol = foundMolecule.get();

    double delta = 0.001;
    assertEquals(2.0, updatedMol.getCoefficient(), delta);
    assertEquals(100.0, updatedMol.getMass(), delta);
    assertEquals(0.5, updatedMol.getMoles(), delta);
    assertEquals(200.0, updatedMol.getExpectedAmount(), delta);
    assertEquals(180.0, updatedMol.getActualAmount(), delta);
    assertEquals(90.0, updatedMol.getActualYield(), delta);
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
        getFromJsonAjaxReturnObject(createResult, StoichiometryDTO.class);

    MvcResult deleteResult =
        mockMvc
            .perform(
                delete("/chemical/stoichiometry")
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    boolean deleteSuccess = getFromJsonAjaxReturnObject(deleteResult, Boolean.class);
    assertTrue(deleteSuccess);

    MvcResult getResult =
        mockMvc
            .perform(
                get("/chemical/stoichiometry")
                    .param("chemId", reaction.getId().toString())
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
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
        getFromJsonAjaxReturnObject(createResult, StoichiometryDTO.class);

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
                put("/chemical/stoichiometry")
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateDTO))
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    // check agent added
    StoichiometryDTO updatedAfterAdd =
        getFromJsonAjaxReturnObject(updateResult, StoichiometryDTO.class);
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
        getFromJsonAjaxReturnObject(createResult, StoichiometryDTO.class);

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

    MvcResult addResult =
        mockMvc
            .perform(
                put("/chemical/stoichiometry")
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(addDTO))
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    StoichiometryDTO afterAdd = getFromJsonAjaxReturnObject(addResult, StoichiometryDTO.class);
    assertNotNull(afterAdd);
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

    MvcResult updateResult2 =
        mockMvc
            .perform(
                put("/chemical/stoichiometry")
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(deleteAgentDTO))
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    StoichiometryDTO updatedAfterDelete =
        getFromJsonAjaxReturnObject(updateResult2, StoichiometryDTO.class);
    assertNotNull(updatedAfterDelete);
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
        getFromJsonAjaxReturnObject(createResult, StoichiometryDTO.class);

    StoichiometryMoleculeUpdateDTO bogusUpdate = new StoichiometryMoleculeUpdateDTO();
    bogusUpdate.setId(4242424242L);
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
                put("/chemical/stoichiometry")
                    .param("stoichiometryId", createdStoichiometry.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateDTO))
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("Error updating stoichiometry: "));
    assertTrue(body.contains("Molecule ID "));
    assertTrue(body.contains(" not found in existing stoichiometry molecules"));
  }

  @Test
  public void updateStoichiometry_addNonAgentWithoutId_returnsError() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);
    RSChemElement reaction = addReactionToField(docField, user);
    MvcResult createResult = createStoichiometry(reaction);
    StoichiometryDTO created = getFromJsonAjaxReturnObject(createResult, StoichiometryDTO.class);

    StoichiometryMoleculeUpdateDTO newNonAgent = new StoichiometryMoleculeUpdateDTO();
    newNonAgent.setRole(MoleculeRole.REACTANT);
    newNonAgent.setSmiles("CC");

    List<StoichiometryMoleculeUpdateDTO> updates =
        StoichiometryMapper.toUpdateDTOs(created.getMolecules());
    updates.add(newNonAgent);

    StoichiometryUpdateDTO updateDTO = new StoichiometryUpdateDTO();
    updateDTO.setId(created.getId());
    updateDTO.setMolecules(updates);

    MvcResult result =
        mockMvc
            .perform(
                put("/chemical/stoichiometry")
                    .param("stoichiometryId", created.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateDTO))
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("Error updating stoichiometry: "));
    assertTrue(body.contains("Only AGENT molecules can be added on update without an ID"));
  }

  @Test
  public void updateStoichiometry_addAgentWithoutSmiles_returnsError() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field docField = doc1.getFields().get(0);
    RSChemElement reaction = addReactionToField(docField, user);
    MvcResult createResult = createStoichiometry(reaction);
    StoichiometryDTO created = getFromJsonAjaxReturnObject(createResult, StoichiometryDTO.class);

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
                put("/chemical/stoichiometry")
                    .param("stoichiometryId", created.getId().toString())
                    .contentType(APPLICATION_JSON)
                    .content(new ObjectMapper().writeValueAsString(updateDTO))
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("Error updating stoichiometry: "));
    assertTrue(body.contains("New AGENT molecule requires a SMILES string"));
  }

  @Test
  public void deleteStoichiometry_withNonexistentId_returnsError() throws Exception {
    String missingId = String.valueOf(1122334455L);
    MvcResult result =
        mockMvc
            .perform(
                delete("/chemical/stoichiometry")
                    .param("stoichiometryId", missingId)
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("Error deleting stoichiometry with id " + missingId));
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
