package com.researchspace.model.inventory;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Transient;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.envers.Audited;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

/** Represents RSpace Inventory Instrument Template */
@Entity
@DiscriminatorValue("InstrumentTemplate")
@Audited
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Indexed
public class InstrumentTemplate extends InstrumentEntity {

  private static final String TEMPLATE_MOVE_NOT_ALLOWED =
      "InstrumentTemplate cannot be moved or attached to containers";

  /**
   * Whether this template may be edited, deleted or transferred. Defaults to {@code true}; the only
   * writer of {@code false} is the seeder of a default (system) template, which must stay read-only
   * for everyone including its owning sysadmin. A duplicate of a template is a fresh instance that
   * keeps the default {@code true}, and an instrument created from a template has no such flag.
   * Lives on the concrete template (not {@link InstrumentEntity}) because editability is only
   * meaningful for templates; single-table inheritance keeps the column on the shared table.
   */
  private boolean editable = true;

  public InstrumentTemplate() {
    super();
  }

  protected InstrumentTemplate(Instrument originInstrument, User currentuser) {
    this();
    shallowCopyBasicFields(originInstrument, this);
    copy(originInstrument, this, this::defaultNameCopy, currentuser);
  }

  /**
   * Explicit getter (wins over the class-level Lombok {@code @Getter}) so the persisted column is
   * named {@code isEditable} rather than the JavaBeans default {@code editable}. Envers picks the
   * property up automatically via the class-level {@code @Audited}.
   */
  @Column(name = "isEditable", nullable = false)
  @ColumnDefault(
      "1") // sibling Instrument inserts omit this template-only column, so DB needs a default
  public boolean isEditable() {
    return editable;
  }

  public void setEditable(boolean editable) {
    this.editable = editable;
  }

  @Override
  public InstrumentEntity copyToTemplate(User currentUser) {
    throw new IllegalArgumentException(
        "Only an Instrument can be copied into an InstrumentTemplate");
  }

  @Override
  public InstrumentEntity copyFromTemplate(User currentUser) {
    Instrument copy = new Instrument(this, currentUser);
    copy.setInstrumentTemplate(this);
    copy.setTemplateLinkedVersion(this.getVersion());
    return copy;
  }

  @Transient
  @Override
  public boolean isTemplate() {
    return true;
  }

  @Transient
  @Override
  public GlobalIdPrefix getGlobalIdPrefix() {
    return GlobalIdPrefix.NT;
  }

  @Transient
  @Override
  public InventoryRecordType getType() {
    return InventoryRecordType.INSTRUMENT_TEMPLATE;
  }

  protected InstrumentEntity shallowCopy() {
    InstrumentEntity copy = new InstrumentTemplate();
    shallowCopyBasicFields(copy);
    return copy;
  }

  @Override
  public InstrumentEntity copy(User currentUser) {
    return super.copy(this::defaultNameCopy, currentUser);
  }

  @Override
  public void moveToNewParent(Container targetParent) {
    throw new IllegalArgumentException(TEMPLATE_MOVE_NOT_ALLOWED);
  }

  @Override
  public void moveToNewParentWithCoords(Container targetParent, Integer coordX, Integer coordY) {
    throw new IllegalArgumentException(TEMPLATE_MOVE_NOT_ALLOWED);
  }

  @Override
  public void moveToNewParentAndLocation(Container targetParent, ContainerLocation targetLocation) {
    throw new IllegalArgumentException(TEMPLATE_MOVE_NOT_ALLOWED);
  }

  @Override
  public void removeFromCurrentParent() {
    throw new IllegalArgumentException(TEMPLATE_MOVE_NOT_ALLOWED);
  }

  @Override
  public void setLastNonWorkbenchParent(Container lastNonWorkbenchParent) {
    if (this.getLastNonWorkbenchParent() != null || lastNonWorkbenchParent != null) {
      throw new IllegalArgumentException(TEMPLATE_MOVE_NOT_ALLOWED);
    }
    super.setLastNonWorkbenchParent(null);
  }
}
