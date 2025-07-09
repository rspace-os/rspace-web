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
public class ChemicalSearchRequest {

  @NotNull(message = "Search type is required")
  private ChemicalImportSearchType searchType;

  @NotNull(message = "Search term is required")
  private String searchTerm;
}
