package com.researchspace.api.v1.model.stoichiometry;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDeductionRequest {
  @NotNull private Long stoichiometryId;

  // Element-level @NotNull: a null id would escape the manager's per-link try/catch inside its
  // lock-ordering pre-pass (Map.entry rejects a null key) and turn a malformed public request into
  // a 500 instead of a 400 (Copilot review, PR #1090).
  @NotNull @NotEmpty private List<@NotNull Long> linkIds;

  private boolean updateFieldHtml;
}
