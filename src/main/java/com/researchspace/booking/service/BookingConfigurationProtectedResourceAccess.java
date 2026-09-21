package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceGranteeKeys;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessCallerCapabilities;
import com.researchspace.service.resourceaccess.ResourceAccessCallerDocument;
import com.researchspace.service.resourceaccess.ResourceAccessDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Adapts Booking configurations to the generic resource-access service. */
@Component
public class BookingConfigurationProtectedResourceAccess
    implements ProtectedResourceAccess<BookingConfiguration, Long> {

  private final BookingConfigurationDao configurationDao;
  private final FeatureFlagManager featureFlags;
  private final BookingItemPermissions permissions;

  public BookingConfigurationProtectedResourceAccess(
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurationDao,
      FeatureFlagManager featureFlags,
      BookingItemPermissions permissions) {
    this.configurationDao = configurationDao;
    this.featureFlags = featureFlags;
    this.permissions = permissions;
  }

  @Override
  public boolean featureEnabled(User subject) {
    return subject != null && featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject);
  }

  @Override
  public Optional<BookingConfiguration> find(Long id) {
    return id == null ? Optional.empty() : configurationDao.getSafeNull(id);
  }

  @Override
  public Optional<BookingConfiguration> lock(Long id) {
    return id == null ? Optional.empty() : configurationDao.lockById(id);
  }

  @Override
  public ResourceAccess access(BookingConfiguration resource) {
    return null;
  }

  @Override
  public boolean isInherited(BookingConfiguration resource) {
    return true;
  }

  @Override
  public ResolvedResourceAccess resolveInherited(BookingConfiguration resource, User subject) {
    return permissions.resolve(resource, subject);
  }

  @Override
  public ResolvedResourceAccess resolveInheritedForMutation(
      BookingConfiguration resource, User subject) {
    return permissions.resolveForMutation(resource, subject);
  }

  @Override
  public ResourceAccessDocument inheritedDocument(
      BookingConfiguration resource, User subject, ResolvedResourceAccess resolved) {
    ResourceAccessCallerDocument caller =
        new ResourceAccessCallerDocument(
            resolved.effectiveRole(),
            resolved.roleSources(),
            new ResourceAccessCallerCapabilities(false, false, false),
            subject == null || subject.getId() == null
                ? null
                : ResourceGranteeKeys.user(subject.getId()));
    return new ResourceAccessDocument(
        BookingResourceRoleScheme.SCHEME_KEY, 0L, List.of(), caller, true);
  }

  @Override
  public String viewAccessCapability() {
    return BookingResourceRoleScheme.READ_RESOURCE;
  }

  @Override
  public String manageAssignmentsCapability() {
    return BookingResourceRoleScheme.MANAGE_ASSIGNMENTS;
  }

  @Override
  public String manageOwnersCapability() {
    return BookingResourceRoleScheme.MANAGE_OWNERS;
  }

  @Override
  public void beforeAccessMutation(BookingConfiguration configuration) {
    if (configuration.getState() == BookingConfigurationState.ARCHIVED) {
      throw new BookingConfigurationLifecycleException();
    }
  }

  @Override
  public String viewAuditCapability() {
    return BookingResourceRoleScheme.VIEW_AUDIT;
  }
}
