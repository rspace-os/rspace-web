package com.researchspace.model.inventory;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import jakarta.validation.ConstraintViolationException;

import org.hibernate.envers.Audited;

import com.researchspace.model.core.GlobalIdentifier;

import lombok.Getter;
import lombok.Setter;

/**
 * Abstract class inherited by entities connected to Inventory Records
 */
@MappedSuperclass
@Getter
@Setter
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Audited
public abstract class InventoryRecordConnectedEntity {

  /*
   * We want a foreign-key link to InventoryRecord, but as IR is abstract and not a db table
   * we need a field (column) for each concrete subclass i.e. Sample/SubSample/Container.
   */
  private SampleEntity sample;
  private SubSample subSample;
  private Container container;
  private InstrumentEntity instrumentEntity;

  /**
   * @return inventory record holding this field.
   */
  @Transient
  public InventoryRecord getInventoryRecord() {
    if (sample != null) {
      return sample;
    }
    if (subSample != null) {
      return subSample;
    }
    if (container != null) {
      return container;
    }
    if (instrumentEntity != null) {
      return instrumentEntity;
    }
    return null;
  }

  /**
   * Sets parent
   */
  public void setInventoryRecord(InventoryRecord invRec) {
    if (invRec instanceof SampleEntity) {
      sample = (SampleEntity) invRec;
    } else if (invRec instanceof SubSample) {
      subSample = (SubSample) invRec;
    } else if (invRec instanceof Container) {
      container = (Container) invRec;
    } else if (invRec instanceof InstrumentEntity) {
      instrumentEntity = (InstrumentEntity) invRec;
    }
  }

  /**
   * @return global id of a record to which this entity is connected to (or null if none)
   */
  @Transient
  public GlobalIdentifier getConnectedRecordOid() {
    InventoryRecord parent = getInventoryRecord();
    return parent != null ? parent.getOid() : null;
  }

  @Transient
  public String getConnectedRecordGlobalIdentifier() {
    GlobalIdentifier parentOid = getConnectedRecordOid();
    return parentOid != null ? parentOid.getIdString() : null;
  }

  @PrePersist
  @PreUpdate
  public void validateBeforeSave() {
    int parentCount = getNonInventoryRecordParentCount();
    parentCount = sample == null ? parentCount : ++parentCount;
    parentCount = subSample == null ? parentCount : ++parentCount;
    parentCount = container == null ? parentCount : ++parentCount;
    parentCount = instrumentEntity == null ? parentCount : ++parentCount;
    if (parentCount > 1) {
      throw new ConstraintViolationException(this.getClass().getSimpleName()
          + " cannot be connected to more than one inventory record", null);
    }
  }

  @ManyToOne(cascade = CascadeType.MERGE)
  public SampleEntity getSample() {
    return sample;
  }

  @ManyToOne(cascade = CascadeType.MERGE)
  public SubSample getSubSample() {
    return subSample;
  }

  @ManyToOne(cascade = CascadeType.MERGE)
  public Container getContainer() {
    return container;
  }

  @ManyToOne(cascade = CascadeType.MERGE)
  public InstrumentEntity getInstrumentEntity() {
    return instrumentEntity;
  }

  @Transient
  protected int getNonInventoryRecordParentCount() {
    return 0;
  }

}
