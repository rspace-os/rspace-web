package com.researchspace.model.inventory;

import com.researchspace.model.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import java.io.Serializable;
import java.util.Date;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;

@Entity
@Audited
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = {"id", "content", "creationDateMillis"})
public class SubSampleNote implements Serializable {

  private static final long serialVersionUID = 284160459745885323L;

  private Long id;

  private Long creationDateMillis;
  private User createdBy;

  private String content;

  private SubSample subSample;

  public SubSampleNote(String content, User creator) {
    setContent(content);
    setCreationDateMillis(new Date().getTime());
    setCreatedBy(creator);
    setSubSample(subSample);
  }

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "sub_sample_note_gen")
  @TableGenerator(
      name = "sub_sample_note_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  @ManyToOne
  @JoinColumn(nullable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  public User getCreatedBy() {
    return createdBy;
  }

  @ManyToOne(cascade = CascadeType.MERGE)
  @JoinColumn(nullable = false)
  private SubSample getSubSample() {
    return subSample;
  }

  /*
   * Copies properties (except id, not related properties)
   */
  SubSampleNote shallowCopy() {
    SubSampleNote copy = new SubSampleNote(content, null);
    copy.setCreationDateMillis(creationDateMillis);
    return copy;
  }

  @Column(nullable = false)
  public Long getCreationDateMillis() {
    return creationDateMillis;
  }

  private void setCreationDateMillis(Long millis) {
    this.creationDateMillis = millis;
  }

  /**
   * Content length for subsample note. @FullTextField is on the getter (not the field) because this
   * entity uses property access (@Id on getter). In Hibernate Search 7, field-level annotations are
   * ignored for property-access entities.
   */
  @Column(length = 2000)
  @FullTextField(name = "fieldData")
  public String getContent() {
    return content;
  }
}
