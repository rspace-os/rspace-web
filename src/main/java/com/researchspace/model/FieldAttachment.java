package com.researchspace.model;

import com.researchspace.model.field.Field;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.FilterDef;
import org.hibernate.envers.Audited;

/**
 * Join table for Field/Media link associations. This needs to be an entity, rather than defined as
 * ManyToMAny since otherwise the revision history mechanism fails.
 *
 * <p>Also these associations can be marked deleted.
 */
@Entity
@Audited
@FilterDef(name = "fieldAttachmentNotDeleted", defaultCondition = "deleted = 0")
@Data
@EqualsAndHashCode(of = {"mediaFile", "field"})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldAttachment implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "field_attachment_gen")
  @TableGenerator(
      name = "field_attachment_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  @Setter(AccessLevel.PACKAGE)
  private Long id;

  @ManyToOne private Field field;

  @ManyToOne private EcatMediaFile mediaFile;

  /** Boolean flag for whether this attachment is marked as deleted/ */
  private boolean deleted = false;

  /** */
  private static final long serialVersionUID = 8912412332370429772L;

  public FieldAttachment(Field field, EcatMediaFile mediaFile) {
    super();
    this.field = field;
    this.mediaFile = mediaFile;
  }

  /**
   * @param deleted
   * @return this for method chaining
   */
  public FieldAttachment setDeleted(boolean deleted) {
    this.deleted = deleted;
    return this;
  }
}
