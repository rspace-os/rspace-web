package com.researchspace.service.inventory.csvimport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvParser;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
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
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.csvexport.InventoryItemCsvExporter;
import com.researchspace.service.inventory.impl.InventoryBulkOperationHandler;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.naming.InvalidNameException;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

public abstract class InventoryItemCsvImporter {

  @Getter @Setter private Integer csvLinesLimit;

  private final ApiInventoryRecordType recordType;

  @Autowired protected InventoryBulkOperationHandler bulkOperationHandler;
  @Autowired protected InventoryImportSampleFieldCreator importedFieldCreator;
  @Autowired protected CsvLinkValueParser linkParser;
  @Autowired protected InventoryIdentifierApiManager inventoryIdentifierApiManager;
  @Autowired protected MessageSourceUtils messages;
  @Autowired private ApiAvailabilityHandler apiHandler;

  private final Set<String> reservedSampleFieldNames = (new Sample()).getReservedFieldNames();

  /** The exporter's template field header: {@code Name (TYPE)} or {@code Name (TYPE, IT<n>)}. */
  private static final ObjectReader STRICT_JSON_READER =
      new ObjectMapper().reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

  private static final Pattern EXPORTED_FIELD_COLUMN_NAME =
      Pattern.compile(
          "^(.*?)\\s*\\(("
              + Arrays.stream(FieldType.values()).map(Enum::name).collect(Collectors.joining("|"))
              + ")(,\\s*[A-Z]{2}\\d+(v\\d+)?)?\\)$",
          Pattern.CASE_INSENSITIVE);

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
    result.populateBlankValueColumns();
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

  /**
   * Decides which template field each CSV column not mapped to a default field fills. When the
   * template was created from this CSV, the columns fill the template fields in order. Otherwise a
   * column whose header names a template field fills that field, ignoring case, surrounding spaces,
   * the exporter's {@code (TYPE, IT<n>)} suffix and the {@code _} prefix the parse step gives
   * reserved names; the other columns fill the remaining template fields in order, as before.
   *
   * @param columnNames CSV header line
   * @param defaultMappedColumnIndexes indexes of the columns mapped to default fields
   * @param templateFields template fields, as many as the columns not mapped to default fields
   * @param templateCreatedFromCsv whether the template was just created from this CSV's columns
   * @return template field index for each column index not mapped to a default field
   * @throws IllegalArgumentException if two headers name the same template field
   */
  protected Map<Integer, Integer> getColumnIndexToTemplateFieldIndexMap(
      List<String> columnNames,
      Set<Integer> defaultMappedColumnIndexes,
      List<ApiInventoryEntityField> templateFields,
      boolean templateCreatedFromCsv) {

    Map<Integer, Integer> positionalMap =
        getPositionalColumnIndexToTemplateFieldIndexMap(
            columnNames.size(), defaultMappedColumnIndexes);
    if (templateCreatedFromCsv) {
      return positionalMap;
    }

    Map<Integer, Integer> nameMap = new LinkedHashMap<>();
    for (Integer columnIndex : positionalMap.keySet()) {
      Integer templateFieldIndex =
          findTemplateFieldIndexForColumnName(columnNames.get(columnIndex), templateFields);
      if (templateFieldIndex != null) {
        nameMap.put(columnIndex, templateFieldIndex);
      }
    }
    if (nameMap.isEmpty()) {
      return positionalMap;
    }
    Map<Integer, List<String>> columnNamesByTemplateFieldIndex = new LinkedHashMap<>();
    nameMap.forEach(
        (columnIndex, templateFieldIndex) ->
            columnNamesByTemplateFieldIndex
                .computeIfAbsent(templateFieldIndex, k -> new ArrayList<>())
                .add(columnNames.get(columnIndex).trim()));
    for (Map.Entry<Integer, List<String>> entry : columnNamesByTemplateFieldIndex.entrySet()) {
      if (entry.getValue().size() > 1) {
        throw new IllegalArgumentException(
            messages.getMessage(
                "errors.inventory.import.csvColumnsMatchingSameTemplateField",
                new Object[] {
                  quoteColumnNames(entry.getValue()), templateFields.get(entry.getKey()).getName()
                }));
      }
    }
    // columns whose header names no template field keep filling the remaining fields in order
    List<Integer> remainingTemplateFieldIndexes = new ArrayList<>();
    for (int i = 0; i < templateFields.size(); i++) {
      if (!nameMap.containsValue(i)) {
        remainingTemplateFieldIndexes.add(i);
      }
    }
    Map<Integer, Integer> result = new LinkedHashMap<>();
    int nextRemaining = 0;
    for (Integer columnIndex : positionalMap.keySet()) {
      Integer templateFieldIndex = nameMap.get(columnIndex);
      result.put(
          columnIndex,
          templateFieldIndex != null
              ? templateFieldIndex
              : remainingTemplateFieldIndexes.get(nextRemaining++));
    }
    return result;
  }

  /** Columns not mapped to default fields fill the template fields in order. */
  protected Map<Integer, Integer> getPositionalColumnIndexToTemplateFieldIndexMap(
      int columnCount, Set<Integer> defaultMappedColumnIndexes) {
    Map<Integer, Integer> result = new LinkedHashMap<>();
    for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
      if (!defaultMappedColumnIndexes.contains(columnIndex)) {
        result.put(columnIndex, result.size());
      }
    }
    return result;
  }

  private Integer findTemplateFieldIndexForColumnName(
      String columnName, List<ApiInventoryEntityField> templateFields) {
    String trimmedName = columnName.trim();
    Integer index = findTemplateFieldIndexForName(trimmedName, templateFields);
    if (index == null && reservedSampleFieldNames.contains(trimmedName.toLowerCase())) {
      // parsing a CSV into a template names the field for a reserved column name "_<name>"
      index = findTemplateFieldIndexForName("_" + trimmedName, templateFields);
    }
    if (index == null) {
      Matcher exportedName = EXPORTED_FIELD_COLUMN_NAME.matcher(trimmedName);
      if (exportedName.matches()) {
        index = findTemplateFieldIndexForName(exportedName.group(1).trim(), templateFields);
      }
    }
    return index;
  }

  private Integer findTemplateFieldIndexForName(
      String name, List<ApiInventoryEntityField> templateFields) {
    for (int i = 0; i < templateFields.size(); i++) {
      String fieldName = templateFields.get(i).getName();
      if (fieldName != null && fieldName.trim().equalsIgnoreCase(name)) {
        return i;
      }
    }
    return null;
  }

  private String quoteColumnNames(List<String> columnNames) {
    return columnNames.stream().map(name -> "\"" + name + "\"").collect(Collectors.joining(", "));
  }

  /**
   * Options selected by a CSV cell of a radio or choice field: the cell if it is an allowed option,
   * else a JSON array of strings (the exporter's format), else for a choice field a comma list of
   * allowed options. Any other cell is taken as one option, which validation rejects.
   */
  protected List<String> getSelectedOptionsFromCsvValue(
      String value, ApiInventoryEntityField templateField) {
    List<String> allowedOptions =
        templateField.getDefinition() == null
            ? List.of()
            : templateField.getDefinition().getOptions();
    if (allowedOptions.contains(value)) {
      return List.of(value);
    }
    List<String> jsonOptions = parseJsonArrayOfStrings(value.trim());
    if (jsonOptions != null) {
      return jsonOptions;
    }
    if (ApiFieldType.CHOICE.equals(templateField.getType())) {
      List<String> listedOptions = Arrays.stream(value.split(",", -1)).map(String::trim).toList();
      if (listedOptions.stream().noneMatch(String::isEmpty)
          && allowedOptions.containsAll(listedOptions)) {
        return listedOptions;
      }
    }
    return List.of(value);
  }

  /** The strings of a cell that is exactly one JSON array of strings, else null. */
  private List<String> parseJsonArrayOfStrings(String value) {
    if (!value.startsWith("[")) {
      return null;
    }
    JsonNode node;
    try {
      node = STRICT_JSON_READER.readTree(value);
    } catch (JsonProcessingException e) {
      return null;
    }
    if (!node.isArray()) {
      return null;
    }
    List<String> options = new ArrayList<>();
    for (JsonNode element : node) {
      if (!element.isTextual()) {
        return null;
      }
      options.add(element.textValue());
    }
    return options;
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
              "Unable to find an existing assignable identifier: " + value);
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
          messages.getMessage(
              "errors.inventory.import.parentContainerGlobalIdInvalid", new Object[] {value}));
    }
  }

  protected ApiError getBadRequestIllegalArgumentApiError(String msg) {
    return new ApiError(
        HttpStatus.BAD_REQUEST,
        ApiErrorCodes.ILLEGAL_ARGUMENT.getCode(),
        messages.getMessage("api.errors.detected", new Object[] {1}),
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
