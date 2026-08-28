package com.researchspace.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.Record;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/**
 * Represents a chemical structure. <br>
 * May also have references to internal database tables controlled by 3rd party libraries.
 *
 * <p>Invariants:
 *
 * <ul>
 *   <li>Only of chemId or reactionId can be non-null.
 *   <li>Dataimage should not be null.
 *   <li>Chemelements should not be null.
 * </ul>
 */
@Entity
@Audited
@Table
@Getter
@Setter
@EqualsAndHashCode(of = {"id", "parentId"})
@Builder
@AllArgsConstructor
public class RSChemElement implements Serializable, IFieldLinkableElement {

  /** */
  private static final long serialVersionUID = -8764723602823933240L;

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "rs_chem_element_gen")
  @TableGenerator(
      name = "rs_chem_element_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  private Long id;

  private Long parentId;

  private Long ecatChemFileId;

  /** A PNG representation of chemical structure /reaction */
  @Lob private byte[] dataImage;

  /** A representation of the chemical structure that can be used to load chemical editer */
  @Lob private String chemElements;

  /** Smiles string of chemical */
  @Lob private String smilesString;

  /**
   * References the cd_id column of jchem_structures table. IF this is set, reactionId and rgroupId
   * should be null
   */
  private Integer chemId;

  /**
   * References the cd_id column of a jchem_reactions table. If this is set, chemId and rgroupId
   * should be null
   */
  private Integer reactionId;

  /**
   * References the cd_id column of a jchem_rgroups table. If this is set, chemId and reactionId
   * should be null
   */
  private Integer rgroupId;

  @Column(columnDefinition = "TEXT")
  private String metadata;

  /** A String describing the format of the chem element */
  @Column(length = 32, nullable = false)
  @Enumerated(EnumType.STRING)
  @Builder.Default
  private ChemElementsFormat chemElementsFormat = ChemElementsFormat.MOL;

  /**
   * Unidirectional relation to a record holding this chemical element
   *
   * @return
   */
  @ManyToOne @JsonIgnore private Record record;

  @CreationTimestamp
  @Temporal(TemporalType.TIMESTAMP)
  @Column(nullable = false, updatable = false)
  private Date creationDate;

  // private setter for hibernate. this value should not be modifiable
  private void setCreationDate(Date creationDate) {
    this.creationDate = creationDate;
  }

  /** File containing preview of the chemical */
  @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  private FileProperty imageFileProperty;

  public RSChemElement() {}

  public RSChemElement(
      byte[] decodedBytes,
      String chem,
      ChemElementsFormat chemFormat,
      Long fieldId,
      Record record) {
    this.dataImage = decodedBytes;
    this.chemElements = chem;
    this.chemElementsFormat = chemFormat;
    setParentId(fieldId);
    setRecord(record);
  }

  /**
   * Generates a complete copy of this object's data fields.<br>
   * The copy has a null database id and is <b>not</b> connected to the original owning record.
   *
   * @return A new {@link RSChemElement} object.
   */
  public RSChemElement shallowCopy() {
    RSChemElement copy = new RSChemElement();

    copy.setDataImage(getDataImage());
    copy.setChemElements(getChemElements());
    copy.setSmilesString(getSmilesString());
    // We only set chemId, reactionId or rgroupId depending on which id is present in the original,
    // we only want one reference to the jchem table the chemical is stored in
    if (chemId != null) {
      copy.setChemId(chemId);
    } else if (reactionId != null) {
      copy.setReactionId(reactionId);
    } else {
      copy.setRgroupId(rgroupId);
    }
    copy.setChemElementsFormat(chemElementsFormat);
    copy.setEcatChemFileId(ecatChemFileId);

    return copy;
  }

  /** Sets chemId, sets reactionId and rgroupId to null */
  public void setChemId(Integer chemId) {
    this.chemId = chemId;
    this.reactionId = null;
    this.rgroupId = null;
  }

  /** Sets reactionId, sets chemId and rgroupId to null */
  public void setReactionId(Integer reactionId) {
    this.chemId = null;
    this.reactionId = reactionId;
    this.rgroupId = null;
  }

  /** Sets rgroupId, sets chemId and reactionId to null */
  public void setRgroupId(Integer rgroupId) {
    this.chemId = null;
    this.reactionId = null;
    this.rgroupId = rgroupId;
  }

  /**
   * Boolean test whether this ChemElement is stored as a reaction with substrates and products
   *
   * @return
   */
  @Transient
  @JsonIgnore
  public boolean isReaction() {
    return reactionId != null;
  }

  /**
   * Test for 'non-reaction'. Structures and multistep formulae are stored as molecules
   *
   * @return
   */
  @Transient
  @JsonIgnore
  public boolean isMoleculeOrMultiStepReaction() {
    return chemId != null;
  }

  /**
   * Get the jchem id, whichever id isn't null out of rgroupId, reactionId or chemId.
   *
   * @return the jchem id.
   */
  @Transient
  @JsonIgnore
  public Integer getJchemId() {
    if (chemId != null) {
      return chemId;
    } else if (reactionId != null) {
      return reactionId;
    } else {
      return rgroupId;
    }
  }

  @Override
  @Transient
  @JsonIgnore
  public GlobalIdentifier getOid() {
    return new GlobalIdentifier(GlobalIdPrefix.CH, getId());
  }
}
