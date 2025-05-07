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
public class ChemicalDataDTO {
  private Long ecatChemFileId;
  @NotNull private String chemElements;
  @NotNull private String chemElementsFormat;
  @NotNull private String imageBase64;
  @NotNull private long fieldId;
  private Long rsChemElementId;
}
