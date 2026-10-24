package com.researchspace.webapp.controller;

import static com.researchspace.core.util.DateUtil.convertDateToISOFormat;
import static java.util.stream.Collectors.joining;

import com.researchspace.core.util.DateUtil;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.StringAbbreviationUtils;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.audit.search.AuditTrailSearchResult;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
  private static final Locale CSV_LOCALE = Locale.US;
  private static final Pattern PERMANENT_DELETE =
      Pattern.compile("(?:^|;)\\s*permanent=true(?:;|$)");

  @Autowired private MessageSourceUtils messages;

  String getMaxResultsExceededMessage() {
    return messages.getMessage(
        "export.audit.csv.maxResultsExceeded", new Object[] {MAX_RESULTS_PER_CSV}, CSV_LOCALE);
  }

  // CsvBeanWriter property names; these must match AuditTrailCSVConverterInput fields.
  private String[] HEADER = {"time", "user", "action", "type", "resource", "name", "description"};

  // All CSV text remains en-US because consumers treat it as a stable contract.
  private String[] getDisplayHeader() {
    return new String[] {
      messages.getMessageForLocale("common:profile.accountActivity.time", CSV_LOCALE),
      messages.getMessageForLocale("common:userDetails.roles.user", CSV_LOCALE),
      messages.getMessageForLocale("common:profile.accountActivity.action", CSV_LOCALE),
      messages.getMessageForLocale("export.audit.csv.headerType", CSV_LOCALE),
      messages.getMessageForLocale("export.audit.csv.headerResource", CSV_LOCALE),
      messages.getMessageForLocale("export.audit.csv.headerName", CSV_LOCALE),
      messages.getMessageForLocale("export.audit.csv.headerDescription", CSV_LOCALE)
    };
  }

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

      beanWriter.writeHeader(getDisplayHeader());
      beanWriter.writeComment(createComment(inputSearchConfig, res));

      List<AuditTrailSearchResult> auditEntries = res.getResults();
      log.info("Retrieved {} audit events ", auditEntries.size());
      for (AuditTrailSearchResult auditEntry : auditEntries) {
        String id = "n/a";
        String name = "n/a";
        String desc = "n/a";
        Map<String, Object> data = Map.of();
        if (auditEntry.getData() != null
            && auditEntry.getData().getData() != null
            && auditEntry.getData().getData().getData() != null) {
          data = auditEntry.getData().getData().getData();
          id = data.getOrDefault("id", "n/a").toString();
          name = data.getOrDefault("name", "n/a").toString();
          String desc2 = auditEntry.getData().getDescription();
          if (BookingAuditDetails.isBookingEvent(auditEntry.getEvent(), data)) {
            desc =
                StringUtils.defaultIfBlank(
                    BookingAuditDetails.combine(
                        BookingAuditDetails.format(data, messages, CSV_LOCALE), desc2),
                    desc);
          } else if (!StringUtils.isBlank(desc2)) {
            desc = desc2;
          } else {
            desc = generateDescription(auditEntry, data);
          }
        }

        AuditTrailCSVConverterInput pojo =
            new AuditTrailCSVConverterInput(
                convertDateToISOFormat(auditEntry.getTimestamp(), TimeZone.getDefault()) + "",
                auditEntry.getEvent().getSubject(),
                getDisplayAction(auditEntry, data),
                getDisplayType(auditEntry, data),
                id,
                name,
                desc);
        beanWriter.write(pojo, HEADER, CELL_PROCESSORS);
      }

      beanWriter.flush();
    }
    return createCsvEntityResponse(swStringWriter.toString());
  }

  private String getDisplayType(AuditTrailSearchResult auditEntry, Map<String, Object> data) {
    if (isBookingEvent(auditEntry, data)) {
      return messages.getMessageForLocale("export.audit.csv.bookingType", CSV_LOCALE);
    }
    return auditEntry.getEvent().getDomain().toString();
  }

  private String getDisplayAction(AuditTrailSearchResult auditEntry, Map<String, Object> data) {
    AuditAction action = auditEntry.getEvent().getAction();
    if (!isBookingEvent(auditEntry, data)) {
      return action.toString();
    }

    String description = auditEntry.getEvent().getDescription();
    if (AuditAction.DELETE.equals(action)
        && description != null
        && PERMANENT_DELETE.matcher(description).find()) {
      return messages.getMessageForLocale(
          "export.audit.csv.bookingActionPermanentlyDeleted", CSV_LOCALE);
    }

    Object state = data.get("state");
    if (AuditAction.WRITE.equals(action) && state != null && "CANCELLED".equals(state.toString())) {
      return messages.getMessageForLocale("export.audit.csv.bookingActionCancelled", CSV_LOCALE);
    }
    if (AuditAction.DELETE.equals(action) && state != null && "ARCHIVED".equals(state.toString())) {
      return messages.getMessageForLocale("export.audit.csv.bookingActionArchived", CSV_LOCALE);
    }
    return action.toString();
  }

  private boolean isBookingEvent(AuditTrailSearchResult auditEntry, Map<String, Object> data) {
    return BookingAuditDetails.isBookingEvent(auditEntry.getEvent(), data);
  }

  private String generateDescription(AuditTrailSearchResult auditEntry, Map<String, Object> data) {
    String rc = "n/a";
    if (AuditAction.MOVE.equals(auditEntry.getEvent().getAction())) {
      Map<String, Map<String, Object>> from = nestedObjectMap(data.get("from"));
      Map<String, Map<String, Object>> to = nestedObjectMap(data.get("to"));
      if (from != null && to != null) {
        rc = generateMoveDetails(from, to);
      }

    } else if (AuditAction.EXPORT.equals(auditEntry.getEvent().getAction())) {
      List<Map<String, Object>> exportedList = objectMapList(data.get("exported"));
      if (!CollectionUtils.isEmpty(exportedList)) {
        rc = generateExportDetails(exportedList);
      }
    }

    return rc;
  }

  private static Map<String, Map<String, Object>> nestedObjectMap(Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("Expected an object");
    }
    Map<String, Map<String, Object>> result = new LinkedHashMap<>();
    map.forEach((key, nested) -> result.put(String.class.cast(key), objectMap(nested)));
    return result;
  }

  private static List<Map<String, Object>> objectMapList(Object value) {
    if (value == null) {
      return null;
    }
    if (!(value instanceof List<?> list)) {
      throw new IllegalArgumentException("Expected an array");
    }
    return list.stream().map(AuditTrailSearchResultCsvGenerator::objectMap).toList();
  }

  private static Map<String, Object> objectMap(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("Expected an object");
    }
    Map<String, Object> result = new LinkedHashMap<>();
    map.forEach((key, nested) -> result.put(String.class.cast(key), nested));
    return result;
  }

  private String generateExportDetails(List<Map<String, Object>> exportedList) {
    // join with ';' as is going to CSV
    String ids = exportedList.stream().map(m -> m.get("id").toString()).collect(joining(";"));
    return messages.getMessage(
        "export.audit.csv.exportedItemCount",
        new Object[] {exportedList.size(), StringAbbreviationUtils.abbreviate(ids, 100)},
        CSV_LOCALE);
  }

  private String generateMoveDetails(
      Map<String, Map<String, Object>> from, Map<String, Map<String, Object>> to) {
    return messages.getMessage(
        "export.audit.csv.moveDetails",
        new Object[] {
          from.get("data").get("name"),
          from.get("data").get("id"),
          to.get("data").get("name"),
          to.get("data").get("id")
        },
        CSV_LOCALE);
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
        messages.getMessage(
            "export.audit.csv.commentGeneratedAt",
            new Object[] {
              DateUtil.convertDateToISOFormat(Instant.now().toEpochMilli(), TimeZone.getDefault())
            },
            CSV_LOCALE);
    if (res.getTotalHits().intValue() >= MAX_RESULTS_PER_CSV) {
      comment = comment + " " + getMaxResultsExceededMessage();
    }
    return comment;
  }
}
