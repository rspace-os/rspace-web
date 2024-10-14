package com.researchspace.webapp.integrations.fieldmark.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldmarkSampleLocation {

  private FieldmarkLocationGeometry geometry;
}
