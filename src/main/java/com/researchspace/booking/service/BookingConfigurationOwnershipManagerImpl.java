package com.researchspace.booking.service;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Default transaction-participating Booking ownership coordinator. */
@Service
public class BookingConfigurationOwnershipManagerImpl
    implements BookingConfigurationOwnershipManager {

  private final BookingConfigurationDao configurationDao;
  private final BookingConfigurationProtectedResourceAccess protectedAccess;
  private final ResourceAccessManager accessManager;

  public BookingConfigurationOwnershipManagerImpl(
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurationDao,
      BookingConfigurationProtectedResourceAccess protectedAccess,
      ResourceAccessManager accessManager) {
    this.configurationDao = configurationDao;
    this.protectedAccess = protectedAccess;
    this.accessManager = accessManager;
  }

  @Override
  public void transferInstrumentOwnership(
      Long instrumentId, User outgoingOwner, User incomingOwner, User subject, User actor) {
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId);
    configurationDao
        .lockByTarget(target)
        .ifPresent(
            configuration -> {
              if (configuration.getState() != BookingConfigurationState.ACTIVE) {
                throw new BookingConfigurationLifecycleException();
              }
              accessManager.transferDirectOwnership(
                  protectedAccess,
                  configuration.getId(),
                  outgoingOwner,
                  incomingOwner,
                  subject,
                  actor);
            });
  }
}
