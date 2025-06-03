package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({
  "templateInfo",
  "fieldNameForColumnName",
  "radioOptionsForColumn",
  "quantityUnitForColumn",
  "columnsWithoutBlankValue",
  "columnNames",
  "rowsCount"
})
public class ApiInventoryImportSampleParseResult extends ApiInventoryImportParseResult {

  @JsonProperty("templateInfo")
  private ApiSampleTemplatePost templateInfo;

  @JsonProperty("radioOptionsForColumn")
  private Map<String, List<String>> radioOptionsForColumn = new HashMap<>();

  @JsonProperty("quantityUnitForColumn")
  private Map<String, Integer> quantityUnitForColumn = new HashMap<>();

  public void populateColumnNames(String[] csvColumnNames, List<String> fieldNames) {
    super.populateColumnNames(csvColumnNames, fieldNames);
    for (int i = 0; i < fieldNames.size(); i++) {
      getFieldNameForColumnName().put(csvColumnNames[i], fieldNames.get(i));
    }
  }
}
