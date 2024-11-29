package com.researchspace.model.dtos.fieldmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.researchspace.fieldmark.model.utils.FieldmarkTypeExtractor;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldmarkRecordDTO {

  private String recordId;
  private String identifier;
  private String timestamp;

  // Map<fieldName, FieldValue>
  private Map<String, FieldmarkTypeExtractor> fields;

  public FieldmarkRecordDTO(String timestamp) {
    this.fields = new LinkedHashMap<>();
    this.timestamp = timestamp;
  }

  public FieldmarkTypeExtractor addField(String fieldName, FieldmarkTypeExtractor fieldValue) {
    return this.fields.put(fieldName, fieldValue);
  }

  public FieldmarkTypeExtractor getField(String fieldName) {
    return this.fields.get(fieldName);
  }
}
