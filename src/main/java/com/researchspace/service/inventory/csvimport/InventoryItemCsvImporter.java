package com.researchspace.service.inventory.csvimport;

import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryImportParseResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.csvexport.InventoryItemCsvExporter;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.naming.InvalidNameException;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

public abstract class InventoryItemCsvImporter {

  @Getter @Setter private Integer csvLinesLimit;

  private final ApiInventoryRecordType recordType;

  @Autowired protected InventoryBulkOperationHandler bulkOperationHandler;
  @Autowired protected InventoryImportSampleFieldCreator importedFieldCreator;
  @Autowired protected InventoryIdentifierApiManager inventoryIdentifierApiManager;
  @Autowired private ApiAvailabilityHandler apiHandler;

  private final Set<String> reservedSampleFieldNames = (new Sample()).getReservedFieldNames();

  protected InventoryItemCsvImporter(ApiInventoryRecordType recordType) {
    this.recordType = recordType;
  }

  public ApiInventoryImportParseResult readCsvIntoParseResults(
      InputStream inputStream, User createdBy) throws IOException {
    return readCsvIntoParseResults(inputStream, new ApiInventoryImportParseResult(), createdBy);
  }

  protected ApiInventoryImportParseResult readCsvIntoParseResults(
      InputStream inputStream, ApiInventoryImportParseResult result, User user) throws IOException {

    // read lines from csv
    result.setRows(readCsvLinesFromInputStream(inputStream));
    assertNumberOfCsvLinesAcceptable(result.getRows().size());

    // populate column/field names
    String[] csvColumnNames = result.getRows().get(0);
    List<String> fieldNames = getSampleFieldNamesForCsvColumnNames(csvColumnNames);
    assertNoRepeatingColumnNames(fieldNames);

    result.populateColumnNames(csvColumnNames, fieldNames);
    result.setRowsCount(result.getRows().size() - 1);

    result.convertRowsToFieldNameToValuesMap();
    result.populateResultWithNonBlankColumns();
    Map<String, String> fieldMapping = null;
    if (apiHandler.isInventoryAndDataciteEnabled(user)) {
      for (String columnName : result.getColumnNames()) {
        List<String> fieldValues = result.getColumnNameToValuesMap().get(columnName);
        fieldMapping = importedFieldCreator.getFieldMappingForIdentifier(columnName, fieldValues);
        if (!fieldMapping.isEmpty()) {
          result.getFieldMappings().putAll(fieldMapping);
        }
      }
    }

    return result;
  }

  public abstract void readCsvIntoImportResult(
      InputStream inputStream,
      Map<String, String> csvColumnToFieldMapping,
      ApiInventoryImportResult importResult,
      User user)
      throws IOException;

  protected void assertNumberOfCsvLinesAcceptable(int size) {
    if (size == 0) {
      throw new IllegalArgumentException("CSV file seems to be empty");
    }
    if (size == 1) {
      throw new IllegalArgumentException(
          "CSV file seems to have just one line, should have at least two: "
              + "one for column names, and another for a "
              + recordType.toString().toLowerCase()
              + " to import");
    }
    if (size > csvLinesLimit + 1) {
      throw new IllegalArgumentException(
          "CSV file is too long, import limit is set to "
              + csvLinesLimit
              + " "
              + recordType.toString().toLowerCase()
              + "s.");
    }
  }

  protected List<String[]> readCsvLinesFromInputStream(InputStream inputStream) throws IOException {
    CsvMapper mapper = new CsvMapper();
    mapper.enable(CsvParser.Feature.WRAP_AS_ARRAY);
    mapper.enable(CsvParser.Feature.SKIP_EMPTY_LINES);
    mapper.enable(CsvParser.Feature.TRIM_SPACES);
    MappingIterator<String[]> linesIterator =
        mapper.readerFor(String[].class).readValues(inputStream);
    List<String[]> csvLines = linesIterator.readAll();
    List<String[]> csvLinesWithoutInitialComments = removeInitialCommentLines(csvLines);
    return csvLinesWithoutInitialComments;
  }

  private List<String[]> removeInitialCommentLines(List<String[]> csvLines) {
    if (csvLines.isEmpty() || !isCsvCommentLine(csvLines.get(0))) {
      return csvLines;
    }
    List<String[]> csvLinesWithoutInitialComments = new ArrayList<>();
    for (int i = 0; i < csvLines.size(); i++) {
      if (!isCsvCommentLine(csvLines.get(i))) {
        csvLinesWithoutInitialComments.addAll(csvLines.subList(i, csvLines.size()));
        break;
      }
    }
    return csvLinesWithoutInitialComments;
  }

  private boolean isCsvCommentLine(String[] lineValues) {
    String firstValueInLine = lineValues.length > 0 ? lineValues[0] : null;
    return firstValueInLine != null
        && firstValueInLine.startsWith(InventoryItemCsvExporter.CSV_COMMENT_PREFIX);
  }

  private List<String> getSampleFieldNamesForCsvColumnNames(String[] columnNames) {
    if (columnNames.length == 0) {
      throw new IllegalArgumentException("First line of CSV file (column names) seems to be empty");
    }
    List<String> result = new ArrayList<>();
    for (String name : columnNames) {
      if (StringUtils.isBlank(name)) {
        throw new IllegalArgumentException("Found empty value in column names line");
      }
      String suggestedName = name.trim();
      if (reservedSampleFieldNames.contains(suggestedName.toLowerCase())) {
        suggestedName = "_" + suggestedName;
      }
      result.add(suggestedName);
    }
    return result;
  }

  public void assertNoRepeatingColumnNames(List<String> fieldNames) {
    if (fieldNames.stream().distinct().count() < fieldNames.size()) {
      throw new IllegalArgumentException("CSV file has repeating column names");
    }
  }

  protected Map<Integer, String> generateColumnIndexToDefaultFieldMap(
      Map<String, String> csvColumnToFieldMapping, List<String> columnNames) {

    Map<Integer, String> resultMap = new HashMap<>();
    for (Map.Entry<String, String> mapping : csvColumnToFieldMapping.entrySet()) {
      int columnIndex = getColumnIndexForColumnName(mapping.getKey(), columnNames);
      resultMap.put(columnIndex, mapping.getValue());
    }
    return resultMap;
  }

  protected int getColumnIndexForColumnName(String targetName, List<String> columnNames) {
    for (int i = 0; i < columnNames.size(); i++) {
      if (targetName.equals(columnNames.get(i))) {
        return i;
      }
    }
    throw new IllegalArgumentException(
        "Couldn't find '" + targetName + "' among columns in CSV file");
  }

  protected void setDefaultFieldFromMappedColumn(
      ApiInventoryRecordInfo apiInvRec, String fieldName, String value, User user)
      throws InvalidNameException {
    if (StringUtils.isBlank(fieldName)) {
      return; // column mapping target set to empty means column should be ignored
    }
    if (StringUtils.isBlank(value)) {
      return; // no need to translate empty value
    }

    switch (fieldName.toLowerCase()) {
      case "name":
        apiInvRec.setName(value);
        break;
      case "description":
        apiInvRec.setDescription(value);
        break;
      case "tags":
        apiInvRec.setApiTagInfo(value);
        break;
      case "source":
        ((ApiSampleWithFullSubSamples) apiInvRec).setSampleSource(SampleSource.valueOf(value));
        break;
      case "expiry date":
        ((ApiSampleWithFullSubSamples) apiInvRec).setExpiryDate(LocalDate.parse(value));
        break;
      case "quantity":
        apiInvRec.setQuantity(new ApiQuantityInfo(QuantityInfo.of(value)));
        break;
      case "identifier":
        apiHandler.assertInventoryAndDataciteEnabled(user);
        List<ApiInventoryDOI> identifierList =
            inventoryIdentifierApiManager.findIdentifiers("draft", false, value, false, user);
        if (identifierList.isEmpty()) {
          throw new IllegalArgumentException(
              "Unable to find an existing assignable identifier for " + fieldName + ": " + value);
        } else {
          apiInvRec.setIdentifiers(identifierList);
        }
        break;
      default:
        throw new IllegalArgumentException("unrecognized field mapping: " + fieldName);
    }
  }

  protected void assertValidParentContainerGlobalId(String value) {
    // parent container global id must be a valid container or workbench id
    if (!GlobalIdentifier.isValid(value)
        || (!value.startsWith(GlobalIdPrefix.BE.name())
            && !value.startsWith(GlobalIdPrefix.IC.name()))) {
      throw new IllegalArgumentException(
          "Parent Container Global Id '"
              + value
              + "' is not a valid global id of an inventory container");
    }
  }

  protected ApiError getBadRequestIllegalArgumentApiError(String msg) {
    return new ApiError(
        HttpStatus.BAD_REQUEST,
        ApiErrorCodes.ILLEGAL_ARGUMENT.getCode(),
        "Errors detected : 1",
        msg);
  }

  /* for testing purposes */
  public void setInventoryIdentifierManager(
      InventoryIdentifierApiManager inventoryIdentifierApiManager) {
    this.inventoryIdentifierApiManager = inventoryIdentifierApiManager;
  }

  public void setApiHandler(ApiAvailabilityHandler apiHandler) {
    this.apiHandler = apiHandler;
  }
}
