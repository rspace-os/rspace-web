package com.researchspace.webapp.integrations.protocolsio;

import static org.apache.commons.io.FileUtils.readFileToString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.webapp.controller.MVCTestBase;
import java.io.File;
import javax.annotation.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

@WebAppConfiguration
@RunWith(ConditionalTestRunner.class)
public class ProtocolsIOControllerMVCIT extends MVCTestBase {

  private ObjectMapper objectMapper;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    objectMapper = new ObjectMapper();
    objectMapper.enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  @RunIfSystemPropertyDefined("nightly")
  // RSPAC-1882
  public void testStandardUploadWithImages() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    // this is a modified version of a p-io protocol with images.
    // original has 5 images stored on AWS, this has 2 so that test runs afaster
    File testFile = RSpaceTestUtils.getResource("pio-with-images.json");
    String p1 = readFileToString(testFile, "UTF-8");
    long initialImageCount = getCountOfEntityTable("EcatImage");
    long initialFolderCount = getCountOfEntityTable("Folder");
    MvcResult result =
        mockMvc
            .perform(
                post("/importer/generic/protocols_io")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("[" + p1 + "]"))
            .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
            .andReturn();
    ProtocolsIOController.PIOResponse recordInformation =
        getFromJsonAjaxReturnObject(result, ProtocolsIOController.PIOResponse.class);
    assertEquals(1, recordInformation.getResults().size());
    // check 2 images are imported - we don't import duplicates
    assertEquals(initialImageCount + 2, getCountOfEntityTable("EcatImage").intValue());
    String docContentString = getFieldData(anyUser, recordInformation);
    // images are duplicated in the JSON obtained from PIO
    assertEquals(2, getImagesInFieldDataCount(docContentString));
    // should be a folder created to hold images
    assertEquals(initialFolderCount + 1, getCountOfEntityTable("Folder").intValue());
  }

  private int getImagesInFieldDataCount(String docContentString) throws Exception {
    return doInTransaction(
        () ->
            fieldParser
                .findFieldElementsInContent(docContentString)
                .getElements(EcatImage.class)
                .size());
  }

  private String getFieldData(User anyUser, ProtocolsIOController.PIOResponse recordInformation) {
    return fieldMgr
        .getFieldsByRecordId(recordInformation.getResults().get(0).getId(), anyUser)
        .get(0)
        .getFieldData();
  }

  @Test
  public void testStandardUpload() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    File testFile = RSpaceTestUtils.getResource("p_io_8163.json");
    String p1 = readFileToString(testFile, "UTF-8");
    String p2 = readFileToString(testFile, "UTF-8");
    String protocolList = "[" + p1 + "," + p2 + "]";
    MvcResult result =
        mockMvc
            .perform(
                post("/importer/generic/protocols_io")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(protocolList))
            .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
            .andReturn();
    ProtocolsIOController.PIOResponse recordInformation =
        getFromJsonAjaxReturnObject(result, ProtocolsIOController.PIOResponse.class);
    assertNotNull(recordInformation.getResults().get(0));
    assertNotNull(recordInformation.getImportFolderId());

    Integer newId = (Integer) getJsonPathValue(result, "$.data.results[0].id");
    // is an ProtocolsIO form
    StructuredDocument newDoc = recordMgr.get(newId).asStrucDoc();
    RSForm form = newDoc.getForm();
    assertTrue(form.isSystemForm());
    assertEquals("ProtocolsIO", form.getName());

    // and is in imports folder.
    Folder parent = newDoc.getOwnerParent().get();
    assertTrue(parent.isImportsFolder());
    assertEquals(parent.getId().intValue(), getJsonPathValue(result, "$.data.importFolderId"));
    String contentString =
        fieldMgr.getFieldsByRecordId(newDoc.getId(), anyUser).get(0).getFieldData();
    assertTrue(contentString.contains("colorimetric"));
  }

  private HttpHeaders getApiHeaders(@Nullable String accessToken) {
    HttpHeaders headers = new HttpHeaders();
    if (accessToken != null) headers.add("Authorization", String.format("Bearer %s", accessToken));
    return headers;
  }
}
