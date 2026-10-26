package com.researchspace.model.booking;

import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceGranteeKeys;
import com.researchspace.model.resourceaccess.ResourceGranteeKind;
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

/** One user or group selected to receive Booker on newly created configurations. */
@Entity
@Audited
public class BookingDefaultAccessGrantee implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Getter
  @Setter
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "defaults_id", nullable = false, updatable = false)
  @Getter
  private BookingConfigurationDefaults defaults;

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

  @Column(nullable = false, length = 255)
  @NotBlank
  @Getter
  private String nameSnapshot;

  @Column(length = 255)
  @Getter
  private String detailSnapshot;

  protected BookingDefaultAccessGrantee() {}

  public static BookingDefaultAccessGrantee forUser(User user) {
    Objects.requireNonNull(user, "user");
    Long id = Objects.requireNonNull(user.getId(), "user.id");
    return new BookingDefaultAccessGrantee(
        ResourceGranteeKeys.user(id),
        ResourceGranteeKind.USER,
        user,
        null,
        user.getDisplayName(),
        user.getUsername());
  }

  public static BookingDefaultAccessGrantee forGroup(Group group) {
    Objects.requireNonNull(group, "group");
    Long id = Objects.requireNonNull(group.getId(), "group.id");
    return new BookingDefaultAccessGrantee(
        ResourceGranteeKeys.group(id),
        ResourceGranteeKind.GROUP,
        null,
        group,
        group.getDisplayName(),
        group.getUniqueName());
  }

  private BookingDefaultAccessGrantee(
      String granteeKey,
      ResourceGranteeKind granteeKind,
      User user,
      Group group,
      String nameSnapshot,
      String detailSnapshot) {
    this.granteeKey = granteeKey;
    this.granteeKind = granteeKind;
    this.user = user;
    this.group = group;
    if (nameSnapshot == null || nameSnapshot.isBlank()) {
      throw new IllegalArgumentException("nameSnapshot must not be blank");
    }
    this.nameSnapshot = nameSnapshot;
    this.detailSnapshot = detailSnapshot;
  }

  void attachTo(BookingConfigurationDefaults defaults) {
    if (this.defaults != null && this.defaults != defaults) {
      throw new IllegalArgumentException("Grantee already belongs to another defaults aggregate");
    }
    this.defaults = defaults;
  }

  void detachFrom(BookingConfigurationDefaults defaults) {
    if (this.defaults == defaults) {
      this.defaults = null;
    }
  }

  /** Allows a deleted-principal snapshot while rejecting mixed user/group references. */
  @AssertTrue(message = "Booking default access grantee principal shape is invalid")
  public boolean isPrincipalShapeValid() {
    if (granteeKind == ResourceGranteeKind.USER) {
      return group == null;
    }
    if (granteeKind == ResourceGranteeKind.GROUP) {
      return user == null;
    }
    return false;
  }
}
