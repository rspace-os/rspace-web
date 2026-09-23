package com.researchspace.booking.service;

import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Instrument;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Initializes a new owner's preference when a currently bookable instrument changes owners. */
@Component
public class BookingNotificationSubscriptionLifecycleListener {

  private final BookingFreshConfigurationReader configurations;
  private final BookingNotificationSubscriptionManager subscriptions;

  public BookingNotificationSubscriptionLifecycleListener(
      BookingFreshConfigurationReader configurations,
      BookingNotificationSubscriptionManager subscriptions) {
    this.configurations = configurations;
    this.subscriptions = subscriptions;
  }

  /**
   * Handles the inventory transfer synchronously inside its transaction, where the instrument row
   * is already locked. Non-bookable instruments are initialized later if a booking configuration is
   * created for them.
   */
  @EventListener
  public void instrumentTransferred(InventoryTransferEvent event) {
    if (!(event.getTransferredItem() instanceof Instrument instrument) || instrument.isDeleted()) {
      return;
    }
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrument.getId());
    // The transfer holds the instrument lock, which serializes this check with configuration
    // creation. Read through a fresh READ_COMMITTED transaction to avoid the transfer's older
    // REPEATABLE_READ snapshot, then initialize in the still-locked transfer transaction.
    if (configurations.hasConfiguration(target)) {
      subscriptions.initializeForInstrument(instrument);
    }
  }
}
