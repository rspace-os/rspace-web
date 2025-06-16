package com.researchspace.model.dtos.chemistry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChemicalImportSearchResult {
  private String name;
  private String previewImage;
  private String smiles;
  private String formula;
  private String cas;
  private String pubchemId;
  private String pubchemUrl;
}
