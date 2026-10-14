package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.core.util.JacksonUtil;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ApiInventoryImportParseResultTest {

  private static ApiInventoryImportParseResult parse(String[]... rows) {
    ApiInventoryImportParseResult result = new ApiInventoryImportParseResult();
    result.setRows(List.of(rows));
    result.populateColumnNames(rows[0], List.of(rows[0]));
    result.convertRowsToFieldNameToValuesMap();
    result.populateBlankValueColumns();
    return result;
  }

  @Test
  void blankColumnsListsOnlyColumnsWithNoValueInAnyRow() {
    ApiInventoryImportParseResult result =
        parse(
            new String[] {"Name", "Empty", "Spaces", "Partly"},
            new String[] {"a", "", " ", "x"},
            new String[] {"b", "", "  ", ""});

    assertEquals(List.of("Empty", "Spaces"), result.getBlankColumns());
    assertEquals(List.of("Name"), result.getColumnsWithoutBlankValue());
  }

  @Test
  void blankColumnsReachesTheImportPageUnderThatName() {
    ApiInventoryImportParseResult result =
        parse(new String[] {"Name", "Empty"}, new String[] {"a", ""});

    Map<?, ?> json = JacksonUtil.fromJson(JacksonUtil.toJson(result), Map.class);

    assertEquals(List.of("Empty"), json.get("blankColumns"));
  }
}
