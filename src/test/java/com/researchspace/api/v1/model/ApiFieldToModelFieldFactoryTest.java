package com.researchspace.api.v1.model;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static org.junit.Assert.assertNotNull;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiSampleField.ApiInventoryFieldDef;
import java.util.Arrays;
import java.util.EnumSet;
import org.junit.Test;

public class ApiFieldToModelFieldFactoryTest {

  ApiFieldToModelFieldFactory factory = new ApiFieldToModelFieldFactory();

  @Test
  public void allEnumsCovered() {
    // test fields that don't internally self-validate yet.
    for (ApiFieldType type :
        EnumSet.complementOf(
            EnumSet.of(
                ApiFieldType.CHOICE, ApiFieldType.URI, ApiFieldType.RADIO, ApiFieldType.NUMBER,
                ApiField.ApiFieldType.TIME, ApiField.ApiFieldType.DATE))) {
      ApiSampleField any = new ApiSampleField();
      any.setContent("some content");
      any.setType(type);
      any.setName("afield");
      assertNotNull(factory.apiSampleFieldToModelField(any));
    }
  }

  @Test
  public void numberField() {
    assertValues("123.4", "not a num", ApiFieldType.NUMBER);
  }

  @Test
  public void uriField() {
    assertValues("file://something.txt", "not a uri", ApiFieldType.URI);
  }

  @Test
  public void choiceField() {
    ApiSampleField anyApiField = new ApiSampleField();
    anyApiField.setType(ApiFieldType.CHOICE);
    // no definition
    assertIllegalArgumentException(() -> factory.apiSampleFieldToModelField(anyApiField));

    // no options
    ApiInventoryFieldDef def = new ApiInventoryFieldDef();
    anyApiField.setDefinition(def);
    assertIllegalArgumentException(() -> factory.apiSampleFieldToModelField(anyApiField));

    // valid options, no content (i.e no default preselected options)
    def.setOptions(Arrays.asList("a=b", "a=c"));
    anyApiField.setContent("");
    assertNotNull(factory.apiSampleFieldToModelField(anyApiField));
  }

  @Test
  public void radioField() {
    ApiSampleField anyApiField = new ApiSampleField();
    anyApiField.setType(ApiFieldType.RADIO);
    // no definition
    assertIllegalArgumentException(() -> factory.apiSampleFieldToModelField(anyApiField));

    // no options
    ApiInventoryFieldDef def = new ApiInventoryFieldDef();
    anyApiField.setDefinition(def);
    assertIllegalArgumentException(() -> factory.apiSampleFieldToModelField(anyApiField));

    // valid options, no content (i.e no default preselected options)
    def.setOptions(Arrays.asList("a=b", "a=c"));
    anyApiField.setContent("");
    assertNotNull(factory.apiSampleFieldToModelField(anyApiField));
  }

  @Test
  public void timeField(){
    // time field should be 24hour in pattern HH:mm
    assertValues("10:24", "9:24", ApiFieldType.TIME);
  }

  @Test
  public void dateField(){
    // date field should be in pattern yyyy-MM-dd
    assertValues("2024-08-19", "24/08/19", ApiFieldType.DATE);
  }

  private void assertValues(String valid, String invalid, ApiFieldType type) {
    ApiSampleField any = new ApiSampleField();
    any.setContent(invalid);
    any.setType(type);
    any.setName("afield");
    assertIllegalArgumentException(() -> factory.apiSampleFieldToModelField(any));
    any.setContent(valid);
    assertNotNull(factory.apiSampleFieldToModelField(any));
  }
}
