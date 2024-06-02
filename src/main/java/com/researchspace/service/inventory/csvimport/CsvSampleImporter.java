package com.researchspace.service.inventory.csvimport;

import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleParseResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.inventory.field.SampleField;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.units.RSUnitDef;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CsvSampleImporter extends InventoryItemCsvImporter {

  @Value("${inventory.import.samplesLimit}")
  private Integer csvSampleLinesLimit;

  @Autowired private IRecordFactory recordFactory;

  @Autowired private InventoryImportSampleFieldCreator importedFieldCreator;

  public CsvSampleImporter() {
    super(ApiInventoryRecordType.SAMPLE);
  }

  @PostConstruct
  private void setLinesLimit() {
    setCsvLinesLimit(csvSampleLinesLimit);
  }

  public ApiInventoryImportSampleParseResult parseSamplesCsvFile(
      String filename, InputStream inputStream, User createdBy) throws IOException {

    ApiInventoryImportSampleParseResult sampleParseResult =
        new ApiInventoryImportSampleParseResult();
    readCsvIntoParseResults(inputStream, sampleParseResult);

    String templateName =
        filename.toLowerCase().endsWith(".csv")
            ? filename.substring(0, filename.length() - 4)
            : filename;
    Sample template = recordFactory.createSample(templateName, createdBy);
    template.setTemplate(true);
    setDefaultsInSuggestedParsedTemplate(template);

    // for each column decide field type and add to result template
    for (String columnName : sampleParseResult.getColumnNames()) {
      String fieldName = sampleParseResult.getFieldNameForColumnName().get(columnName);
      List<String> fieldValues = sampleParseResult.getColumnNameToValuesMap().get(columnName);
      SampleField field =
          importedFieldCreator.getSuggestedSampleFieldForNameAndValues(fieldName, fieldValues);
      template.addSampleField(field);

      populateResultWithRadioOptionsForColumn(field, columnName, fieldValues, sampleParseResult);
      populateResultWithQuantityTypeForColumn(field, columnName, fieldValues, sampleParseResult);
    }

    sampleParseResult.setTemplateInfo(new ApiSampleTemplatePost(template));

    return sampleParseResult;
  }

  private void setDefaultsInSuggestedParsedTemplate(Sample template) {
    template.setSampleSource(SampleSource.OTHER);
  }

  private void populateResultWithRadioOptionsForColumn(
      SampleField field,
      String columnName,
      List<String> fieldValues,
      ApiInventoryImportSampleParseResult result) {

    // skip text fields (too much data to send back & minimal chance for fitting as radio)
    if (FieldType.TEXT.equals(field.getType())) {
      return;
    }
    List<String> radioOptions =
        importedFieldCreator.calculateRadioOptions(new HashSet<>(fieldValues));
    result.getRadioOptionsForColumn().put(columnName, radioOptions);
  }

  private void populateResultWithQuantityTypeForColumn(
      SampleField field,
      String columnName,
      List<String> fieldValues,
      ApiInventoryImportSampleParseResult result) {

    if (FieldType.NUMBER.equals(field.getType())) {
      result.getQuantityUnitForColumn().put(columnName, RSUnitDef.DIMENSIONLESS.getId());
      return;
    }

    RSUnitDef quantityUnit = importedFieldCreator.getCommonQuantityUnit(fieldValues);
    if (quantityUnit != null) {
      result.getQuantityUnitForColumn().put(columnName, quantityUnit.getId());
    }
  }

  public void readCsvIntoImportResult(
      InputStream samplesIS,
      Map<String, String> csvColumnToFieldMapping,
      ApiInventoryImportResult importResult)
      throws IOException {

    // already present and store the template
    ApiInventoryImportSampleImportResult sampleProcessingResult = importResult.getSampleResult();

    // read lines from csv
    List<String[]> lines = readCsvLinesFromInputStream(samplesIS);
    assertNumberOfCsvLinesAcceptable(lines.size());

    // find column indexes of default sample field mappings
    List<String> columnNames = Arrays.asList(lines.get(0));
    assertNoRepeatingColumnNames(columnNames);
    Map<Integer, String> columnIndexToDefaultFieldMap =
        generateColumnIndexToDefaultFieldMap(csvColumnToFieldMapping, columnNames);

    checkSampleCsvCompatibilityWithSampleTemplate(
        sampleProcessingResult, columnNames, columnIndexToDefaultFieldMap);
    boolean isTemplateCompatible = sampleProcessingResult.getTemplate().getRecord() != null;

    if (isTemplateCompatible) {
      List<String[]> csvSampleLines = lines.subList(1, lines.size());
      convertLinesToSamples(
          sampleProcessingResult, csvSampleLines, columnIndexToDefaultFieldMap, columnNames.size());
    }
  }

  private void checkSampleCsvCompatibilityWithSampleTemplate(
      ApiInventoryImportSampleImportResult csvProcessingResult,
      List<String> columnNames,
      Map<Integer, String> columnIndexToDefaultFieldMap) {

    ApiSampleTemplate template = (ApiSampleTemplate) csvProcessingResult.getTemplate().getRecord();
    int templateFieldsCount = template.getFields().size();
    int csvFieldsMappedToTemplateFieldsCount =
        columnNames.size() - columnIndexToDefaultFieldMap.size();

    if (templateFieldsCount != csvFieldsMappedToTemplateFieldsCount) {
      ApiError error =
          bulkOperationHandler.convertExceptionToApiError(
              new IllegalArgumentException(
                  String.format(
                      "Number of unmapped CSV columns is %d, but number of fields in sample"
                          + " template is %d. The CSV file must exactly map all the template"
                          + " fields.",
                      csvFieldsMappedToTemplateFieldsCount, templateFieldsCount)));

      csvProcessingResult.addTemplateError(error);
    }

    if (csvProcessingResult.getTemplate().getError() != null) {
      csvProcessingResult.setErrorStatusAndResetSuccessCount(
          ApiInventoryBulkOperationResult.InventoryBulkOperationStatus.PREVALIDATION_ERROR);
    } else {
      csvProcessingResult.setStatus(
          ApiInventoryBulkOperationResult.InventoryBulkOperationStatus.PREVALIDATED);
    }
  }

  public void convertLinesToSamples(
      ApiInventoryImportSampleImportResult csvProcessingResult,
      List<String[]> linesToImport,
      Map<Integer, String> columnIndexToDefaultFieldMap,
      int expectedColumnsNumber) {

    ApiSampleTemplate template = (ApiSampleTemplate) csvProcessingResult.getTemplate().getRecord();

    int resultCount = 0;
    for (String[] line : linesToImport) {
      ApiSampleWithFullSubSamples apiSample = new ApiSampleWithFullSubSamples();
      apiSample.setTemplateId(template.getId());

      // if quantity mapping is set, the lines that don't set quantity should default to 0
      if (columnIndexToDefaultFieldMap.containsValue("quantity")) {
        apiSample.setQuantity(new ApiQuantityInfo(BigDecimal.ZERO, template.getDefaultUnitId()));
      }

      // try setting name & fields from line values
      try {
        if (line.length != expectedColumnsNumber) {
          throw new IllegalArgumentException(
              "Unexpected number of values in CSV line, "
                  + "expected: "
                  + expectedColumnsNumber
                  + ", was: "
                  + line.length);
        }
        for (int currentColumnIndex = 0; currentColumnIndex < line.length; currentColumnIndex++) {
          String value = line[currentColumnIndex];
          if (columnIndexToDefaultFieldMap.containsKey(currentColumnIndex)) {
            String fieldName = columnIndexToDefaultFieldMap.get(currentColumnIndex);
            if ("import identifier".equalsIgnoreCase(fieldName)) {
              if (!StringUtils.isBlank(value)) {
                if (csvProcessingResult.getResultNumberForImportId(value) != null) {
                  throw new IllegalArgumentException(
                      "Import identifier '"
                          + value
                          + "' was already used in row "
                          + (csvProcessingResult.getResultNumberForImportId(value) + 1));
                }
                csvProcessingResult.addResultNumberWithImportId(resultCount, value);
              }
            } else if ("parent container import id".equalsIgnoreCase(fieldName)) {
              if (!StringUtils.isBlank(value)) {
                csvProcessingResult.addResultNumberWithParentContainerImportId(resultCount, value);
              }
            } else if ("parent container global id".equalsIgnoreCase(fieldName)) {
              if (!StringUtils.isBlank(value)) {
                assertValidParentContainerGlobalId(value);
                csvProcessingResult.addResultNumberWithParentContainerGlobalId(
                    resultCount, new GlobalIdentifier(value));
              }
            } else {
              setDefaultFieldFromMappedColumn(apiSample, fieldName, value);
            }
          } else {
            ApiSampleField sampleField = new ApiSampleField();
            ApiSampleField templateField = template.getFields().get(apiSample.getFields().size());
            sampleField.setName(templateField.getName());
            sampleField.setType(templateField.getType());
            if (!StringUtils.isBlank(value)) {
              if (sampleField.isOptionsStoringField()) {
                sampleField.setSelectedOptions(Arrays.asList(value)); // assumes a single option
              } else {
                sampleField.setContent(value);
              }
            }
            apiSample.getFields().add(sampleField);
          }
        }
        csvProcessingResult.addSuccessResult(apiSample);
      } catch (RuntimeException iae) {
        csvProcessingResult.addError(getBadRequestIllegalArgumentApiError(iae.getMessage()));
      }

      resultCount++;
    }
  }
}
