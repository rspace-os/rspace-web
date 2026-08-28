package com.researchspace.model.elninventory;

import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.InventoryRecordConnectedEntity;
import com.researchspace.model.units.QuantityInfo;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

/** Represents usage of a particular inventory material within ELN experiment. */
@Entity
@Audited
@Setter
@EqualsAndHashCode(
    of = {"id"},
    callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MaterialUsage extends InventoryRecordConnectedEntity implements Serializable {

  private static final long serialVersionUID = 4330059378109785385L;

  private Long id;

  private QuantityInfo usedQuantity;

  private ListOfMaterials parentLom;

  public MaterialUsage(
      ListOfMaterials parentLom, InventoryRecord invRec, QuantityInfo usedQuantity) {
    setParentLom(parentLom);
    setInventoryRecord(invRec);
    setUsedQuantity(usedQuantity);
  }

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "material_usage_gen")
  @TableGenerator(
      name = "material_usage_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  @Embedded
  @AttributeOverrides({
    @AttributeOverride(name = "unitId", column = @Column(name = "quantityUnitId")),
    @AttributeOverride(
        name = "numericValue",
        column = @Column(name = "quantityNumericValue", precision = 19, scale = 3))
  })
  public QuantityInfo getUsedQuantity() {
    return usedQuantity;
  }

  public void setUsedQuantity(QuantityInfo newQuantity) {
    if (newQuantity != null && newQuantity.getUnitId() != null) {
      if (BigDecimal.ZERO.compareTo(newQuantity.getNumericValue()) > 0) {
        throw new IllegalArgumentException(
            "Trying to set negative record quantity: " + newQuantity.getNumericValue());
      }
    }
    this.usedQuantity = newQuantity;
  }

  @ManyToOne(cascade = CascadeType.MERGE)
  @JoinColumn(nullable = false)
  private ListOfMaterials getParentLom() {
    return parentLom;
  }

  @Transient
  public String getUsedQuantityPlainString() {
    return getUsedQuantity() == null ? "" : getUsedQuantity().toPlainString();
  }
}
