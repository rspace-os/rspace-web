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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.ChemConversionInputDto;
import com.researchspace.model.dtos.chemistry.ChemicalSearchRequestDTO;
import com.researchspace.model.dtos.chemistry.ConvertedStructureDto;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.webapp.controller.RSChemController.ChemEditorInputDto;
import com.researchspace.webapp.controller.RSChemController.ChemSearchResultsPage;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.StopWatch;
import org.hamcrest.Matchers;
import org.jsoup.Jsoup;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebAppConfiguration
@RunWith(ConditionalTestRunner.class)
@TestPropertySource(properties = "chemistry.service.url=")
@Ignore // RSDEV-88 current default chemistry implementation returns empty data. No front-end code
// calls RSChemController endpoints
public class RSChemControllerMVCIT extends MVCTestBase {

  @Autowired private MockServletContext servletContext;

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
    ChemicalSearchRequestDTO request = new ChemicalSearchRequestDTO(chemdata, 0, 0, "SUBSTRUCTURE");
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
  @RunIfSystemPropertyDefined("nightly")
  public void testStructureSearchWithChemFileInGallery() throws Exception {
    doc1 = createBasicDocumentInRootFolderWithText(user, "any");
    Field fld = doc1.getFields().get(0);
    String chemdata = RSpaceTestUtils.getExampleChemString();
    // we save a new chem element, created in chem editor for example and create a new
    // ecatchemistryfile.
    addChemStructureToFieldWithLinkedChemFile(
        addChemistryFileToGallery("Amfetamine.mol", user), fld, user);
    MvcResult result =
        mockMvc
            .perform(
                post("/chemical/search")
                    .param("searchInput", chemdata)
                    .param("pageNumber", "0")
                    .param("pageSize", "10")
                    .param("searchType", "SUBSTRUCTURE")
                    .principal(principal))
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
}
