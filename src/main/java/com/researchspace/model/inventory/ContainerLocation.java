package com.researchspace.model.inventory;

import java.io.Serializable;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import jakarta.validation.ConstraintViolationException;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents location inside RSpace Inventory Container. Location can store SubSample, or another
 * Container.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(of = {"id", "coordX", "coordY"})
public class ContainerLocation implements Serializable {

  private static final long serialVersionUID = -8738857456396221527L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false, cascade = CascadeType.MERGE) // cannot be null
  private Container container;

  @OneToOne(mappedBy = "parentLocation", cascade = {CascadeType.PERSIST, CascadeType.MERGE,
      CascadeType.REFRESH})
  private Container storedContainer;

  @OneToOne(mappedBy = "parentLocation", cascade = {CascadeType.PERSIST, CascadeType.MERGE,
      CascadeType.REFRESH})
  private SubSample storedSubSample;

  @OneToOne(mappedBy = "parentLocation", cascade = {CascadeType.PERSIST, CascadeType.MERGE,
      CascadeType.REFRESH})
  private Instrument storedInstrument;

  private int coordX;
  private int coordY;

  public ContainerLocation(Container parentContainer) {
    container = parentContainer;
  }


  @Transient
  public InventoryRecord getStoredRecord() {
    if (storedContainer != null) {
      return storedContainer;
    } else if (storedSubSample != null) {
      return storedSubSample;
    } else if (storedInstrument != null) {
      return storedInstrument;
    }
    return null;
  }

  /**
   * Shouldn't be called directly, use
   * {@link Container#setRecordInLocation(MovableInventoryRecord, ContainerLocation)}
   *
   * @param record
   */
  void addStoredRecord(InventoryRecord record) {
    if (getStoredRecord() != null) {
      throw new IllegalStateException(
          "location already has a record: " + getStoredRecord().getGlobalIdentifier());
    }
    if (record.isContainer()) {
      setStoredContainer((Container) record);
    } else if (record.isSubSample()) {
      setStoredSubSample((SubSample) record);
    } else if (record.isInstrument()) {
      setStoredInstrument((Instrument) record);
    } else {
      throw new IllegalArgumentException(
          "can't put record: " + record.getGlobalIdentifier() + " into container location");
    }
  }

  /**
   * Shouldn't be called directly, use {@link Container#removeStoredRecord(ContainerLocation)}
   */
  void removeStoredRecord() {
    setStoredContainer(null);
    setStoredSubSample(null);
    setStoredInstrument(null);
  }

  @PrePersist
  @PreUpdate
  public void validateBeforeSave() {
    int parentCount = 0;
    parentCount = storedContainer == null ? parentCount : ++parentCount;
    parentCount = storedSubSample == null ? parentCount : ++parentCount;
    parentCount = storedInstrument == null ? parentCount : ++parentCount;
    if (parentCount > 1) {
      throw new ConstraintViolationException(
          "Location cannot store more than one item of the following: container, subsample, instrument",
          null);
    }
    if (coordY < 1 || coordX < 1) {
      throw new ConstraintViolationException(String.format(
          "Valid location coordinates start at (1,1), cannot be (%d,%d)", coordX, coordY), null);
    }
  }

}
