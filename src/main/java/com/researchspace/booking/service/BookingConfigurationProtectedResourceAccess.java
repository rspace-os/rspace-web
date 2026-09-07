package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Adapts Booking configurations to the generic resource-access service. */
@Component
public class BookingConfigurationProtectedResourceAccess
    implements ProtectedResourceAccess<BookingConfiguration, Long> {

  private final BookingConfigurationDao configurationDao;
  private final FeatureFlagManager featureFlags;

  public BookingConfigurationProtectedResourceAccess(
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurationDao,
      FeatureFlagManager featureFlags) {
    this.configurationDao = configurationDao;
    this.featureFlags = featureFlags;
  }

  @Override
  public boolean featureEnabled(User subject) {
    return subject != null && featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject);
  }

  @Override
  public Optional<BookingConfiguration> find(Long id) {
    return configurationDao.getSafeNull(id);
  }

  @Override
  public Optional<BookingConfiguration> lock(Long id) {
    Optional<BookingConfiguration> configuration = configurationDao.lockById(id);
    configuration
        .filter(value -> value.getState() == BookingConfigurationState.ARCHIVED)
        .ifPresent(
            ignored -> {
              throw new BookingConfigurationLifecycleException();
            });
    return configuration;
  }

  @Override
  public ResourceAccess access(BookingConfiguration resource) {
    return resource.getResourceAccess();
  }

  @Override
  public String viewAccessCapability() {
    return BookingResourceRoleScheme.MANAGE_ASSIGNMENTS;
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
  public String viewAuditCapability() {
    return BookingResourceRoleScheme.VIEW_AUDIT;
  }
}
