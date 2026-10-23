package com.researchspace.model.inventory;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.AuditTrailData;
import com.researchspace.model.audittrail.AuditTrailIdentifier;
import com.researchspace.model.audittrail.AuditTrailProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Setter;

/**
 * A request from one user for material from another user's sample. The sample's current owner
 * decides it; originalOwner records who was asked. A transfer of ownership closes any request still
 * open: fulfilled if the new owner raised it, rejected otherwise.
 */
@Entity
@AuditTrailData(auditDomain = AuditDomain.REQUEST)
@Setter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class SampleRequest implements Serializable {

  /** Length of the note column. Longer notes are rejected rather than truncated. */
  public static final int NOTE_COLUMN_LENGTH = 2000;

  private static final long serialVersionUID = 1L;

  private Long id;
  private Sample sample;
  private String requesterUsername;
  private Long requesterId;
  private String originalOwner;
  private Long originalOwnerId;
  private SampleRequestStatus status = SampleRequestStatus.PENDING;
  private String note;
  private Date created = new Date();
  private List<SampleRequestStatusChange> statusChanges = new ArrayList<>();

  /** for hibernate and pagination criteria */
  public SampleRequest() {}

  public SampleRequest(Sample sample, User requester, String note) {
    this.sample = sample;
    this.requesterUsername = requester.getUsername();
    this.requesterId = requester.getId();
    this.originalOwner = sample.getOwner().getUsername();
    this.originalOwnerId = sample.getOwner().getId();
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
  @AuditTrailProperty(name = "requestId")
  public Long getId() {
    return id;
  }

  /** The sample the material is being asked for. Its current owner is the approver. */
  @ManyToOne(optional = false)
  @JoinColumn(nullable = false)
  public Sample getSample() {
    return sample;
  }

  /**
   * The requester's username, for display, including after their deletion; {@link
   * #getRequesterId()} identifies them.
   */
  @Column(nullable = false, length = User.MAX_UNAME_LENGTH)
  @AuditTrailProperty(name = "requester")
  public String getRequesterUsername() {
    return requesterUsername;
  }

  /**
   * The requester's user id, with no foreign key so it cannot block their deletion. Unlike the
   * username, an id is never reused, so a later account with the same username is not them.
   */
  @Column(nullable = false)
  public Long getRequesterId() {
    return requesterId;
  }

  /** Username of who owned the sample when the request was raised. Set at creation only. */
  @Column(nullable = false, length = User.MAX_UNAME_LENGTH)
  public String getOriginalOwner() {
    return originalOwner;
  }

  /** The original owner's user id, kept like {@link #getRequesterId()}. */
  @Column(nullable = false)
  public Long getOriginalOwnerId() {
    return originalOwnerId;
  }

  /** Whether the user raised this request: same account, not merely the same username. */
  public boolean isRequester(User user) {
    return requesterId.equals(user.getId());
  }

  /** Whether the user owned the sample when this request was raised. */
  public boolean isOriginalOwner(User user) {
    return originalOwnerId.equals(user.getId());
  }

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  @AuditTrailProperty(name = "status")
  public SampleRequestStatus getStatus() {
    return status;
  }

  /**
   * The audit entry's id, which the auditing page renders as a global id link. A request has none
   * of its own, so the sample's is used and a search by that id finds the request too.
   */
  @Transient
  @AuditTrailIdentifier
  public String getRequestedSampleGlobalId() {
    return sample != null ? sample.getGlobalIdentifier() : null;
  }

  /** Free text: what the requester needs and why. */
  @Column(nullable = false, length = NOTE_COLUMN_LENGTH)
  public String getNote() {
    return note;
  }

  @Temporal(TemporalType.TIMESTAMP)
  @Column(nullable = false, updatable = false)
  public Date getCreated() {
    return created;
  }

  /** Full status history, oldest first, starting with the PENDING entry written at creation. */
  @OneToMany(mappedBy = "sampleRequest", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("created, id")
  public List<SampleRequestStatusChange> getStatusChanges() {
    return statusChanges;
  }

  public SampleRequestStatusChange recordStatus(
      User author, SampleRequestStatus newStatus, String reason) {
    return recordStatus(author, newStatus, reason, null);
  }

  /**
   * Records a new current status and appends it to the history, including the sample this
   * transition fulfilled the request with, if any.
   */
  public SampleRequestStatusChange recordStatus(
      User author, SampleRequestStatus newStatus, String reason, Sample transferredSample) {
    SampleRequestStatusChange change =
        new SampleRequestStatusChange(this, author, newStatus, reason, transferredSample);
    statusChanges.add(change);
    setStatus(newStatus);
    return change;
  }
}
