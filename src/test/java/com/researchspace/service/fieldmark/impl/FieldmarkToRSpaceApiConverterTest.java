package com.researchspace.service.fieldmark.impl;

import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createContainerRequest;
import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createSampleRequest;
import static com.researchspace.service.fieldmark.impl.FieldmarkToRSpaceApiConverter.createSampleTemplateRequest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.fieldmark.model.FieldmarkLocation;
import com.researchspace.fieldmark.model.FieldmarkLocationGeometry;
import com.researchspace.fieldmark.model.utils.FieldmarkFileExtractor;
import com.researchspace.fieldmark.model.utils.FieldmarkLocationExtractor;
import com.researchspace.fieldmark.model.utils.FieldmarkTypeExtractor;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.dtos.fieldmark.FieldmarkNotebookDTO;
import com.researchspace.model.dtos.fieldmark.FieldmarkRecordDTO;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public class FieldmarkToRSpaceApiConverterTest {
  private static final long CONTAINER_ID = 98304L;

  private ObjectMapper mapper;
  private JsonParser parser;
  private FieldmarkNotebookDTO notebookDTO;

  @Before
  public void init() throws IOException, URISyntaxException {
    MockitoAnnotations.openMocks(this);
    mapper = new ObjectMapper();
    parser = new JsonParser();

    String json =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/notebookDTO.json", Charset.defaultCharset());
    notebookDTO = mapper.readValue(json, FieldmarkNotebookDTO.class);
  }

  @Test
  public void testCreateSampleTemplateRequest() throws IOException {
    ApiSampleTemplatePost underTest = createSampleTemplateRequest(notebookDTO);
    assertNotNull(underTest);
    String underTestJsonObject = mapper.writeValueAsString(underTest);

    String expectedJsonObject =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/api-sample-template-request.json", Charset.defaultCharset());

    JsonElement actual = parser.parse(underTestJsonObject);
    JsonElement expected = parser.parse(expectedJsonObject);

    assertEquals(expected, actual);
  }

  @Test
  public void testCreateContainerRequest() {
    User user = new User("sysadmin1");
    user.setId(-12L);
    user.setEmail("sysadmin@researchspace.com");
    user.setFirstName("System");
    user.setLastName("Admin");
    user.setRoles(Set.of(Role.SYSTEM_ROLE));

    ApiContainer underTest = createContainerRequest(notebookDTO, user);
    assertNotNull(underTest);
    assertEquals(user.getUsername(), underTest.getOwner().getUsername());
    assertEquals(underTest.getName(), "Container RSpace IGSN Demo - 2024-11-20 18:24:03");

    assertEquals(13, underTest.getExtraFields().size());
    assertEquals("item name", underTest.getExtraFields().get(0).getName());
    assertEquals("projectId", underTest.getExtraFields().get(1).getName());
    assertEquals("leadInstitution", underTest.getExtraFields().get(2).getName());
    assertEquals("projectLead", underTest.getExtraFields().get(3).getName());
    assertEquals("projectStatus", underTest.getExtraFields().get(4).getName());
    assertEquals("age", underTest.getExtraFields().get(5).getName());
    assertEquals("size", underTest.getExtraFields().get(6).getName());
    assertEquals("preDescription", underTest.getExtraFields().get(7).getName());
    assertEquals("isPublic", underTest.getExtraFields().get(8).getName());
    assertEquals("isRequest", underTest.getExtraFields().get(9).getName());
    assertEquals("showQRCodeButton", underTest.getExtraFields().get(10).getName());
    assertEquals("notebookVersion", underTest.getExtraFields().get(11).getName());
    assertEquals("schemaVersion", underTest.getExtraFields().get(12).getName());
  }

  @Test
  public void testCreateSampleRequest() throws IOException {
    ApiSampleTemplate sampleTemplate = getPreBuiltSampleTemplate(notebookDTO);
    ApiSampleWithFullSubSamples underTest =
        createSampleRequest(
            notebookDTO.getRecord("rec-ae48f602-c9c4-4e9e-ae3b-6ecf65706e87"),
            sampleTemplate,
            CONTAINER_ID);
    assertNotNull(underTest);
    String underTestJsonObject = mapper.writeValueAsString(underTest);

    String expectedJsonObject =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/api-sample-request.json", Charset.defaultCharset());

    JsonElement actual = parser.parse(underTestJsonObject);
    JsonElement expected = parser.parse(expectedJsonObject);

    assertEquals(expected, actual);
  }

  public static ApiSampleTemplate getPreBuiltSampleTemplate(FieldmarkNotebookDTO notebookDTO)
      throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    String sampleTemplateJson =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/api-sample-template-response.json", Charset.defaultCharset());
    for (Entry<String, FieldmarkRecordDTO> fieldmarkRecordDTOEntry :
        notebookDTO.getRecords().entrySet()) {
      FieldmarkRecordDTO currentRecord = fieldmarkRecordDTOEntry.getValue();
      FieldmarkTypeExtractor<Map<String, Double>> extractor =
          currentRecord.getField("Sample-Location");

      FieldmarkLocation locationObject = null;
      if (extractor.getFieldValue() != null) {
        locationObject =
            new FieldmarkLocation(
                new FieldmarkLocationGeometry(
                    List.of(
                        extractor.getFieldValue().get("latitude"),
                        extractor.getFieldValue().get("longitude"))));
      }

      FieldmarkLocationExtractor locationExtractor = new FieldmarkLocationExtractor(locationObject);

      currentRecord.getFields().put("Sample-Location", locationExtractor);
      currentRecord.setFields(currentRecord.getFields());
    }

    return mapper.readValue(sampleTemplateJson, ApiSampleTemplate.class);
  }

  public static ApiSampleWithFullSubSamples getPreBuiltSample(FieldmarkNotebookDTO notebookDTO)
      throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    String sampleJson =
        IOUtils.resourceToString(
            "/TestResources/fieldmark/api-sample-response-10.json", Charset.defaultCharset());

    for (Entry<String, FieldmarkRecordDTO> fieldmarkRecordDTOEntry :
        notebookDTO.getRecords().entrySet()) {
      FieldmarkRecordDTO currentRecord = fieldmarkRecordDTOEntry.getValue();
      FieldmarkTypeExtractor<String> extractor = currentRecord.getField("Sample-Photograph");

      FieldmarkFileExtractor fileExtractor = new FieldmarkFileExtractor();
      fileExtractor.setFileName("fileName");

      File attachment = new File(FileUtils.getTempDirectoryPath() + "/attachedFile.jpg");
      FileUtils.writeStringToFile(attachment, extractor.getFieldValue(), Charset.defaultCharset());
      fileExtractor.setFieldValue(FileUtils.readFileToByteArray(attachment));

      currentRecord.getFields().put("Sample-Photograph", fileExtractor);
      currentRecord.setFields(currentRecord.getFields());
    }

    return mapper.readValue(sampleJson, ApiSampleWithFullSubSamples.class);
  }
}
