package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.researchspace.model.units.Quantifiable;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Information about quantity. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiQuantityInfo implements Quantifiable {

  @JsonProperty("numericValue")
  private BigDecimal numericValue;

  @JsonProperty("unitId")
  private Integer unitId;

  public ApiQuantityInfo(Quantifiable quantifiable) {
    unitId = quantifiable.getUnitId();
    numericValue = quantifiable.getNumericValue();
  }

  public ApiQuantityInfo(BigDecimal value, RSUnitDef unit) {
    this(value, unit.getId());
  }

  public QuantityInfo toQuantityInfo() {
    return new QuantityInfo(numericValue, unitId);
  }
}
