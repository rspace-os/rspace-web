package com.researchspace.model.inventory.field;

import com.researchspace.model.field.FieldType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.envers.Audited;

/**
 * A structured Sample template field of type 'link'. It holds a single optional {@link
 * InventoryLink} value (one target + relation + optional version pin) and a pipe-delimited whitelist
 * of permitted DataCite relation types. A null/empty whitelist means all relation types are allowed.
 *
 * <p>Distinct from {@link ExtraLinkField}, which is the record-level extra-field link; this is part
 * of the {@link InventoryEntityField} single-table hierarchy used to define template fields.
 */
@Entity
@Audited
@DiscriminatorValue("link")
@Setter
public class InventoryLinkField extends InventoryEntityField {

  private static final long serialVersionUID = 1L;

  private InventoryLink link;
  private String allowedRelationTypes;

  public InventoryLinkField() {
    super(FieldType.LINK, "");
  }

  /*
   * Annotations are placed on the getter (not the field) because the InventoryEntityField hierarchy
   * uses PROPERTY access (@Id is on getId()). With field-level annotations Hibernate ignores
   * @JoinColumn and falls back to the property name as the column name. Mirrors ExtraLinkField.
   */
  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "link_id", unique = true)
  public InventoryLink getLink() {
    return link;
  }

  /**
   * Pipe-delimited whitelist of permitted DataCite relation types; null or empty means all relation
   * types are allowed.
   */
  @Column(name = "allowed_relation_types", length = 1024)
  public String getAllowedRelationTypes() {
    return allowedRelationTypes;
  }

  /**
   * A mandatory link field requires a populated link target and relation type, not data-column
   * content. Both are checked with a blank (null/empty/whitespace) test: targetGlobalId because a
   * blank target is not a real reference, and relationType because it is mapped NOT NULL on {@link
   * InventoryLink}. Without these checks a blank value passes validation and then fails at flush
   * with a low-level DB constraint violation rather than a clear validation message.
   */
  @Override
  @Transient
  public boolean isValidValueForMandatoryField(String fieldData) {
    return link != null
        && StringUtils.isNotBlank(link.getTargetGlobalId())
        && StringUtils.isNotBlank(link.getRelationType());
  }

  /**
   * A link is legitimately left unfilled when existing samples are synced to a template version that
   * newly marks the link mandatory; it is populated by a later, separate link update (where {@link
   * #isValidValueForMandatoryField} does enforce a real target + relation type). So an empty
   * mandatory link must not abort the bulk "update samples to latest template version" run, unlike a
   * data-column field, which would still require a value.
   */
  @Override
  @Transient
  protected boolean requiresValueWhenBecomingMandatoryOnTemplateUpdate() {
    return false;
  }

  /** Also clears the link association, where this field type holds its value (not the data column). */
  @Override
  public void clearValue() {
    super.clearValue();
    setLink(null);
  }

  @Override
  public InventoryLinkField shallowCopy() {
    InventoryLinkField copy = new InventoryLinkField();
    copy.setAllowedRelationTypes(getAllowedRelationTypes());
    if (link != null) {
      // shallowCopy() deep-copies into a brand-new unsaved row (no id, timestamps set on persist),
      // so a sample instantiated from a template never shares the template field's link row. Both
      // clone paths delegate here so they cannot diverge (e.g. on the deleted flag).
      copy.setLink(link.shallowCopy());
    }
    copyFields(copy);
    return copy;
  }
}
