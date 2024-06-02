package com.researchspace.webapp.controller;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.model.record.TestFactory.createAnyRecord;
import static com.researchspace.webapp.controller.AuditTrailSearchResultCsvGenerator.MAX_RESULTS_EXCEEDED;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditData;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.io.IOException;
import java.time.Instant;
import org.junit.Test;
import org.springframework.http.ResponseEntity;

public class AuditTrailSearchResultCsvGeneratorTest {
  AuditTrailSearchResultCsvGenerator auditTrailSearchResultCsvGenerator =
      new AuditTrailSearchResultCsvGenerator();
  User anyUser = TestFactory.createAnyUser("any");

  @Test
  public void emptyResultsHandledGracefully() throws IOException {
    ISearchResults<AuditTrailSearchResult> emptyResults = createEmptySearchResults();
    ResponseEntity<String> results =
        auditTrailSearchResultCsvGenerator.convertToCsv(emptyResults, defaultSearchConfig());
    assertEquals(2, countLines(results));
    assertThat(results.getBody(), not(containsString(MAX_RESULTS_EXCEEDED)));
  }

  @Test
  public void moveDescriptionIncluded() throws IOException {
    Record aRecord = createRecordWithId(25L);
    String moveData =
        "{\"data\":{\"from\":{\"data\":{\"id\":\"FL160\",\"name\":\"Examples\",\"type\":\"FOLDER:SYSTEM\"}},\"id\":\"FL189\",\"name\":\"a\",\"to\":{\"data\":{\"id\":\"FL155\",\"name\":\"user5e\",\"type\":\"FOLDER:ROOT\"}},\"type\":\"FOLDER\"}}";
    AuditData parsedMoveLineAuditData = AuditData.fromJson(moveData);
    ISearchResults<AuditTrailSearchResult> validResults =
        createValidSearchResultForMoveEvent(aRecord, parsedMoveLineAuditData);
    ResponseEntity<String> results =
        auditTrailSearchResultCsvGenerator.convertToCsv(validResults, defaultSearchConfig());
    assertEquals(3, countLines(results));
    // desc column of 1st result row
    String desc = getCellByRowColumn(results, 2, 6);
    assertThat(desc, startsWith("From Examples (FL160) to user5e (FL155)"));
  }

  @Test
  public void exportDescriptionIncluded() throws IOException {
    Record aRecord = createRecordWithId(25L);
    String exportLogData =
        "{\"data\":{\"configuration\":{\"archiveType\":\"html\",\"exportScope\":\"SELECTION\"},\"exportPath\":{\"name\":\"RSpace-2020-01-10-11-51-html-field-elements-experiment-k-PmEed59VS8ZA.zip\"},\"exported\":[{\"name\":\"field"
            + " elements experiment\",\"id\":30495}]}}";
    AuditData parsedExportLineAuditData = AuditData.fromJson(exportLogData);
    ISearchResults<AuditTrailSearchResult> validResults =
        createValidExportResultForMoveEvent(aRecord, parsedExportLineAuditData);
    ResponseEntity<String> results =
        auditTrailSearchResultCsvGenerator.convertToCsv(validResults, defaultSearchConfig());
    assertEquals(3, countLines(results));
    String desc = getCellByRowColumn(results, 2, 6);
    assertThat(desc, startsWith("1 item(s) exported: 30495"));
  }

  private String getCellByRowColumn(ResponseEntity<String> results, int row, int column) {
    String csvLine = results.getBody().split("\\n")[row];
    String desc = csvLine.split(",")[column];
    return desc;
  }

  @Test
  public void missingNameIdAndDescriptionHandled() throws IOException {
    Record aRecord = createRecordWithId(25L);
    ISearchResults<AuditTrailSearchResult> validResults = createValidSearchResults(aRecord, null);
    ResponseEntity<String> results =
        auditTrailSearchResultCsvGenerator.convertToCsv(validResults, defaultSearchConfig());
    assertEquals(3, countLines(results));
    assertThat(results.getBody(), not(containsString(MAX_RESULTS_EXCEEDED)));
    String id = getCellByRowColumn(results, 2, 4);
    assertThat(id, not(containsString("25")));
    assertThat(results.getBody(), not(containsString(aRecord.getName())));
  }

  @Test
  public void nameIdAndDescriptionIncludedIfPresent() throws IOException {
    Record aRecord = createRecordWithId(25L);
    AuditData data = auditDataWithIdOrName(25L, aRecord.getName());
    ISearchResults<AuditTrailSearchResult> results = createValidSearchResults(aRecord, data);
    ResponseEntity<String> csvResponse =
        auditTrailSearchResultCsvGenerator.convertToCsv(results, defaultSearchConfig());
    assertEquals(3, countLines(csvResponse));
    assertThat(csvResponse.getBody(), not(containsString(MAX_RESULTS_EXCEEDED)));
    String id = getCellByRowColumn(csvResponse, 2, 4);
    assertThat(id, containsString("25"));
    assertThat(csvResponse.getBody(), containsString(aRecord.getName()));
  }

  private AuditTrailUISearchConfig defaultSearchConfig() {
    return new AuditTrailUISearchConfig();
  }

  @Test
  public void commentsInCsvNoteMaxResultsExceeded() throws IOException {
    Record aRecord = createRecordWithId(25L);
    AuditData data = auditDataWithIdOrName(25L, aRecord.getName());
    ISearchResults<AuditTrailSearchResult> results = createMoreThanMaxSearchResults(aRecord, data);
    ResponseEntity<String> csvResponse =
        auditTrailSearchResultCsvGenerator.convertToCsv(results, defaultSearchConfig());
    assertEquals(3, countLines(csvResponse));
    assertThat(csvResponse.getBody(), containsString(MAX_RESULTS_EXCEEDED));
  }

  private Record createRecordWithId(Long id) {
    Record aRecord = createAnyRecord(anyUser);
    aRecord.setId(id);
    return aRecord;
  }

  private ISearchResults<AuditTrailSearchResult> createMoreThanMaxSearchResults(
      Record aRecord, AuditData data) {
    AuditTrailSearchResult result = createValidSearchResult(aRecord, data);
    // this just simulates max results exceeded
    return new SearchResultsImpl<AuditTrailSearchResult>(
        TransformerUtils.toList(result),
        0,
        1 + AuditTrailSearchResultCsvGenerator.MAX_RESULTS_PER_CSV);
  }

  private ISearchResults<AuditTrailSearchResult> createValidSearchResults(
      Record aRecord, AuditData data) {
    AuditTrailSearchResult result = createValidSearchResult(aRecord, data);
    return new SearchResultsImpl<AuditTrailSearchResult>(toList(result), 0, 1);
  }

  private AuditTrailSearchResult createValidSearchResult(Record aRecord, AuditData data) {
    HistoricData historicData =
        new HistoricData(
            AuditDomain.RECORD, new GenericEvent(anyUser, aRecord, AuditAction.CREATE), data);
    AuditTrailSearchResult result =
        new AuditTrailSearchResult(historicData, Instant.now().toEpochMilli());
    return result;
  }

  private ISearchResults<AuditTrailSearchResult> createValidSearchResultForMoveEvent(
      Record aRecord, AuditData data) {
    return doCreateSearchResult(aRecord, data, AuditAction.MOVE);
  }

  private ISearchResults<AuditTrailSearchResult> createValidExportResultForMoveEvent(
      Record aRecord, AuditData data) {
    return doCreateSearchResult(aRecord, data, AuditAction.EXPORT);
  }

  private ISearchResults<AuditTrailSearchResult> doCreateSearchResult(
      Record aRecord, AuditData data, AuditAction action) {
    HistoricData historicData =
        new HistoricData(AuditDomain.RECORD, new GenericEvent(anyUser, aRecord, action), data);
    AuditTrailSearchResult result =
        new AuditTrailSearchResult(historicData, Instant.now().toEpochMilli());
    return new SearchResultsImpl<AuditTrailSearchResult>(toList(result), 0, 1);
  }

  private AuditData auditDataWithIdOrName(Long id, String name) {
    AuditData data = new AuditData();
    data.getData().put("id", id);
    data.getData().put("name", name);
    return data;
  }

  private int countLines(ResponseEntity<String> results) {
    return results.getBody().split("\n").length;
  }

  private ISearchResults<AuditTrailSearchResult> createEmptySearchResults() {
    return SearchResultsImpl.emptyResult(pgCrit());
  }

  private PaginationCriteria<AuditTrailSearchResult> pgCrit() {
    return PaginationCriteria.createDefaultForClass(AuditTrailSearchResult.class);
  }
}
