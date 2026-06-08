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
  @NotNull @NotEmpty private List<Long> linkIds;
  private boolean updateFieldHtml;
}
