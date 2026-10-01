package com.researchspace.booking.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.UserBookingCalendarSubscriptionDao;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.BookingInstrumentQueryDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.testutils.TestFactory;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class BookingCalendarManagerTest {

  static final long CONFIGURATION_ID = 11L;
  static final long BOOKING_ID = 42L;
  static final String RAW_TOKEN = "A".repeat(43);
  static final URI SERVER_BASE_URI = URI.create("https://rspace.example/context");
  static final String FEED_URL = "https://rspace.example/context/public/booking/calendars/feed.ics";

  static BookingCalendarManager createManager(
      BookingCalendarSubscriptionDao subscriptionDao,
      UserBookingCalendarSubscriptionDao userSubscriptionDao,
      BookingConfigurationDao configurationDao,
      TimeSlotBookingManager bookingManager,
      BookingInstrumentQueryDao instrumentDao,
      FeatureFlagManager featureFlags,
      BookingItemPermissions accessManager,
      BookingCalendarFeedGenerator generator,
      BookingCalendarCreationTransaction creationTransaction) {
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example/context");
    return new BookingCalendarManagerImpl(
        subscriptionDao,
        userSubscriptionDao,
        configurationDao,
        bookingManager,
        instrumentDao,
        featureFlags,
        accessManager,
        generator,
        creationTransaction,
        new BookingCalendarProperties(100, 10_000, 1),
        properties,
        () -> RAW_TOKEN);
  }

  static User createOwner() {
    User owner = TestFactory.createAnyUser("owner");
    owner.setId(7L);
    return owner;
  }

  static BookingConfiguration createConfiguration(User owner) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(CONFIGURATION_ID);
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 101L));
    configuration.setResourceAccess(
        new ResourceAccess(BookingResourceRoleScheme.SCHEME_KEY, owner, new Date()));
    return configuration;
  }

  static BookableItemCalendarSubscription subscription(
      BookingConfiguration configuration, User owner) {
    return new BookableItemCalendarSubscription(
        configuration, owner, CryptoUtils.hashToken("previous"), "previous", new Date(1000L));
  }

  static ResolvedResourceAccess ownerAccess() {
    return new ResolvedResourceAccess(
        Optional.of(BookingResourceRoleScheme.OWNER),
        Set.of(
            BookingResourceRoleScheme.READ_RESOURCE,
            BookingResourceRoleScheme.CREATE_CALENDAR_SUBSCRIPTION,
            BookingResourceRoleScheme.MANAGE_ALL_EVENTS),
        List.of());
  }
}
