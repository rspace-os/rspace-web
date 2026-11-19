package com.researchspace.service.inventory.csvimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult.InventoryBulkOperationStatus;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryEntityField.ApiInventoryFieldDef;
import com.researchspace.api.v1.model.ApiInventoryImportInstrumentImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleImportResult;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unmapped CSV columns fill the template fields named by their headers (RSDEV-1717), and radio or
 * choice cells select the options they list (RSDEV-1716), in both sample and instrument imports.
 */
public class CsvImporterTemplateFieldMatchingTest {

  private final CsvSampleImporter sampleImporter = new CsvSampleImporter();
  private final CsvInstrumentImporter instrumentImporter = new CsvInstrumentImporter();
  private final User user = new User("u");

  @BeforeEach
  void setUp() {
    MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
    for (InventoryItemCsvImporter importer :
        List.<InventoryItemCsvImporter>of(sampleImporter, instrumentImporter)) {
      importer.messages = messages;
      importer.setCsvLinesLimit(10);
    }
  }

  private void reportErrorsAsTheirMessage(InventoryItemCsvImporter importer) {
    InventoryBulkOperationHandler bulkHandler = mock(InventoryBulkOperationHandler.class);
    when(bulkHandler.convertExceptionToApiError(any()))
        .thenAnswer(
            call ->
                importer.getBadRequestIllegalArgumentApiError(
                    call.<Exception>getArgument(0).getMessage()));
    importer.bulkOperationHandler = bulkHandler;
  }

  @Test
  void reorderedHeadersFillTheTemplateFieldsTheyName() throws IOException {
    ApiInventoryImportSampleImportResult result =
        importSamples("Name,Toxic,GMO,Permit\ns1,No,Yes,permit-123\n");

    List<ApiInventoryEntityField> fields = sampleFields(result, 0);
    assertEquals("GMO", fields.get(0).getName());
    assertEquals(List.of("Yes"), fields.get(0).getSelectedOptions());
    assertEquals("Toxic", fields.get(1).getName());
    assertEquals(List.of("No"), fields.get(1).getSelectedOptions());
    assertEquals("permit-123", fields.get(2).getContent());
  }

  @Test
  void exportedHeadersMatchIgnoringCaseAndTypeSuffix() throws IOException {
    ApiInventoryImportSampleImportResult result =
        importSamples("Name,\"permit (STRING, IT12)\", Toxic (RADIO) ,gmo\ns1,p-1,No,Yes\n");

    List<ApiInventoryEntityField> fields = sampleFields(result, 0);
    assertEquals(List.of("Yes"), fields.get(0).getSelectedOptions());
    assertEquals(List.of("No"), fields.get(1).getSelectedOptions());
    assertEquals("p-1", fields.get(2).getContent());
  }

  @Test
  void headersNamingNoTemplateFieldFillTheFieldsInOrder() throws IOException {
    ApiInventoryImportSampleImportResult result = importSamples("Name,A,B,C\ns1,Yes,No,p-1\n");

    List<ApiInventoryEntityField> fields = sampleFields(result, 0);
    assertEquals(List.of("Yes"), fields.get(0).getSelectedOptions());
    assertEquals(List.of("No"), fields.get(1).getSelectedOptions());
    assertEquals("p-1", fields.get(2).getContent());
  }

  @Test
  void headersNamingNoTemplateFieldFillTheRemainingFieldsInOrder() throws IOException {
    ApiInventoryImportSampleImportResult result =
        importSamples("Name,Toxic,Licence,GMO\ns1,No,p-1,Yes\n");

    List<ApiInventoryEntityField> fields = sampleFields(result, 0);
    assertEquals(List.of("Yes"), fields.get(0).getSelectedOptions());
    assertEquals(List.of("No"), fields.get(1).getSelectedOptions());
    assertEquals("p-1", fields.get(2).getContent());
  }

  @Test
  void twoHeadersNamingOneTemplateFieldFailPrevalidation() throws IOException {
    reportErrorsAsTheirMessage(sampleImporter);
    ApiInventoryImportSampleImportResult result =
        importSamples("Name,GMO,\"gmo (RADIO, IT1)\",Permit\ns1,Yes,No,p-1\n");

    assertEquals(InventoryBulkOperationStatus.PREVALIDATION_ERROR, result.getStatus());
    assertThat(result.getTemplate().getError().getErrors().get(0))
        .contains("\"GMO\", \"gmo (RADIO, IT1)\" all match the template field \"GMO\"");
  }

  @Test
  void choiceCellSelectsEachOptionOfAnExportedJsonArrayOrACommaList() {
    ApiInventoryImportSampleImportResult result = sampleResult(hazardsAndGradeTemplate());
    List<String[]> lines =
        List.of(
            new String[] {"s1", "[\"Bio\",\"Chem\"]", "A"},
            new String[] {"s2", "Bio, Rad", "B"},
            new String[] {"s3", "Chem", "[\"A\"]"});

    sampleImporter.convertLinesToSamples(result, lines, Map.of(0, "name"), 3, user);

    assertEquals(List.of("Bio", "Chem"), sampleFields(result, 0).get(0).getSelectedOptions());
    assertEquals(List.of("A"), sampleFields(result, 0).get(1).getSelectedOptions());
    assertEquals(List.of("Bio", "Rad"), sampleFields(result, 1).get(0).getSelectedOptions());
    assertEquals(List.of("Chem"), sampleFields(result, 2).get(0).getSelectedOptions());
    assertEquals(List.of("A"), sampleFields(result, 2).get(1).getSelectedOptions());
  }

  @Test
  void cellListingAnOptionTheFieldLacksStaysOneOptionForValidationToReject() {
    ApiInventoryImportSampleImportResult result = sampleResult(hazardsAndGradeTemplate());
    List<String[]> lines = List.<String[]>of(new String[] {"s1", "Bio,Lead", "A,B"});

    sampleImporter.convertLinesToSamples(result, lines, Map.of(0, "name"), 3, user);

    assertEquals(List.of("Bio,Lead"), sampleFields(result, 0).get(0).getSelectedOptions());
    assertEquals(List.of("A,B"), sampleFields(result, 0).get(1).getSelectedOptions());
  }

  @Test
  void cellThatIsExactlyAnAllowedOptionSelectsIt() {
    ApiSampleTemplate template = new ApiSampleTemplate();
    template.getFields().add(optionsField("Level", ApiFieldType.RADIO, "[1]", "[2]"));
    ApiInventoryImportSampleImportResult result = sampleResult(template);
    List<String[]> lines = List.<String[]>of(new String[] {"s1", "[1]"});

    sampleImporter.convertLinesToSamples(result, lines, Map.of(0, "name"), 2, user);

    assertEquals(List.of("[1]"), sampleFields(result, 0).get(0).getSelectedOptions());
  }

  @Test
  void malformedListCellStaysOneOptionForValidationToReject() {
    ApiInventoryImportSampleImportResult result = sampleResult(hazardsAndGradeTemplate());
    List<String> cells = List.of("[\"Bio\"] typo", "[1]", "[\"Bio\",null]", "Bio,", ",");

    List<String[]> lines = cells.stream().map(cell -> new String[] {"s", cell, "A"}).toList();
    sampleImporter.convertLinesToSamples(result, lines, Map.of(0, "name"), 3, user);

    for (int row = 0; row < cells.size(); row++) {
      assertEquals(List.of(cells.get(row)), sampleFields(result, row).get(0).getSelectedOptions());
    }
  }

  @Test
  void onlyReservedHeadersMatchTheirUnderscoredTemplateField() throws IOException {
    ApiSampleTemplate template = new ApiSampleTemplate();
    for (String name : List.of("First", "_Batch", "_Source")) {
      ApiInventoryEntityField field = new ApiInventoryEntityField();
      field.setName(name);
      field.setType(ApiFieldType.STRING);
      template.getFields().add(field);
    }
    ApiInventoryImportSampleImportResult result = sampleResult(template);
    ApiInventoryImportResult importResult = new ApiInventoryImportResult();
    importResult.setSampleResult(result);

    sampleImporter.readCsvIntoImportResult(
        IOUtils.toInputStream("Name,Source,Batch,Other\ns1,src,b,o\n"),
        Map.of("Name", "name"),
        importResult,
        user);

    List<ApiInventoryEntityField> fields = sampleFields(result, 0);
    assertEquals("b", fields.get(0).getContent());
    assertEquals("o", fields.get(1).getContent());
    assertEquals("src", fields.get(2).getContent());
  }

  @Test
  void instrumentImportMatchesHeadersByNameAndReadsMultiSelectCells() throws IOException {
    ApiInstrumentTemplate template = new ApiInstrumentTemplate();
    template.getFields().addAll(hazardsAndGradeTemplate().getFields());
    ApiInventoryImportInstrumentImportResult instrumentResult =
        new ApiInventoryImportInstrumentImportResult();
    instrumentResult.addExistingTemplateResult(template);
    ApiInventoryImportResult importResult = new ApiInventoryImportResult();
    importResult.setInstrumentResult(instrumentResult);

    instrumentImporter.readCsvIntoImportResult(
        IOUtils.toInputStream("Name,Grade,Hazards\ni1,B,\"[\"\"Bio\"\",\"\"Rad\"\"]\"\n"),
        Map.of("Name", "name"),
        importResult,
        user);

    List<ApiInventoryEntityField> fields =
        ((ApiInstrument) instrumentResult.getResults().get(0).getRecord()).getFields();
    assertEquals(List.of("Bio", "Rad"), fields.get(0).getSelectedOptions());
    assertEquals(List.of("B"), fields.get(1).getSelectedOptions());
  }

  @Test
  void instrumentHeaderNamingNoTemplateFieldFillsTheRemainingField() throws IOException {
    ApiInstrumentTemplate template = new ApiInstrumentTemplate();
    template.getFields().addAll(hazardsAndGradeTemplate().getFields());
    ApiInventoryImportInstrumentImportResult instrumentResult =
        new ApiInventoryImportInstrumentImportResult();
    instrumentResult.addExistingTemplateResult(template);
    ApiInventoryImportResult importResult = new ApiInventoryImportResult();
    importResult.setInstrumentResult(instrumentResult);

    instrumentImporter.readCsvIntoImportResult(
        IOUtils.toInputStream("Name,Grade,Risk\ni1,B,Bio\n"),
        Map.of("Name", "name"),
        importResult,
        user);

    List<ApiInventoryEntityField> fields =
        ((ApiInstrument) instrumentResult.getResults().get(0).getRecord()).getFields();
    assertEquals(List.of("Bio"), fields.get(0).getSelectedOptions());
    assertEquals(List.of("B"), fields.get(1).getSelectedOptions());
  }

  private ApiInventoryImportSampleImportResult importSamples(String csv) throws IOException {
    ApiSampleTemplate template = new ApiSampleTemplate();
    template.getFields().add(optionsField("GMO", ApiFieldType.RADIO, "Yes", "No"));
    template.getFields().add(optionsField("Toxic", ApiFieldType.RADIO, "Yes", "No"));
    ApiInventoryEntityField permit = new ApiInventoryEntityField();
    permit.setName("Permit");
    permit.setType(ApiFieldType.STRING);
    template.getFields().add(permit);
    ApiInventoryImportSampleImportResult result = new ApiInventoryImportSampleImportResult();
    result.addExistingTemplateResult(template);
    ApiInventoryImportResult importResult = new ApiInventoryImportResult();
    importResult.setSampleResult(result);

    sampleImporter.readCsvIntoImportResult(
        IOUtils.toInputStream(csv), Map.of("Name", "name"), importResult, user);
    return result;
  }

  private ApiSampleTemplate hazardsAndGradeTemplate() {
    ApiSampleTemplate template = new ApiSampleTemplate();
    template.getFields().add(optionsField("Hazards", ApiFieldType.CHOICE, "Bio", "Chem", "Rad"));
    template.getFields().add(optionsField("Grade", ApiFieldType.RADIO, "A", "B"));
    return template;
  }

  private ApiInventoryImportSampleImportResult sampleResult(ApiSampleTemplate template) {
    ApiInventoryImportSampleImportResult result = new ApiInventoryImportSampleImportResult();
    result.addExistingTemplateResult(template);
    return result;
  }

  private ApiInventoryEntityField optionsField(String name, ApiFieldType type, String... options) {
    ApiInventoryEntityField field = new ApiInventoryEntityField();
    field.setName(name);
    field.setType(type);
    field.setDefinition(new ApiInventoryFieldDef(List.of(options), false));
    return field;
  }

  private List<ApiInventoryEntityField> sampleFields(
      ApiInventoryImportSampleImportResult result, int row) {
    return ((ApiSampleWithFullSubSamples) result.getResults().get(row).getRecord()).getFields();
  }
}
