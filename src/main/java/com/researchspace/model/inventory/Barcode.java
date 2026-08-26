package com.researchspace.model.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.io.Serializable;
import java.util.Date;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.KeywordField;

/** Basic model used to represent all barcodes added to inventory items */
@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {"id", "barcodeData", "format", "creationDate"})
@Audited
public class Barcode extends InventoryRecordConnectedEntity implements Serializable {

  private static final long serialVersionUID = 1015505407767174713L;

  private Long id;

  // indexing barcode content separately
  @KeywordField(name = "barcodeData")
  private String barcodeData;

  private String format;

  // indexing barcode description together with field data
  @FullTextField(name = "fieldData")
  private String description;

  private Date creationDate;
  private String createdBy;
  private boolean deleted;

  public Barcode(String barcodeData, String createdBy) {
    setBarcodeData(barcodeData);
    setCreatedBy(createdBy);
    setCreationDate(new Date());
  }

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "barcode_gen")
  @TableGenerator(
      name = "barcode_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  /**
   * Date of entity creation, i.e. date of uploading inventory file to RSpace. Returns a copy of the
   * stored date object for better encapsulation
   */
  @Column(nullable = false, updatable = false)
  @Temporal(TemporalType.TIMESTAMP)
  public Date getCreationDate() {
    return creationDate == null ? null : new Date(creationDate.getTime());
  }

  /**
   * Performs shallow copy of the attachment with copied reference to FileProperty. Does not set
   * InventoryRecord relation.
   */
  public Barcode shallowCopy() {
    Barcode copy = new Barcode(getBarcodeData(), getCreatedBy());
    copy.setDescription(getDescription());
    copy.setDeleted(isDeleted());
    return copy;
  }
}
