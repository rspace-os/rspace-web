package com.researchspace.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.Record;
import jakarta.persistence.Cacheable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/**
 * Represents a piece of Math content in a text field. <br>
 * All associations are lazy.
 */
@Entity
@Audited
@Getter
@Setter
@EqualsAndHashCode(of = {"mathSvg", "latex"})
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
public class RSMath implements Serializable, IFieldLinkableElement {

  public static final int LATEX_COLUMN_SIZE = 2000;

  /** */
  private static final long serialVersionUID = -8764723602823933240L;

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "rs_math_gen")
  @TableGenerator(
      name = "rs_math_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  private Long id;

  /** This will be <code>null</code> if mathelement is in a snippet */
  @ManyToOne(fetch = FetchType.LAZY)
  private Field field;

  @Column(nullable = false, length = LATEX_COLUMN_SIZE)
  @Size(max = LATEX_COLUMN_SIZE)
  private String latex;

  /** This can be a structured document or a snippet. */
  @ManyToOne(fetch = FetchType.LAZY)
  private Record record;

  @ManyToOne(
      cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.DETACH},
      fetch = FetchType.LAZY)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private ImageBlob mathSvg;

  /** For tools/frameworks */
  public RSMath() {}

  /**
   * Public constructor
   *
   * @param svgStringAsBytes
   * @param latex
   * @param parentField
   */
  public RSMath(byte[] svgStringAsBytes, String latex, Field parentField) {
    this.mathSvg = new ImageBlob(svgStringAsBytes);
    this.latex = latex;
    this.field = parentField;
  }

  /**
   * Generates a complete copy of this object's data fields.<br>
   * The copy has a null database id and is <b>not</b> connected to the original owning field.
   *
   * @return A new copy
   */
  public RSMath shallowCopy() {
    RSMath copy = new RSMath();
    copy.setMathSvg(getMathSvg());
    copy.setLatex(getLatex());
    return copy;
  }

  @Override
  @Transient
  @JsonIgnore
  public GlobalIdentifier getOid() {
    return new GlobalIdentifier(GlobalIdPrefix.MA, getId());
  }

  @Override
  public String toString() {
    return "RSMath [id="
        + id
        + ", field="
        + ((field != null) ? field.getId() + "" : "null")
        + "record="
        + ((record != null) ? record.getId() + "" : "null")
        + "latex="
        + latex
        + ((getMathSvg() != null)
            ? StringUtils.abbreviate(
                new String(getMathSvg().getData(), StandardCharsets.UTF_8), 255)
            : "null");
  }

  /**
   * Gets SVG String representation
   *
   * @return
   */
  @Transient
  public String getMathSvgString() {
    return new String(getMathSvg().getData(), StandardCharsets.UTF_8);
  }
}
