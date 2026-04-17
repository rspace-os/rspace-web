package com.researchspace.api.v1.model.stoichiometry;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDeductionResult {

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class IndividualResult {
    private Long linkId;
    private boolean success;
    private String errorMessage;

    public IndividualResult(Long linkId, boolean success) {
      this.linkId = linkId;
      this.success = success;
    }
  }

  private List<IndividualResult> results = new ArrayList<>();
  private Long stoichiometryId;
  private Long revisionNumber;

  public void addResult(IndividualResult result) {
    this.results.add(result);
  }
}
