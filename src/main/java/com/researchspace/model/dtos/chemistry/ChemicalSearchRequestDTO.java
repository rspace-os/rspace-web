package com.researchspace.model.dtos.chemistry;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChemicalSearchRequestDTO {
  public String searchInput;
  public int pageNumber;
  public int pageSize;
  public String searchType;
}
