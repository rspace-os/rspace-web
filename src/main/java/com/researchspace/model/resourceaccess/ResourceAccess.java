package com.researchspace.model.resourceaccess;

import com.researchspace.model.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/** Versioned aggregate that owns every direct role assignment for one protected resource. */
@Entity
@Audited
public class ResourceAccess implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Getter
  @Setter
  private Long id;

  @Column(nullable = false, length = 64, updatable = false)
  @NotBlank
  @Getter
  private String schemeKey;

  @Version
  @Column(nullable = false)
  @Getter
  @Setter
  private long version;

  @Column(nullable = false, updatable = false)
  private Date createdAt;

  @Column(nullable = false)
  private Date updatedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "createdBy_id", updatable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  @Getter
  @Setter
  private User createdBy;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "updatedBy_id")
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  @Getter
  @Setter
  private User updatedBy;

  @OneToMany(
      mappedBy = "resourceAccess",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("id")
  @Getter
  private final Set<ResourceRoleAssignment> assignments = new LinkedHashSet<>();

  protected ResourceAccess() {}

  public ResourceAccess(String schemeKey, User actor, Date timestamp) {
    if (schemeKey == null || schemeKey.isBlank()) {
      throw new IllegalArgumentException("Resource access scheme key must not be blank");
    }
    Objects.requireNonNull(timestamp, "timestamp");
    this.schemeKey = schemeKey;
    this.createdBy = actor;
    this.updatedBy = actor;
    this.createdAt = new Date(timestamp.getTime());
    this.updatedAt = new Date(timestamp.getTime());
  }

  /** Adds an assignment and establishes both sides of the aggregate relationship. */
  public void addAssignment(ResourceRoleAssignment assignment) {
    Objects.requireNonNull(assignment, "assignment");
    if (assignments.stream()
        .anyMatch(existing -> existing.getGranteeKey().equals(assignment.getGranteeKey()))) {
      throw new IllegalArgumentException(
          "Duplicate resource access grantee: " + assignment.getGranteeKey());
    }
    assignment.attachTo(this);
    assignments.add(assignment);
  }

  /** Removes an assignment from this aggregate. */
  public void removeAssignment(ResourceRoleAssignment assignment) {
    if (assignments.remove(assignment)) {
      assignment.detachFrom(this);
    }
  }

  /** Updates audit metadata for a semantic assignment change. */
  public void touch(User actor, Date timestamp) {
    Objects.requireNonNull(timestamp, "timestamp");
    updatedBy = actor;
    updatedAt = new Date(timestamp.getTime());
  }

  public Date getCreatedAt() {
    return new Date(createdAt.getTime());
  }

  public Date getUpdatedAt() {
    return new Date(updatedAt.getTime());
  }
}
