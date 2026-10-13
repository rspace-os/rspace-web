package com.researchspace.archive.elninventory;

import com.researchspace.model.elninventory.MaterialUsage;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import java.math.BigDecimal;
import java.util.List;
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
  private Integer schemaVersion = 2;

  private Long invRecId;
  private String invRecType;
  private String globalId;
  private String igsn;
  private BigDecimal usageValue;
  private Integer usageUnitId;
  private String usagePlainText;

  public ArchivalMaterialUsage(MaterialUsage mu) {
    setInvRecId(mu.getInventoryRecord().getId());
    setInvRecType(mu.getInventoryRecord().getType().name());
    setGlobalId(mu.getInventoryRecord().getGlobalIdentifier());
    List<DigitalObjectIdentifier> identifiers = mu.getInventoryRecord().getActiveIdentifiers();
    if (!identifiers.isEmpty()) {
      setIgsn(identifiers.get(0).getPublicUrl());
    }
    if (mu.getUsedQuantity() != null) {
      setUsageValue(mu.getUsedQuantity().getNumericValue());
      setUsageUnitId(mu.getUsedQuantity().getUnitId());
      setUsagePlainText(mu.getUsedQuantityPlainString());
    }
  }
}
