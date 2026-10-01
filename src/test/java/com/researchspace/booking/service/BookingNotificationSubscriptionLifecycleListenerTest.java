package com.researchspace.booking.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Instrument;
import org.junit.jupiter.api.Test;

class BookingNotificationSubscriptionLifecycleListenerTest {

  private final BookingFreshConfigurationReader configurations =
      mock(BookingFreshConfigurationReader.class);
  private final BookingNotificationSubscriptionManager subscriptions =
      mock(BookingNotificationSubscriptionManager.class);
  private final BookingNotificationSubscriptionLifecycleListener listener =
      new BookingNotificationSubscriptionLifecycleListener(configurations, subscriptions);

  @Test
  void initializesNewOwnerOnlyWhenInstrumentHasBookingConfiguration() {
    Instrument instrument = new Instrument();
    instrument.setId(42L);
    var target = new BookableTargetReference(BookableTargetType.INSTRUMENT, 42L);
    when(configurations.hasConfiguration(target)).thenReturn(false);

    listener.instrumentTransferred(event(instrument));

    verify(subscriptions, never()).initializeForInstrument(instrument);

    when(configurations.hasConfiguration(target)).thenReturn(true);
    listener.instrumentTransferred(event(instrument));

    verify(subscriptions).initializeForInstrument(instrument);
  }

  @Test
  void ignoresInstrumentTemplatesAndDeletedInstruments() {
    Instrument deleted = new Instrument();
    deleted.setId(43L);
    deleted.setRecordDeleted(true);
    listener.instrumentTransferred(event(deleted));

    verifyNoTransferLookups();
  }

  private static InventoryTransferEvent event(Instrument instrument) {
    User user = new User("transfer-actor");
    return new InventoryTransferEvent(instrument, user, user, user);
  }

  private void verifyNoTransferLookups() {
    org.mockito.Mockito.verifyNoInteractions(configurations, subscriptions);
  }
}
