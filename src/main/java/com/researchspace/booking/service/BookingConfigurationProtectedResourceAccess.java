package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.service.resourceaccess.ProtectedResourceAccess;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/** Adapts Booking configurations to the generic resource-access service. */
@Component
public class BookingConfigurationProtectedResourceAccess
    implements ProtectedResourceAccess<BookingConfiguration, Long> {

  private final BookingConfigurationDao configurationDao;

  public BookingConfigurationProtectedResourceAccess(
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurationDao) {
    this.configurationDao = configurationDao;
  }

  @Override
  public Optional<BookingConfiguration> find(Long id) {
    return configurationDao.getSafeNull(id);
  }

  @Override
  public Optional<BookingConfiguration> lock(Long id) {
    return configurationDao.lockById(id);
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
