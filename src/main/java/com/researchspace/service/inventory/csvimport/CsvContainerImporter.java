package com.researchspace.service.inventory.csvimport;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryImportPartialResult;
import com.researchspace.api.v1.model.ApiInventoryImportResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.naming.InvalidNameException;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CsvContainerImporter extends InventoryItemCsvImporter {

  @Value("${inventory.import.containersLimit}")
  private Integer csvContainerLinesLimit;

  public CsvContainerImporter() {
    super(ApiInventoryRecordType.CONTAINER);
  }

  @PostConstruct
  private void setLinesLimit() {
    setCsvLinesLimit(csvContainerLinesLimit);
  }

  @Override
  public void readCsvIntoImportResult(
      InputStream inputStream,
      Map<String, String> csvColumnToFieldMapping,
      ApiInventoryImportResult importResult,
      User user)
      throws IOException {

    // read lines from csv
    List<String[]> lines = readCsvLinesFromInputStream(inputStream);
    assertNumberOfCsvLinesAcceptable(lines.size());

    // find column indexes of default container field mappings
    List<String> columnNames = Arrays.asList(lines.get(0));
    assertNoRepeatingColumnNames(columnNames);
    Map<Integer, String> columnIndexToDefaultFieldMap =
        generateColumnIndexToDefaultFieldMap(csvColumnToFieldMapping, columnNames);

    ApiInventoryImportPartialResult csvProcessingResult =
        new ApiInventoryImportPartialResult(ApiInventoryRecordType.CONTAINER);
    importResult.setContainerResult(csvProcessingResult);

    // convert csv lines to containers and prevalidate
    List<String[]> csvContainerLines = lines.subList(1, lines.size());
    convertLinesToContainers(
        csvProcessingResult,
        csvContainerLines,
        columnIndexToDefaultFieldMap,
        columnNames.size(),
        user);
  }

  public void convertLinesToContainers(
      ApiInventoryImportPartialResult csvProcessingResult,
      List<String[]> csvContainerLines,
      Map<Integer, String> columnIndexToDefaultFieldMap,
      int expectedColumnsNumber,
      User user) {

    int resultCount = 0;
    for (String[] line : csvContainerLines) {
      ApiContainer apiContainer = new ApiContainer();
      apiContainer.setCType("LIST"); // default

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
              setDefaultFieldFromMappedColumn(apiContainer, fieldName, value, user);
            }
          }
        }
        csvProcessingResult.addSuccessResult(apiContainer);

      } catch (RuntimeException | InvalidNameException iae) {
        csvProcessingResult.addError(getBadRequestIllegalArgumentApiError(iae.getMessage()));
      }

      resultCount++;
    }
  }
}
