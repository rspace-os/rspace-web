package com.researchspace.webapp.controller;

import static com.researchspace.core.util.DateUtil.convertDateToISOFormat;
import static java.util.stream.Collectors.joining;

import com.researchspace.core.util.DateUtil;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvBeanWriter;
import org.supercsv.prefs.CsvPreference;

/** Converts Audit trail results to CSV format for download. */
@Slf4j
public class AuditTrailSearchResultCsvGenerator {
  static final String ATTACHMENT_FILENAME_RSPACE_AUDIT_TRAIL_CSV =
      "attachment; filename=\"rspace-audit-trail.csv\"";
  static final int MAX_RESULTS_PER_CSV = 10_000;
  static final String MAX_RESULTS_EXCEEDED =
      String.format("Results are truncated at %d results", MAX_RESULTS_PER_CSV);

  private String[] HEADER = {"time", "user", "action", "type", "resource", "name", "description"};
  private static final CellProcessor[] CELL_PROCESSORS =
      new CellProcessor[] {
        null,
        null,
        null,
        null,
        new org.supercsv.cellprocessor.Optional(),
        new org.supercsv.cellprocessor.Optional(),
        new org.supercsv.cellprocessor.Optional()
      };

  ResponseEntity<String> convertToCsv(
      ISearchResults<AuditTrailSearchResult> res, AuditTrailUISearchConfig inputSearchConfig)
      throws IOException {
    StringWriter swStringWriter = new StringWriter(10000); // 10k initial buffer
    try (CsvBeanWriter beanWriter =
        new CsvBeanWriter(swStringWriter, CsvPreference.STANDARD_PREFERENCE)) {

      beanWriter.writeHeader(HEADER);
      beanWriter.writeComment(createComment(inputSearchConfig, res));

      List<AuditTrailSearchResult> auditEntries = res.getResults();
      log.info("Retrieved {} audit events ", auditEntries.size());
      for (AuditTrailSearchResult auditEntry : auditEntries) {
        String id = "n/a";
        String name = "n/a";
        String desc = "n/a";
        if (auditEntry.getData() != null
            && auditEntry.getData().getData() != null
            && auditEntry.getData().getData().getData() != null) {
          Map<String, Object> data = auditEntry.getData().getData().getData();
          id = data.getOrDefault("id", "n/a").toString();
          name = data.getOrDefault("name", "n/a").toString();
          String desc2 = auditEntry.getData().getDescription();
          if (!StringUtils.isBlank(desc2)) {
            desc = desc2;
          } else {
            desc = generateDescription(auditEntry, data);
          }
        }

        AuditTrailCSVConverterInput pojo =
            new AuditTrailCSVConverterInput(
                convertDateToISOFormat(auditEntry.getTimestamp(), TimeZone.getDefault()) + "",
                auditEntry.getEvent().getSubject(),
                auditEntry.getEvent().getAction().toString(),
                auditEntry.getEvent().getDomain().toString(),
                id,
                name,
                desc);
        beanWriter.write(pojo, HEADER, CELL_PROCESSORS);
      }

      beanWriter.flush();
    }
    return createCsvEntityResponse(swStringWriter.toString());
  }

  private String generateDescription(AuditTrailSearchResult auditEntry, Map<String, Object> data) {
    String rc = "n/a";
    if (AuditAction.MOVE.equals(auditEntry.getEvent().getAction())) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Map<String, Map<String, Object>> from = (Map) data.get("from");
      @SuppressWarnings({"unchecked", "rawtypes"})
      Map<String, Map<String, Object>> to = (Map) data.get("to");
      if (from != null && to != null) {
        rc = generateMoveDetails(from, to);
      }

    } else if (AuditAction.EXPORT.equals(auditEntry.getEvent().getAction())) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      List<Map<String, Object>> exportedList = (List) data.get("exported");
      if (!CollectionUtils.isEmpty(exportedList)) {
        rc = generateExportDetails(exportedList);
      }
    }

    return rc;
  }

  private String generateExportDetails(List<Map<String, Object>> exportedList) {
    String rc;
    rc = exportedList.size() + " item(s) exported: ";
    // join with ';' as is going to CSV
    String ids = exportedList.stream().map(m -> m.get("id").toString()).collect(joining(";"));
    rc = rc + StringUtils.abbreviate(ids, 100);
    return rc;
  }

  private String generateMoveDetails(
      Map<String, Map<String, Object>> from, Map<String, Map<String, Object>> to) {
    return String.format(
        "From %s (%s) to %s (%s)",
        from.get("data").get("name"),
        from.get("data").get("id"),
        to.get("data").get("name"),
        to.get("data").get("id"));
  }

  private ResponseEntity<String> createCsvEntityResponse(String csv) {
    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.setContentType(MediaType.parseMediaType("text/csv"));
    responseHeaders.add("Content-Disposition", ATTACHMENT_FILENAME_RSPACE_AUDIT_TRAIL_CSV);
    ResponseEntity<String> rc = new ResponseEntity<>(csv, responseHeaders, HttpStatus.OK);
    return rc;
  }

  private String createComment(
      AuditTrailUISearchConfig inputSearchConfig, ISearchResults<AuditTrailSearchResult> res) {
    String comment =
        String.format(
            "# audit trail download generated at %s.",
            DateUtil.convertDateToISOFormat(Instant.now().toEpochMilli(), TimeZone.getDefault()));
    if (res.getTotalHits().intValue() >= MAX_RESULTS_PER_CSV) {
      comment = comment + " " + MAX_RESULTS_EXCEEDED;
    }
    return comment;
  }
}
