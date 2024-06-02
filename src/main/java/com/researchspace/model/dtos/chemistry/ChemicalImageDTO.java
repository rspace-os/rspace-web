package com.researchspace.model.dtos.chemistry;

import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChemicalImageDTO {
  @NotNull private Long chemId;
  @NotNull private String imageBase64;
}
