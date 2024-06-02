package com.researchspace.model.dtos.chemistry;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChemConversionInputDto {
  @NotNull
  @Size(max = 2_000_000)
  private String structure;

  @Size(max = 20)
  private String inputFormat;

  @Size(max = 20)
  private String parameters;
}
