package com.researchspace.service.inventory.csvimport;

import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryImportInstrumentImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportInstrumentParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.apiutils.ApiError;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CsvInstrumentImporter extends InventoryItemCsvImporter {

  @Value("${inventory.import.instrumentsLimit}")
  private Integer csvInstrumentLinesLimit;

  @Autowired private InventoryImportSampleFieldCreator importedFieldCreator;

  public CsvInstrumentImporter() {
    super(ApiInventoryRecordType.INSTRUMENT);
  }

  @PostConstruct
  private void setLinesLimit() {
    setCsvLinesLimit(csvInstrumentLinesLimit);
  }

  public ApiInventoryImportInstrumentParseResult parseInstrumentsCsvFile(
      String filename, InputStream inputStream, User createdBy) throws IOException {

    ApiInventoryImportInstrumentParseResult parseResult =
        new ApiInventoryImportInstrumentParseResult();
    readCsvIntoParseResults(inputStream, parseResult, createdBy);

    String templateName =
        filename.toLowerCase().endsWith(".csv")
            ? filename.substring(0, filename.length() - 4)
            : filename;

    InstrumentTemplate template = new InstrumentTemplate();
    template.setName(templateName);
    template.setOwner(createdBy);
    template.setCreatedBy(createdBy.getUsername());

    // for each CSV column suggest a template field
    int colIndex = 0;
    for (String columnName : parseResult.getColumnNames()) {
      String fieldName = parseResult.getFieldNameForColumnName().get(columnName);
      List<String> fieldValues = parseResult.getColumnNameToValuesMap().get(columnName);
      InventoryEntityField field =
          importedFieldCreator.getSuggestedSampleFieldForNameAndValues(fieldName, fieldValues);
      // assign columnIndex before adding to the template's field list, so that
      // getActiveFields()'s natural sort cannot NPE on a null columnIndex.
      field.setColumnIndex(++colIndex);
      field.setInventoryRecord(template);
      template.getFields().add(field);

      populateResultWithRadioOptionsForColumn(field, columnName, fieldValues, parseResult);
    }

    parseResult.setTemplateInfo(new ApiInstrumentTemplatePost(template));
    return parseResult;
  }

  private void populateResultWithRadioOptionsForColumn(
      InventoryEntityField field,
      String columnName,
      List<String> fieldValues,
      ApiInventoryImportInstrumentParseResult result) {

    if (FieldType.TEXT.equals(field.getType())) {
      return;
    }
    List<String> radioOptions =
        importedFieldCreator.calculateRadioOptions(new HashSet<>(fieldValues));
    result.getRadioOptionsForColumn().put(columnName, radioOptions);
  }

  @Override
  public void readCsvIntoImportResult(
      InputStream instrumentsIS,
      Map<String, String> csvColumnToFieldMapping,
      ApiInventoryImportResult importResult,
      User user)
      throws IOException {

    ApiInventoryImportInstrumentImportResult instrumentProcessingResult =
        importResult.getInstrumentResult();

    List<String[]> lines = readCsvLinesFromInputStream(instrumentsIS);
    assertNumberOfCsvLinesAcceptable(lines.size());

    List<String> columnNames = Arrays.asList(lines.get(0));
    assertNoRepeatingColumnNames(columnNames);
    Map<Integer, String> columnIndexToDefaultFieldMap =
        generateColumnIndexToDefaultFieldMap(csvColumnToFieldMapping, columnNames);

    checkInstrumentCsvCompatibilityWithTemplate(
        instrumentProcessingResult, columnNames, columnIndexToDefaultFieldMap);
    boolean isTemplateCompatible = instrumentProcessingResult.getTemplate().getRecord() != null;

    if (isTemplateCompatible) {
      List<String[]> csvInstrumentLines = lines.subList(1, lines.size());
      convertLinesToInstruments(
          instrumentProcessingResult,
          csvInstrumentLines,
          columnIndexToDefaultFieldMap,
          columnNames.size(),
          user);
    }
  }

  private void checkInstrumentCsvCompatibilityWithTemplate(
      ApiInventoryImportInstrumentImportResult csvProcessingResult,
      List<String> columnNames,
      Map<Integer, String> columnIndexToDefaultFieldMap) {

    ApiInstrumentTemplate template =
        (ApiInstrumentTemplate) csvProcessingResult.getTemplate().getRecord();
    int templateFieldsCount = template.getFields().size();
    int csvFieldsMappedToTemplateFieldsCount =
        columnNames.size() - columnIndexToDefaultFieldMap.size();

    if (templateFieldsCount != csvFieldsMappedToTemplateFieldsCount) {
      ApiError error =
          bulkOperationHandler.convertExceptionToApiError(
              new IllegalArgumentException(
                  messages.getMessage(
                      "errors.inventory.import.instrumentColumnCountMismatch",
                      new Object[] {csvFieldsMappedToTemplateFieldsCount, templateFieldsCount})));
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

  public void convertLinesToInstruments(
      ApiInventoryImportInstrumentImportResult csvProcessingResult,
      List<String[]> linesToImport,
      Map<Integer, String> columnIndexToDefaultFieldMap,
      int expectedColumnsNumber,
      User user) {

    ApiInstrumentTemplate template =
        (ApiInstrumentTemplate) csvProcessingResult.getTemplate().getRecord();

    int resultCount = 0;
    for (String[] line : linesToImport) {
      ApiInstrument apiInstrument = new ApiInstrument();
      apiInstrument.setTemplateId(template.getId());

      try {
        if (line.length != expectedColumnsNumber) {
          throw new IllegalArgumentException(
              messages.getMessage(
                  "errors.inventory.import.instrumentCsvLineUnexpectedColumnCount",
                  new Object[] {expectedColumnsNumber, line.length}));
        }
        for (int currentColumnIndex = 0; currentColumnIndex < line.length; currentColumnIndex++) {
          String value = line[currentColumnIndex];
          if (columnIndexToDefaultFieldMap.containsKey(currentColumnIndex)) {
            String fieldName = columnIndexToDefaultFieldMap.get(currentColumnIndex);
            if ("parent container global id".equalsIgnoreCase(fieldName)) {
              if (!StringUtils.isBlank(value)) {
                assertValidParentContainerGlobalId(value);
                GlobalIdentifier parentOid = new GlobalIdentifier(value);
                csvProcessingResult.addResultNumberWithParentContainerGlobalId(
                    resultCount, parentOid);
                ApiContainerInfo apiParent = new ApiContainerInfo();
                apiParent.setId(parentOid.getDbId());
                apiInstrument.setParentContainer(apiParent);
              }
            } else {
              setInstrumentFieldFromMappedColumn(apiInstrument, fieldName, value, user);
            }
          } else {
            ApiInventoryEntityField instrumentField = new ApiInventoryEntityField();
            ApiInventoryEntityField templateField =
                template.getFields().get(apiInstrument.getFields().size());
            instrumentField.setName(templateField.getName());
            instrumentField.setType(templateField.getType());
            if (!StringUtils.isBlank(value)) {
              if (instrumentField.isOptionsStoringField()) {
                instrumentField.setSelectedOptions(Arrays.asList(value));
              } else {
                instrumentField.setContent(value);
              }
            }
            apiInstrument.getFields().add(instrumentField);
          }
        }
        csvProcessingResult.addSuccessResult(apiInstrument);
      } catch (RuntimeException iae) {
        csvProcessingResult.addError(getBadRequestIllegalArgumentApiError(iae.getMessage()));
      }

      resultCount++;
    }
  }

  /**
   * Subset of default mappings supported for instrument import. Sample-specific defaults like
   * 'source', 'expiry date', and 'quantity' do not apply to instruments.
   */
  private void setInstrumentFieldFromMappedColumn(
      ApiInstrument apiInstrument, String fieldName, String value, User user) {
    if (StringUtils.isBlank(fieldName) || StringUtils.isBlank(value)) {
      return;
    }
    switch (fieldName.toLowerCase()) {
      case "name":
        apiInstrument.setName(value);
        break;
      case "description":
        apiInstrument.setDescription(value);
        break;
      case "tags":
        apiInstrument.setApiTagInfo(value);
        break;
      default:
        throw new IllegalArgumentException(
            messages.getMessage(
                "errors.inventory.import.instrumentUnrecognizedFieldMapping",
                new Object[] {fieldName}));
    }
  }
}
