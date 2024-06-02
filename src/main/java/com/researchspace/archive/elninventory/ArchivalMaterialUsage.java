package com.researchspace.archive.elninventory;

import com.researchspace.model.elninventory.MaterialUsage;
import java.math.BigDecimal;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement
@NoArgsConstructor
@Getter
@Setter
@ToString
public class ArchivalMaterialUsage {

  @XmlElement(required = true)
  private Integer schemaVersion = 1;

  private Long invRecId;
  private String invRecType;
  private BigDecimal usageValue;
  private Integer usageUnitId;
  private String usagePlainText;

  public ArchivalMaterialUsage(MaterialUsage mu) {
    setInvRecId(mu.getInventoryRecord().getId());
    setInvRecType(mu.getInventoryRecord().getType().name());
    if (mu.getUsedQuantity() != null) {
      setUsageValue(mu.getUsedQuantity().getNumericValue());
      setUsageUnitId(mu.getUsedQuantity().getUnitId());
      setUsagePlainText(mu.getUsedQuantityPlainString());
    }
  }
}
