package com.researchspace.model.inventory;

import com.researchspace.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.io.Serializable;
import java.util.Date;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * A request from one user for material from another user's sample. The approver is not stored: it
 * is always the sample's current owner, so a transfer of ownership moves any open request with it.
 */
@Entity
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SampleRequest implements Serializable {

  /** Length of the note column. Longer notes are rejected rather than truncated. */
  public static final int NOTE_COLUMN_LENGTH = 2000;

  private static final long serialVersionUID = 1L;

  private Long id;
  private Sample sample;
  private User requester;
  private SampleRequestStatus status = SampleRequestStatus.PENDING;
  private String note;
  private Date created = new Date();

  /** for hibernate and pagination criteria */
  public SampleRequest() {}

  public SampleRequest(Sample sample, User requester, String note) {
    this.sample = sample;
    this.requester = requester;
    this.note = note;
  }

  @Id
  @EqualsAndHashCode.Include
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "samplerequest_gen")
  @TableGenerator(
      name = "samplerequest_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  /** The sample the material is being asked for. Its current owner is the approver. */
  @ManyToOne(optional = false)
  @JoinColumn(nullable = false)
  public Sample getSample() {
    return sample;
  }

  @ManyToOne(optional = false)
  @JoinColumn(nullable = false)
  public User getRequester() {
    return requester;
  }

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public SampleRequestStatus getStatus() {
    return status;
  }

  /** Free text: what the requester needs and why. Converted into an operation by the fulfiller. */
  @Column(nullable = false, length = NOTE_COLUMN_LENGTH)
  public String getNote() {
    return note;
  }

  @Temporal(TemporalType.TIMESTAMP)
  @Column(nullable = false, updatable = false)
  public Date getCreated() {
    return created;
  }
}
