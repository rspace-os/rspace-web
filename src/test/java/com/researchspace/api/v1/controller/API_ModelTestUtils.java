package com.researchspace.api.v1.controller;

import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.researchspace.api.v1.controller.SysadminApiController.UserApiPost;
import com.researchspace.api.v1.model.ApiDocument;
import com.researchspace.api.v1.model.ApiDocumentField;
import com.researchspace.api.v1.model.ApiDocumentInfo;
import com.researchspace.api.v1.model.ApiField;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiFile;
import com.researchspace.api.v1.model.ApiFormInfo;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpStatus;
import org.supercsv.cellprocessor.CellProcessorAdaptor;
import org.supercsv.cellprocessor.ParseEnum;
import org.supercsv.cellprocessor.ParseInt;
import org.supercsv.cellprocessor.constraint.NotNull;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.exception.SuperCsvCellProcessorException;
import org.supercsv.io.CsvBeanReader;
import org.supercsv.io.ICsvBeanReader;
import org.supercsv.prefs.CsvPreference;
import org.supercsv.util.CsvContext;

public class API_ModelTestUtils {
  public static ApiDocument createAnyApiDocWithNFields(int numFields, ApiUser owner) {
    ApiDocument doc = new ApiDocument();
    doc.setCreatedMillis(new Date().getTime());
    List<ApiDocumentField> fields = new ArrayList<>();
    for (int i = 0; i < numFields; i++) {
      ApiDocumentField field = createAnyApiDocumentField(i);
      fields.add(field);
    }
    doc.setFields(fields);
    doc.setForm(createAForm());
    doc.setId(1L);
    doc.setLastModifiedMillis(new Date().getTime());
    doc.setName("ADocName");
    doc.setOwner(owner);
    doc.setTags("tag1, tag2, tag3");
    return doc;
  }

  static ApiUser createAUser(String username) {
    ApiUser user =
        new ApiUser(3L, username, username + "@x.com", username + "First", username + "Last");
    return user;
  }

  static UserApiPost createAnyUserPost(String unameString) {
    return new UserApiPost(
        unameString,
        "password1234bgh",
        unameString + "@somewhere.com",
        "first",
        "last",
        "ROLE_USER",
        null,
        randomAlphabetic(16),
        false);
  }

  static ApiFormInfo createAForm() {
    ApiFormInfo form = new ApiFormInfo();
    form.setId(2L);
    form.setName("anyForm");
    form.setStableId("stableId");
    form.setVersion(3);
    return form;
  }

  static ApiDocumentField createAnyApiDocumentField(int nameIndex) {
    ApiDocumentField field = new ApiDocumentField();
    field.setContent("field-content" + nameIndex);
    field.setId(new Long(nameIndex));
    field.setGlobalId(GlobalIdPrefix.FD.name() + new Long(nameIndex));
    field.setName("Field name " + nameIndex);
    field.setType(ApiFieldType.TEXT);
    field.setLastModifiedMillis(new Date().getTime());
    return field;
  }

  public void assertApiDocumentInfoMatchSDoc(
      ApiDocumentInfo apiDocInfo, StructuredDocument testDoc) {
    assertEquals(testDoc.getId(), apiDocInfo.getId());
    assertEquals(testDoc.getName(), apiDocInfo.getName());
    assertEquals(testDoc.isSigned(), apiDocInfo.getSigned());
    assertEquals(testDoc.getDocTag(), apiDocInfo.getTags());
    System.err.println(testDoc.getCreationDate() + "," + new Date(apiDocInfo.getCreatedMillis()));
    assertEquals(testDoc.getCreationDate().getTime(), apiDocInfo.getCreatedMillis().longValue());
    assertEquals(testDoc.getModificationDateMillis(), apiDocInfo.getLastModifiedMillis());

    assertNotNull(apiDocInfo.getOwner());
    assertApiUserInfoMatchUser(apiDocInfo.getOwner(), testDoc.getOwner());

    assertNotNull(apiDocInfo.getForm());
    assertApiFormMatchRSForm(apiDocInfo.getForm(), testDoc.getForm());
  }

  public void assertApiDocumentMatchSDoc(ApiDocument apiDoc, StructuredDocument doc) {
    assertApiDocumentInfoMatchSDoc(apiDoc, doc);

    assertNotNull(apiDoc.getFields());
    assertApiFieldsListMatchFields(apiDoc.getFields(), doc.getFields());
  }

  public void assertApiUserInfoMatchUser(ApiUser apiUser, User user) {
    assertEquals(user.getId(), apiUser.getId());
    assertEquals(user.getUsername(), apiUser.getUsername());
    assertEquals(user.getFirstName(), apiUser.getFirstName());
    assertEquals(user.getLastName(), apiUser.getLastName());
    assertEquals(user.getEmail(), apiUser.getEmail());
  }

  public void assertApiFormMatchRSForm(ApiFormInfo apiForm, RSForm rsForm) {
    assertEquals(rsForm.getId(), apiForm.getId());
    assertEquals(rsForm.getName(), apiForm.getName());
    assertEquals(rsForm.getVersion().getVersion().intValue(), apiForm.getVersion().intValue());
    assertEquals(rsForm.getStableID(), apiForm.getStableId());
  }

  public void assertApiFieldsListMatchFields(
      List<ApiDocumentField> apiFields, List<Field> docFields) {
    assertEquals(docFields.size(), apiFields.size());
    for (int i = 0; i < apiFields.size(); i++) {
      assertApiFieldMatchField(apiFields.get(i), docFields.get(i));
    }
  }

  private void assertApiFieldMatchField(ApiField apiField, Field field) {
    assertEquals(field.getId(), apiField.getId());
    assertEquals(field.getType().toString().toLowerCase(), apiField.getType().toString());
    assertEquals(field.getFieldData(), apiField.getContent());
    assertEquals(field.getModificationDate(), apiField.getLastModifiedMillis());
  }

  public void assertApiFileMatchEcatMediaFile(ApiFile apiFile, EcatMediaFile mediaFile) {
    assertEquals(mediaFile.getId(), apiFile.getId());
    assertEquals(mediaFile.getName(), apiFile.getName());
    assertEquals(mediaFile.getContentType(), apiFile.getContentType());
    assertEquals(mediaFile.getSize(), apiFile.getSize().longValue());
    assertEquals(Long.valueOf(mediaFile.getCreationDate().getTime()), apiFile.getCreatedMillis());

    assertEquals(2, apiFile.getLinks().size());
    assertEquals(ApiLinkItem.SELF_REL, apiFile.getLinks().get(0).getRel());
    assertEquals(ApiLinkItem.ENCLOSURE_REL, apiFile.getLinks().get(1).getRel());
  }

  public static final String[] parseCSVResponseToLines(String csvResponse) {
    return csvResponse.split("\r\n");
  }

  public static void assertRowAndColumnCount(
      String csv, final int expectedLineCount, int expectedColumnCount) throws IOException {
    String[] lines = csv.split("\n");
    assertEquals(expectedLineCount, lines.length);
    for (String line : lines) {
      assertEquals(expectedColumnCount, line.split(",").length);
    }
  }

  public static class StringToList extends CellProcessorAdaptor {
    public StringToList() {
      super();
    }

    @Override
    public <T> T execute(Object value, CsvContext context) {
      if (!(value instanceof String)) {
        throw new SuperCsvCellProcessorException(String.class, value, context, this);
      }
      List<String> result = Arrays.asList(StringUtils.split((String) value, ","));
      return next.execute(result, context);
    }
  }

  public static void assertRowAndColumnCountForApiError(
      String csv, final int expectedLineCount, int expectedColumnCount) throws IOException {
    try (ICsvBeanReader beanReader =
        new CsvBeanReader(new StringReader(csv), CsvPreference.STANDARD_PREFERENCE)) {
      String[] headers = beanReader.getHeader(true);
      assertEquals(expectedColumnCount, headers.length);
      int rowCount = 1;
      while (beanReader.read(
              ApiError.class,
              headers,
              new CellProcessor[] {
                new ParseEnum(HttpStatus.class),
                new ParseInt(),
                new ParseInt(),
                new NotNull(),
                new StringToList()
              })
          != null) {
        rowCount++;
      }
      assertEquals(expectedLineCount, rowCount);
    }
  }
}
