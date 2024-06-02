package com.researchspace.model.dtos.chemistry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// builder is helpful here when all constructor args would be strings
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConvertedStructureDto {
  private Long ecatChemFileId;
  private String structure;
  private String format;
  private String fileName;
  @Builder.Default private String contentUrl = "";
  private String errorMessage;
}
