package com.researchspace.booking.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class BookingCalendarSubscriptionDaoTest extends SpringTransactionalTest {

  @Autowired private BookingCalendarSubscriptionDao subscriptionDao;

  @Test
  void loadsCandidatesForAConfiguration() {
    User user = createInitAndLoginAnyUser();
    Long instrumentId = createBasicInstrumentForUser(user, "Calendar subscription target").getId();

    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setEnabled(true);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId));
    sessionFactory.getCurrentSession().persist(configuration);
    sessionFactory.getCurrentSession().flush();

    sessionFactory
        .getCurrentSession()
        .persist(
            new BookableItemCalendarSubscription(
                configuration, user, "a".repeat(64), "raw-token", new Date()));
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    List<BookingCalendarSubscriptionCandidate> subscriptions =
        subscriptionDao.findCandidatesByConfigurationId(configuration.getId());

    assertEquals(1, subscriptions.size());
    assertEquals(configuration.getId(), subscriptions.get(0).configurationId());
    assertEquals(user.getId(), subscriptions.get(0).userId());
  }
}
