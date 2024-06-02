package com.researchspace.service.inventory.csvimport;

import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryImportSubSampleImportResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CsvSubSampleImporter extends InventoryItemCsvImporter {

  @Value("${inventory.import.subSamplesLimit}")
  private Integer csvSubSamplesLinesLimit;

  public CsvSubSampleImporter() {
    super(ApiInventoryRecordType.SUBSAMPLE);
    setCsvLinesLimit(csvSubSamplesLinesLimit);
  }

  @PostConstruct
  private void setLinesLimit() {
    setCsvLinesLimit(csvSubSamplesLinesLimit);
  }

  public void readCsvIntoImportResult(
      InputStream subSamplesIS,
      Map<String, String> csvColumnToFieldMapping,
      ApiInventoryImportResult importResult)
      throws IOException {

    // read lines from csv
    List<String[]> lines = readCsvLinesFromInputStream(subSamplesIS);
    assertNumberOfCsvLinesAcceptable(lines.size());

    // find column indexes of default container field mappings
    List<String> columnNames = Arrays.asList(lines.get(0));
    assertNoRepeatingColumnNames(columnNames);
    Map<Integer, String> columnIndexToDefaultFieldMap =
        generateColumnIndexToDefaultFieldMap(csvColumnToFieldMapping, columnNames);

    ApiInventoryImportSubSampleImportResult csvProcessingResult =
        new ApiInventoryImportSubSampleImportResult();
    importResult.setSubSampleResult(csvProcessingResult);

    // convert csv lines to subsamples and prevalidate
    List<String[]> csvSubSampleLines = lines.subList(1, lines.size());
    convertLinesToSubSamples(
        csvProcessingResult, csvSubSampleLines, columnIndexToDefaultFieldMap, columnNames.size());
  }

  public void convertLinesToSubSamples(
      ApiInventoryImportSubSampleImportResult csvProcessingResult,
      List<String[]> csvSubSampleLines,
      Map<Integer, String> columnIndexToDefaultFieldMap,
      int expectedColumnsNumber) {

    int resultCount = 0;
    for (String[] line : csvSubSampleLines) {
      ApiSubSample apiSubSample = new ApiSubSample();

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
            if ("parent sample import id".equalsIgnoreCase(fieldName)) {
              if (!StringUtils.isBlank(value)) {
                csvProcessingResult.addResultNumberWithParentSampleImportId(resultCount, value);
              }
            } else if ("parent sample global id".equalsIgnoreCase(fieldName)) {
              if (!StringUtils.isBlank(value)) {
                assertValidParentSampleGlobalId(value);
                csvProcessingResult.addResultNumberWithParentSampleGlobalId(
                    resultCount, new GlobalIdentifier(value));
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
              setDefaultFieldFromMappedColumn(apiSubSample, fieldName, value);
            }
          }
        }
        csvProcessingResult.addSuccessResult(apiSubSample);

      } catch (RuntimeException iae) {
        csvProcessingResult.addError(getBadRequestIllegalArgumentApiError(iae.getMessage()));
      }

      resultCount++;
    }
  }

  private void assertValidParentSampleGlobalId(String value) {
    if (!GlobalIdentifier.isValid(value) || (!value.startsWith(GlobalIdPrefix.SA.name()))) {
      throw new IllegalArgumentException(
          "Parent Sample Global Id '" + value + "' is not a valid global id of a sample");
    }
  }
}
