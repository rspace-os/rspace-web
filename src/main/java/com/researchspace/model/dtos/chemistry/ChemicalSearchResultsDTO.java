package com.researchspace.model.dtos.chemistry;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ChemicalSearchResultsDTO {

  // chemicalHits is used to match chemicals by their RSChemElement.id
  private List<Long> chemicalHits;
  private int totalHits;

  public ChemicalSearchResultsDTO() {
    this.chemicalHits = new ArrayList<>();
    this.totalHits = 0;
  }
}
