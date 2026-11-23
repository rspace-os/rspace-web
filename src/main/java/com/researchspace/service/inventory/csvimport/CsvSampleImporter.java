package com.researchspace.service.inventory.csvimport;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSampleParseResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.csvexport.InventoryItemCsvExporter;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.naming.InvalidNameException;
import org.apache.commons.lang3.StringUtils;
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
    readCsvIntoParseResults(inputStream, sampleParseResult, createdBy);

    String templateName =
        filename.toLowerCase().endsWith(".csv")
            ? filename.substring(0, filename.length() - 4)
            : filename;
    SampleTemplate template = recordFactory.createSampleTemplate(templateName, createdBy);
    setDefaultsInSuggestedParsedTemplate(template);

    // for each column decide field type and add to result template
    for (String columnName : sampleParseResult.getColumnNames()) {
      String fieldName = sampleParseResult.getFieldNameForColumnName().get(columnName);
      List<String> fieldValues = sampleParseResult.getColumnNameToValuesMap().get(columnName);
      InventoryEntityField field =
          importedFieldCreator.getSuggestedSampleFieldForNameAndValues(fieldName, fieldValues);
      template.addSampleField(field);

      populateResultWithRadioOptionsForColumn(field, columnName, fieldValues, sampleParseResult);
      populateResultWithQuantityTypeForColumn(field, columnName, fieldValues, sampleParseResult);
    }

    sampleParseResult.setTemplateInfo(new ApiSampleTemplatePost(template));

    return sampleParseResult;
  }

  private void setDefaultsInSuggestedParsedTemplate(SampleTemplate template) {
    template.setSampleSource(SampleSource.OTHER);
  }

  private void populateResultWithRadioOptionsForColumn(
      InventoryEntityField field,
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
      InventoryEntityField field,
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

  @Override
  public void readCsvIntoImportResult(
      InputStream samplesIS,
      Map<String, String> csvColumnToFieldMapping,
      ApiInventoryImportResult importResult,
      User user)
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

    Map<Integer, Integer> columnIndexToTemplateFieldIndexMap =
        checkSampleCsvCompatibilityWithSampleTemplate(
            sampleProcessingResult, columnNames, columnIndexToDefaultFieldMap);
    boolean isTemplateCompatible = sampleProcessingResult.getTemplate().getRecord() != null;

    if (isTemplateCompatible) {
      List<String[]> csvSampleLines = lines.subList(1, lines.size());
      convertLinesToSamples(
          sampleProcessingResult,
          csvSampleLines,
          columnIndexToDefaultFieldMap,
          columnIndexToTemplateFieldIndexMap,
          columnNames.size(),
          user);
    }
  }

  private Map<Integer, Integer> checkSampleCsvCompatibilityWithSampleTemplate(
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

    Map<Integer, Integer> columnIndexToTemplateFieldIndexMap =
        csvProcessingResult.getTemplate().getError() != null
            ? null
            : mapColumnsToTemplateFieldsOrAddTemplateError(
                columnNames,
                columnIndexToDefaultFieldMap.keySet(),
                template.getFields(),
                csvProcessingResult.isTemplateCreated(),
                csvProcessingResult::addTemplateError);

    if (csvProcessingResult.getTemplate().getError() != null) {
      csvProcessingResult.setErrorStatusAndResetSuccessCount(
          ApiInventoryBulkOperationResult.InventoryBulkOperationStatus.PREVALIDATION_ERROR);
    } else {
      csvProcessingResult.setStatus(
          ApiInventoryBulkOperationResult.InventoryBulkOperationStatus.PREVALIDATED);
    }
    return columnIndexToTemplateFieldIndexMap;
  }

  /** Converts lines whose columns not mapped to default fields fill the template in order. */
  public void convertLinesToSamples(
      ApiInventoryImportSampleImportResult csvProcessingResult,
      List<String[]> linesToImport,
      Map<Integer, String> columnIndexToDefaultFieldMap,
      int expectedColumnsNumber,
      User user) {
    convertLinesToSamples(
        csvProcessingResult,
        linesToImport,
        columnIndexToDefaultFieldMap,
        getPositionalColumnIndexToTemplateFieldIndexMap(
            expectedColumnsNumber, columnIndexToDefaultFieldMap.keySet()),
        expectedColumnsNumber,
        user);
  }

  public void convertLinesToSamples(
      ApiInventoryImportSampleImportResult csvProcessingResult,
      List<String[]> linesToImport,
      Map<Integer, String> columnIndexToDefaultFieldMap,
      Map<Integer, Integer> columnIndexToTemplateFieldIndexMap,
      int expectedColumnsNumber,
      User user) {

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
      ApiInventoryEntityField[] fieldsInTemplateOrder =
          new ApiInventoryEntityField[template.getFields().size()];
      try {
        if (line.length != expectedColumnsNumber) {
          throw new IllegalArgumentException(
              messages.getMessage(
                  "errors.inventory.import.csvLineUnexpectedColumnCount",
                  new Object[] {expectedColumnsNumber, line.length}));
        }
        for (int currentColumnIndex = 0; currentColumnIndex < line.length; currentColumnIndex++) {
          String value = line[currentColumnIndex];
          if (columnIndexToDefaultFieldMap.containsKey(currentColumnIndex)) {
            String fieldName = columnIndexToDefaultFieldMap.get(currentColumnIndex);
            if ("import identifier".equalsIgnoreCase(fieldName)) {
              if (!StringUtils.isBlank(value)) {
                if (csvProcessingResult.getResultNumberForImportId(value) != null) {
                  throw new IllegalArgumentException(
                      messages.getMessage(
                          "errors.inventory.import.importIdentifierAlreadyUsed",
                          new Object[] {
                            value, csvProcessingResult.getResultNumberForImportId(value) + 1
                          }));
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
              setDefaultFieldFromMappedColumn(apiSample, fieldName, value, user);
            }
          } else {
            ApiInventoryEntityField sampleField = new ApiInventoryEntityField();
            int templateFieldIndex = columnIndexToTemplateFieldIndexMap.get(currentColumnIndex);
            ApiInventoryEntityField templateField = template.getFields().get(templateFieldIndex);
            sampleField.setName(templateField.getName());
            sampleField.setType(templateField.getType());
            if (!StringUtils.isBlank(value)) {
              if (sampleField.isOptionsStoringField()) {
                sampleField.setSelectedOptions(
                    getSelectedOptionsFromCsvValue(value, templateField));
              } else if (ApiFieldType.LINK.equals(sampleField.getType())) {
                // the exporter writes its absent-column sentinel for a row that has no such
                // column; that is no link, not a malformed one, so it must not fail the row
                if (!InventoryItemCsvExporter.isAbsentCsvValue(value)) {
                  sampleField.setLink(linkParser.parse(value, user));
                }
              } else {
                sampleField.setContent(value);
              }
            }
            fieldsInTemplateOrder[templateFieldIndex] = sampleField;
          }
        }
        Arrays.stream(fieldsInTemplateOrder)
            .filter(Objects::nonNull)
            .forEach(apiSample.getFields()::add);
        csvProcessingResult.addSuccessResult(apiSample);
      } catch (RuntimeException | InvalidNameException iae) {
        csvProcessingResult.addError(
            getBadRequestIllegalArgumentApiError(messages.getExceptionMessage(iae)));
      }

      resultCount++;
    }
  }
}
