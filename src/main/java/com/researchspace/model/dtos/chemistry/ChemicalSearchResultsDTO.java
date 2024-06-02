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
  private List<Integer> reactionHits;
  private List<Integer> structureHits;
  private List<Integer> rgroupHits;
  private int totalHits;

  public ChemicalSearchResultsDTO() {
    this.reactionHits = new ArrayList<>();
    this.structureHits = new ArrayList<>();
    this.rgroupHits = new ArrayList<>();
    this.totalHits = 0;
  }
}
