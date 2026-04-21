package com.researchspace.api.v1.model.stoichiometry;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDeductionRequest {
  @NotNull private Long stoichiometryId;
  @NotNull @NotEmpty private List<Long> linkIds;
}
