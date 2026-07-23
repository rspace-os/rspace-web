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

  @NotNull(message = "{chem.validation.searchTypeRequired}")
  private ChemicalImportSearchType searchType;

  @NotNull(message = "{chem.validation.searchTermRequired}")
  private String searchTerm;
}
