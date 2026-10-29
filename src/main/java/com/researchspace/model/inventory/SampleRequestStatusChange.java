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
import java.util.Date;
import lombok.EqualsAndHashCode;
import lombok.Setter;

/** One entry in a sample request's status history. */
@Entity
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SampleRequestStatusChange {

  public static final int REASON_COLUMN_LENGTH = 2000;

  private Long id;
  private SampleRequest sampleRequest;
  private String createdByUsername;
  private Long createdById;
  private SampleRequestStatus status;
  private String reason;
  private boolean autoClosed;
  private Sample transferredSample;
  private Date created = new Date();

  /** for hibernate */
  public SampleRequestStatusChange() {}

  public SampleRequestStatusChange(
      SampleRequest sampleRequest,
      User createdBy,
      SampleRequestStatus status,
      String reason,
      Sample transferredSample) {
    this.sampleRequest = sampleRequest;
    this.createdByUsername = createdBy.getUsername();
    this.createdById = createdBy.getId();
    this.status = status;
    this.reason = reason;
    this.transferredSample = transferredSample;
  }

  @Id
  @EqualsAndHashCode.Include
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "samplerequeststatuschange_gen")
  @TableGenerator(
      name = "samplerequeststatuschange_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  @ManyToOne(optional = false)
  @JoinColumn(nullable = false)
  public SampleRequest getSampleRequest() {
    return sampleRequest;
  }

  /**
   * The requester for the PENDING entry and for a cancellation; for a change caused by a transfer,
   * whoever made it (the owner, or a PI or Lab Admin of their group); otherwise the sample owner.
   * Kept for display, including after that user's deletion; {@link #getCreatedById()} identifies
   * them.
   */
  @Column(nullable = false, length = User.MAX_UNAME_LENGTH)
  public String getCreatedByUsername() {
    return createdByUsername;
  }

  /** The actor's user id, with no foreign key; a later account with their username is not them. */
  @Column(nullable = false)
  public Long getCreatedById() {
    return createdById;
  }

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public SampleRequestStatus getStatus() {
    return status;
  }

  /** The reason the owner gave for a rejection. */
  @Column(length = REASON_COLUMN_LENGTH)
  public String getReason() {
    return reason;
  }

  /** Whether a transfer closed the request as a side effect, not an action on the request. */
  public boolean isAutoClosed() {
    return autoClosed;
  }

  /** The sample this transition fulfilled the request with, if any. */
  @ManyToOne
  @JoinColumn
  public Sample getTransferredSample() {
    return transferredSample;
  }

  @Temporal(TemporalType.TIMESTAMP)
  @Column(nullable = false, updatable = false)
  public Date getCreated() {
    return created;
  }
}
