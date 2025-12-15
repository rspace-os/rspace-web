package com.researchspace.model.dtos.chemistry;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChemicalImageDTO {
  private Long chemId;
  private String imageBase64;
}
