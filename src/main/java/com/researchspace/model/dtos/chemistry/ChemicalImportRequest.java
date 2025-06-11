package com.researchspace.model.dtos.chemistry;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChemicalImportRequest {

  @NotNull(message = "Search type is required")
  @Pattern(regexp = "cas|name|smiles", message = "Search type must be one of: cas, name, smiles")
  private String searchType;

  @NotNull(message = "Search term is required")
  private String searchTerm;
}
