package com.researchspace.model.inventory;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.UniquelyIdentifiable;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.model.inventory.field.InventoryEntityField;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Transient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;

/**
 * Represents RSpace Inventory InstrumentEntity (that is Instrument or InstrumentTemplate)
 */
@Entity(name = "InstrumentEntity")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Audited
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@AuditTrailData(auditDomain = AuditDomain.INV_INSTRUMENT)
@Indexed
public abstract class InstrumentEntity extends MovableInventoryRecord implements Serializable,
    UniquelyIdentifiable {

  private static final long serialVersionUID = 186726698891360705L;

  private User owner;

  @IndexedEmbedded
  private List<InventoryEntityField> fields = new ArrayList<>();

  @IndexedEmbedded(name = "extraFields")
  private List<ExtraField> extraFields = new ArrayList<>();

  @IndexedEmbedded
  private List<Barcode> barcodes = new ArrayList<>();

  private List<DigitalObjectIdentifier> identifiers = new ArrayList<>();

  @IndexedEmbedded(name = "files")
  private List<InventoryFile> files = new ArrayList<>();

  private List<InventoryEntityField> activeFields;

  protected int currMaxColIndex = 0;

  public InstrumentEntity() {
  }


  @OneToMany(mappedBy = "instrumentEntity", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy(value = "columnIndex")
  public List<InventoryEntityField> getFields() {
    return fields;
  }

  /**
   * @return list of non-deleted fields belonging to this Instrument, sorted by columnIndex
   */
  @Transient
  public List<InventoryEntityField> getActiveFields() {
    if (activeFields == null) {
      activeFields = getFields().stream().filter(sf -> !sf.isDeleted())
          .sorted().collect(Collectors.toList());
    }
    return activeFields;
  }


  /**
   * Resets column index property for active fields, so they start from 1 and end with
   * currMaxColIndex.
   */
  public void refreshActiveFieldsAndColumnIndex() {
    currMaxColIndex = 0;
    activeFields = null;
    for (InventoryEntityField sf : getActiveFields()) {
      sf.setColumnIndex(++currMaxColIndex);
    }
  }

  public void deleteInstrumentField(
      InventoryEntityField toDelete, boolean deleteOnInstrumentUpdate) {
    Optional<InventoryEntityField> fieldToDeleteOpt = getFieldById(toDelete.getId());
    if (!fieldToDeleteOpt.isPresent()) {
      throw new IllegalArgumentException(
          "Trying to delete a field not belonging to current instrument");
    }
    InventoryEntityField fieldToDelete = fieldToDeleteOpt.get();
    fieldToDelete.setDeleted(true);
    fieldToDelete.setDeleteOnInstrumentUpdate(deleteOnInstrumentUpdate);
    refreshActiveFields();
  }

  public Optional<InventoryEntityField> getFieldById(Long id) {
    return getFields().stream().filter(sf -> id != null && id.equals(sf.getId())).findFirst();
  }

  protected List<InventoryEntityField> refreshActiveFields() {
    activeFields = null;
    return getActiveFields();
  }


  protected void setExtraFields(List<ExtraField> extraFields) {
    this.extraFields = extraFields;
    refreshActiveExtraFields();
  }


  protected void setBarcodes(List<Barcode> barcodes) {
    this.barcodes = barcodes;
    refreshActiveBarcodes();
  }


  @OneToMany(mappedBy = "instrumentEntity", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy(value = "id")
  public List<Barcode> getBarcodes() {
    return barcodes;
  }

  @ManyToOne
  @JoinColumn(nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  @IndexedEmbedded
  @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
  public User getOwner() {
    return owner;
  }

  @OneToMany(mappedBy = "instrumentEntity", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy(value = "id")
  public List<ExtraField> getExtraFields() {
    return extraFields;
  }

  @OneToMany(mappedBy = "instrumentEntity", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy(value = "id")
  public List<InventoryFile> getFiles() {
    return files;
  }

  @OneToMany(mappedBy = "instrumentEntity", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy(value = "id")
  @Override
  public List<DigitalObjectIdentifier> getIdentifiers() {
    return identifiers;
  }

  protected void setIdentifiers(List<DigitalObjectIdentifier> identifiers) {
    this.identifiers = identifiers;
    refreshActiveIdentifiers();
  }


  protected void setFiles(List<InventoryFile> files) {
    this.files = files;
    refreshActiveAttachedFiles();
  }

  @Transient
  public abstract GlobalIdPrefix getGlobalIdPrefix();


  /**
   * Convenience copy method to make a template from a intrument. This is the inverse operation of
   * {@code copyFromTemplate}
   *
   * @return a copy of the instrument, as a Template.
   * @throws IllegalArgumentException if this Instrument <em>is</em> a template
   */
  public abstract InstrumentEntity copyToTemplate(User currentUser);

  /**
   * Convenience copy method to make a normal instrument from a template. Validates that this
   * object <em>is</em> a template, also sets the template and its version into copied instrument.
   *
   * @return a copy of a template, a regular instrument.
   * @throws IllegalArgumentException if this Instrument is not a template
   */
  public abstract InstrumentEntity copyFromTemplate(User currentUser);


  /**
   * @param nameMapper A custom name-mapper to generate name for the new copy.
   * @return
   * @see InstrumentEntity#copy(User)
   */
  protected <T extends InstrumentEntity> T copy(Function<InstrumentEntity, String> nameMapper,
      User currentUser) {
    T instrumentCopy = this.shallowCopy();
    copy(this, instrumentCopy, nameMapper, currentUser);
    return instrumentCopy;
  }

  static void copy(InstrumentEntity origin, InstrumentEntity destination,
      Function<InstrumentEntity, String> nameMapper,
      User currentUser) {
    destination.setName(nameMapper.apply(origin));
    destination.setImageFileProperty(origin.getImageFileProperty());
    destination.setThumbnailFileProperty(origin.getThumbnailFileProperty());
    destination.setCreatedBy(currentUser.getUsername());
    destination.setModifiedBy(currentUser.getUsername());
    destination.setOwner(currentUser);

    for (InventoryEntityField originalField : origin.getFields()) {
      destination.copyAndAddInstrumentField(originalField);
    }
  }

  protected InventoryEntityField copyAndAddInstrumentField(InventoryEntityField originalField) {
    InventoryEntityField copiedField = originalField.shallowCopy();
    copiedField.setTemplateField(
        originalField.getInstrumentEntity().isTemplate() ? originalField
            : originalField.getTemplateField());
    verifyFieldNameAllowed(copiedField.getName());
    currMaxColIndex++;
    if (copiedField.getColumnIndex() == null) {
      copiedField.setColumnIndex(currMaxColIndex);
    }
    copiedField.setInventoryRecord(this);
    getFields().add(copiedField);
    refreshActiveFields();
    return copiedField;
  }

  @Transient
  public abstract boolean isTemplate();

}

