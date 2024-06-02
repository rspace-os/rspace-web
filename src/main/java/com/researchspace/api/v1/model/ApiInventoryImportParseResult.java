package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
@JsonPropertyOrder({"columnNames", "columnsWithoutBlankValue", "rowsCount"})
public class ApiInventoryImportParseResult {

  @JsonProperty("columnNames")
  private List<String> columnNames;

  @JsonProperty("rowsCount")
  private Integer rowsCount;

  @JsonProperty("columnsWithoutBlankValue")
  private List<String> columnsWithoutBlankValue = new ArrayList<>();

  // for internal result processing
  @JsonIgnore private List<String[]> rows;
  @JsonIgnore private Map<String, List<String>> columnNameToValuesMap;

  public void populateColumnNames(String[] csvColumnNames, List<String> fieldNames) {
    columnNames = Arrays.asList(csvColumnNames);
  }

  public void convertRowsToFieldNameToValuesMap() {
    Map<String, List<String>> fieldNameToValuesMap = new HashMap<>();
    int numberOfColumns = rows.get(0).length;
    for (int columnCount = 0; columnCount < numberOfColumns; columnCount++) {
      String name = columnNames.get(columnCount);
      List<String> values = new ArrayList<>();
      for (int linesCount = 1;
          linesCount < rows.size();
          linesCount++) { // start from 2nd line (skip column names)
        String[] lineValues = rows.get(linesCount);
        if (columnCount < lineValues.length) { // if line is shorter, skip the value
          values.add(lineValues[columnCount]);
        }
      }
      fieldNameToValuesMap.put(name, values);
    }
    columnNameToValuesMap = fieldNameToValuesMap;
  }

  public void populateResultWithNonBlankColumns() {
    getColumnsWithoutBlankValue().clear();
    for (String columnName : getColumnNames()) {
      List<String> fieldValues = getColumnNameToValuesMap().get(columnName);
      if (fieldValues.stream().noneMatch(String::isBlank)) {
        getColumnsWithoutBlankValue().add(columnName);
      }
    }
  }
}
