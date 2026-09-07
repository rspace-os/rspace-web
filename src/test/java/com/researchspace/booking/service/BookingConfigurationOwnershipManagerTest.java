package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BookingConfigurationOwnershipManagerTest {

  private final BookingConfigurationDao configurationDao = mock(BookingConfigurationDao.class);
  private final BookingConfigurationProtectedResourceAccess protectedAccess =
      mock(BookingConfigurationProtectedResourceAccess.class);
  private final ResourceAccessManager accessManager = mock(ResourceAccessManager.class);
  private final BookingConfigurationOwnershipManager manager =
      new BookingConfigurationOwnershipManagerImpl(
          configurationDao, protectedAccess, accessManager);
  private final User outgoing = mock(User.class);
  private final User incoming = mock(User.class);
  private final User subject = mock(User.class);
  private final User actor = mock(User.class);

  @Test
  void unconfiguredInstrumentNeedsNoBookingMutation() {
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 17L);
    when(configurationDao.lockByTarget(target)).thenReturn(Optional.empty());

    manager.transferInstrumentOwnership(17L, outgoing, incoming, subject, actor);

    verify(accessManager, never())
        .transferDirectOwnership(protectedAccess, 9L, outgoing, incoming, subject, actor);
  }

  @Test
  void configuredInstrumentDelegatesToTheLockedGenericOwnershipCommand() {
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 17L);
    BookingConfiguration configuration = mock(BookingConfiguration.class);
    when(configuration.getId()).thenReturn(9L);
    when(configuration.getState()).thenReturn(BookingConfigurationState.ACTIVE);
    when(configurationDao.lockByTarget(target)).thenReturn(Optional.of(configuration));

    manager.transferInstrumentOwnership(17L, outgoing, incoming, subject, actor);

    verify(accessManager)
        .transferDirectOwnership(protectedAccess, 9L, outgoing, incoming, subject, actor);
  }

  @Test
  void archivedConfigurationAbortsTheCoordinatedTransfer() {
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 17L);
    BookingConfiguration configuration = mock(BookingConfiguration.class);
    when(configuration.getState()).thenReturn(BookingConfigurationState.ARCHIVED);
    when(configurationDao.lockByTarget(target)).thenReturn(Optional.of(configuration));

    assertThrows(
        BookingConfigurationLifecycleException.class,
        () -> manager.transferInstrumentOwnership(17L, outgoing, incoming, subject, actor));

    verify(accessManager, never())
        .transferDirectOwnership(protectedAccess, 9L, outgoing, incoming, subject, actor);
  }
}
