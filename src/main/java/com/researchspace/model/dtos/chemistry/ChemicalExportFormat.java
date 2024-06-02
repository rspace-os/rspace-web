package com.researchspace.model.dtos.chemistry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChemicalExportFormat {
  private ChemicalExportType exportType;
  private Integer width;
  private Integer height;
  private Double scale;
}
