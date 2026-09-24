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
 * One entry in a sample request's status history, including the PENDING entry written when the
 * request is raised. A table rather than the audit log, which is log-based, so cannot feed a
 * details view. Modelled on GroupMembershipEvent.
 */
@Entity
@Getter
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SampleRequestStatusChange implements Serializable {

  public static final int REASON_COLUMN_LENGTH = 2000;

  private static final long serialVersionUID = 1L;

  private Long id;
  private SampleRequest sampleRequest;
  private String createdByUsername;
  private SampleRequestStatus status;
  private String reason;
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
   * The requester for the PENDING entry and for a cancellation, otherwise the sample owner. Stored
   * as a username so the history survives that user's deletion and cannot block it.
   */
  @Column(nullable = false, length = User.MAX_UNAME_LENGTH)
  public String getCreatedByUsername() {
    return createdByUsername;
  }

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  public SampleRequestStatus getStatus() {
    return status;
  }

  /** Required for a rejection, absent otherwise. */
  @Column(length = REASON_COLUMN_LENGTH)
  public String getReason() {
    return reason;
  }

  /**
   * The sample this transition fulfilled the request with, if any: transferred or newly created.
   */
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
