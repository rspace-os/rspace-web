package com.researchspace.booking.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemNotificationSubscription;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class BookingNotificationSubscriptionDaoTest extends SpringTransactionalTest {

  private final BookingNotificationSubscriptionDao subscriptions;

  @Autowired
  BookingNotificationSubscriptionDaoTest(BookingNotificationSubscriptionDao subscriptions) {
    this.subscriptions = subscriptions;
  }

  @Test
  void persistsExplicitOptOutWithUniqueIdentityAndOptimisticVersion() {
    User owner = createInitAndLoginAnyUser();
    var session = sessionFactory.getCurrentSession();
    Long instrumentId =
        createBasicInstrumentForUser(owner, "Notification subscription target").getId();
    Instrument instrument = session.get(Instrument.class, instrumentId);
    Date now = new Date();

    BookableItemNotificationSubscription saved =
        subscriptions.saveAndFlush(
            new BookableItemNotificationSubscription(owner, instrument, false, now));
    Long subscriptionId = saved.getId();
    assertFalse(saved.isEnabled());
    assertEquals(0L, saved.getVersion());

    session.clear();
    BookableItemNotificationSubscription reloaded =
        subscriptions.findByUserAndInstrument(owner.getId(), instrument.getId()).orElseThrow();
    assertEquals(subscriptionId, reloaded.getId());
    assertFalse(reloaded.isEnabled());
    reloaded.setEnabled(true);
    reloaded.setUpdatedAt(new Date(now.getTime() + 1));
    assertEquals(1L, subscriptions.saveAndFlush(reloaded).getVersion());

    assertThrows(
        DataIntegrityViolationException.class,
        () ->
            subscriptions.saveAndFlush(
                new BookableItemNotificationSubscription(
                    reloaded.getUser(), reloaded.getInstrument(), false, new Date())));
  }
}
