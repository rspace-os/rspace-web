package com.researchspace.model.resourceaccess;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;

/** One direct role assignment owned by a {@link ResourceAccess} aggregate. */
@Entity
@Audited
public class ResourceRoleAssignment implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Getter
  @Setter
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "resourceAccess_id", nullable = false, updatable = false)
  @Getter
  private ResourceAccess resourceAccess;

  @Column(nullable = false, length = 64)
  @NotBlank
  @Getter
  private String roleKey;

  @Column(nullable = false, length = 191, updatable = false)
  @NotBlank
  @Getter
  private String granteeKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16, updatable = false)
  @Getter
  private ResourceGranteeKind granteeKind;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", updatable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  @Getter
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "group_id", updatable = false)
  @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
  @Getter
  private Group group;

  @Enumerated(EnumType.STRING)
  @Column(length = 32, updatable = false)
  @Getter
  private ResourceAudience audienceKey;

  @Column(nullable = false, length = 255)
  @NotBlank
  @Getter
  private String nameSnapshot;

  @Column(length = 255)
  @Getter
  private String detailSnapshot;

  protected ResourceRoleAssignment() {}

  public static ResourceRoleAssignment forUser(String roleKey, User user) {
    Objects.requireNonNull(user, "user");
    Long id = Objects.requireNonNull(user.getId(), "user.id");
    return new ResourceRoleAssignment(
        roleKey,
        ResourceGranteeKeys.user(id),
        ResourceGranteeKind.USER,
        user,
        null,
        null,
        user.getDisplayName(),
        user.getUsername());
  }

  public static ResourceRoleAssignment forGroup(String roleKey, Group group) {
    Objects.requireNonNull(group, "group");
    Long id = Objects.requireNonNull(group.getId(), "group.id");
    return new ResourceRoleAssignment(
        roleKey,
        ResourceGranteeKeys.group(id),
        ResourceGranteeKind.GROUP,
        null,
        group,
        null,
        group.getDisplayName(),
        group.getUniqueName());
  }

  public static ResourceRoleAssignment forAudience(String roleKey, ResourceAudience audience) {
    Objects.requireNonNull(audience, "audience");
    return new ResourceRoleAssignment(
        roleKey,
        ResourceGranteeKeys.audience(audience),
        ResourceGranteeKind.AUDIENCE,
        null,
        null,
        audience,
        audience.displayName(),
        null);
  }

  private ResourceRoleAssignment(
      String roleKey,
      String granteeKey,
      ResourceGranteeKind granteeKind,
      User user,
      Group group,
      ResourceAudience audienceKey,
      String nameSnapshot,
      String detailSnapshot) {
    setRoleKey(roleKey);
    this.granteeKey = granteeKey;
    this.granteeKind = granteeKind;
    this.user = user;
    this.group = group;
    this.audienceKey = audienceKey;
    this.nameSnapshot = requireNonBlank(nameSnapshot, "nameSnapshot");
    this.detailSnapshot = detailSnapshot;
  }

  public void setRoleKey(String roleKey) {
    this.roleKey = requireNonBlank(roleKey, "roleKey");
  }

  void attachTo(ResourceAccess access) {
    if (resourceAccess != null && resourceAccess != access) {
      throw new IllegalArgumentException("Assignment already belongs to another resource access");
    }
    resourceAccess = access;
  }

  void detachFrom(ResourceAccess access) {
    if (resourceAccess == access) {
      resourceAccess = null;
    }
  }

  /**
   * Allows nullable user/group FKs retained after deletion while rejecting mixed principal kinds.
   */
  @AssertTrue(message = "Resource role assignment principal shape is invalid")
  public boolean isPrincipalShapeValid() {
    if (granteeKind == null) {
      return false;
    }
    return switch (granteeKind) {
      case USER -> group == null && audienceKey == null;
      case GROUP -> user == null && audienceKey == null;
      case AUDIENCE -> user == null && group == null && audienceKey == ResourceAudience.ALL_USERS;
    };
  }

  private static String requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
