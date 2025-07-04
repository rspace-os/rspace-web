package com.researchspace.model.dtos.fieldmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.researchspace.fieldmark.model.FieldmarkNotebookMetadata;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldmarkNotebookDTO {

  private static final DateTimeFormatter dateFormat =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private String name;
  private String id;
  private String timestamp;
  private Map<String, FieldmarkRecordDTO> records;
  private String doiIdentifierFieldName;

  private FieldmarkNotebookMetadata metadata;

  public FieldmarkNotebookDTO(String notebookId, String name, String doiIdentifierFieldName) {
    this.id = notebookId;
    this.name = name;
    this.records = new LinkedHashMap<>();
    this.timestamp = dateFormat.format(LocalDateTime.now(Clock.systemDefaultZone()));
    this.doiIdentifierFieldName = doiIdentifierFieldName;
  }

  public FieldmarkRecordDTO addRecord(FieldmarkRecordDTO record) {
    return this.records.put(record.getRecordId(), record);
  }

  public FieldmarkRecordDTO getRecord(String recordId) {
    return this.records.get(recordId);
  }

  public Map<String, FieldmarkRecordDTO> getRecords() {
    return Collections.unmodifiableMap(records);
  }
}
