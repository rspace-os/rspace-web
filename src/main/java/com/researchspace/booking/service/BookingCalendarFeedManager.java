package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.UserBookingCalendarSubscriptionDao;
import com.researchspace.booking.service.BookingCalendarFeedGenerator.CalendarGenerationException;
import com.researchspace.booking.service.BookingCalendarFeedGenerator.CalendarTooLargeException;
import com.researchspace.booking.service.BookingCalendarManager.AtCapacity;
import com.researchspace.booking.service.BookingCalendarManager.Available;
import com.researchspace.booking.service.BookingCalendarManager.Download;
import com.researchspace.booking.service.BookingCalendarManager.FeedResult;
import com.researchspace.booking.service.BookingCalendarManager.NotFound;
import com.researchspace.booking.service.BookingCalendarManager.Oversized;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarEvent;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarSource;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarSourceTooLargeException;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.BookingInstrumentQueryDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.booking.UserBookingCalendarSubscription;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import java.net.URI;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Generates feeds and downloads inside the lifecycle facade's existing transaction. */
final class BookingCalendarFeedManager {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final BookingCalendarSubscriptionDao subscriptionDao;
  private final UserBookingCalendarSubscriptionDao userSubscriptionDao;
  private final TimeSlotBookingManager bookingManager;
  private final BookingInstrumentQueryDao instrumentDao;
  private final FeatureFlagManager featureFlags;
  private final ResourceAccessManager accessManager;
  private final BookingCalendarFeedGenerator generator;
  private final BookingCalendarProperties limits;
  private final URI serverBaseUrl;
  private final Semaphore generationSlots;

  BookingCalendarFeedManager(
      BookingCalendarSubscriptionDao subscriptionDao,
      UserBookingCalendarSubscriptionDao userSubscriptionDao,
      TimeSlotBookingManager bookingManager,
      BookingInstrumentQueryDao instrumentDao,
      FeatureFlagManager featureFlags,
      ResourceAccessManager accessManager,
      BookingCalendarFeedGenerator generator,
      BookingCalendarProperties limits,
      URI serverBaseUrl,
      Semaphore generationSlots) {
    this.subscriptionDao = subscriptionDao;
    this.userSubscriptionDao = userSubscriptionDao;
    this.bookingManager = bookingManager;
    this.instrumentDao = instrumentDao;
    this.featureFlags = featureFlags;
    this.accessManager = accessManager;
    this.generator = generator;
    this.limits = limits;
    this.serverBaseUrl = serverBaseUrl;
    this.generationSlots = generationSlots;
  }

  public Optional<Download> download(Long bookingId, User subject, Locale locale) {
    requireActiveUser(subject);
    requireFeatureRead(subject);
    Optional<TimeSlotBooking> booking = bookingManager.getBooking(bookingId, subject);
    if (booking.isEmpty() || booking.get().getState() != BookingState.CONFIRMED) {
      return Optional.empty();
    }
    Optional<CalendarSource> source = downloadSource(booking.get(), subject);
    if (source.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new Download(
              generator.generateDownload(source.get(), serverBaseUrl, locale, limits.maxBytes()),
              downloadFilename(source.get(), booking.get())));
    } catch (CalendarTooLargeException | CalendarGenerationException ex) {
      SECURITY_LOG.warn(
          "Booking calendar download generation failed for booking [{}]", bookingId, ex);
      return Optional.empty();
    }
  }

  public FeedResult feed(String rawToken, Locale locale, Date refreshedAt) {
    Objects.requireNonNull(rawToken, "Calendar token");
    Objects.requireNonNull(refreshedAt, "Calendar refresh time");
    if (!generationSlots.tryAcquire()) {
      return new AtCapacity();
    }
    try {
      Optional<BookableItemCalendarSubscription> subscription =
          subscriptionDao.findByTokenHash(CryptoUtils.hashToken(rawToken));
      if (subscription.isPresent()) {
        BookableItemCalendarSubscription value = subscription.get();
        User owner = value.getUser();
        if (!BookingDisplayPreferencesManagerImpl.active(owner)
            || !featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)) {
          SECURITY_LOG.debug(
              "Booking calendar subscription [{}] is currently unavailable", value.getId());
          return new NotFound();
        }
        Optional<CalendarSource> source =
            bookingManager.getCalendarSource(
                value.getBookingConfiguration().getId(), owner, refreshedAt, limits.maxEvents());
        if (source.isEmpty()) {
          SECURITY_LOG.debug(
              "Booking calendar subscription [{}] is currently unavailable", value.getId());
          return new NotFound();
        }
        try {
          return new Available(
              generator.generate(source.get(), serverBaseUrl, locale, limits.maxBytes()));
        } catch (CalendarTooLargeException ex) {
          SECURITY_LOG.warn(
              "Booking calendar feed exceeds a safety limit for configuration [{}], subscription"
                  + " [{}]",
              value.getBookingConfiguration().getId(),
              value.getId(),
              ex);
          return new Oversized();
        } catch (CalendarGenerationException ex) {
          SECURITY_LOG.warn(
              "Booking calendar feed generation failed for configuration [{}], subscription [{}]",
              value.getBookingConfiguration().getId(),
              value.getId(),
              ex);
          return new Oversized();
        }
      }
      Optional<UserBookingCalendarSubscription> userSubscription =
          userSubscriptionDao.findByTokenHash(CryptoUtils.hashToken(rawToken));
      if (userSubscription.isEmpty()) {
        return new NotFound();
      }
      UserBookingCalendarSubscription value = userSubscription.get();
      User owner = value.getUser();
      if (!BookingDisplayPreferencesManagerImpl.active(owner)
          || !featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)) {
        SECURITY_LOG.debug(
            "User booking calendar subscription [{}] is currently unavailable", value.getId());
        return new NotFound();
      }
      CalendarSource source =
          bookingManager.getUserCalendarSource(owner, refreshedAt, limits.maxEvents());
      try {
        return new Available(generator.generate(source, serverBaseUrl, locale, limits.maxBytes()));
      } catch (CalendarTooLargeException ex) {
        SECURITY_LOG.warn(
            "User booking calendar feed exceeds a safety limit for subscription [{}]",
            value.getId(),
            ex);
        return new Oversized();
      } catch (CalendarGenerationException ex) {
        SECURITY_LOG.warn(
            "User booking calendar feed generation failed for subscription [{}]",
            value.getId(),
            ex);
        return new Oversized();
      }
    } catch (CalendarSourceTooLargeException ex) {
      SECURITY_LOG.warn("Booking calendar feed source exceeds the configured event limit", ex);
      return new Oversized();
    } catch (RuntimeException ex) {
      SECURITY_LOG.warn("Booking calendar feed is unavailable", ex);
      return new Oversized();
    } finally {
      generationSlots.release();
    }
  }

  /**
   * Names one downloaded file after the item, its global identifier, and the local start date, so a
   * folder of saved bookings sorts and reads sensibly: {@code confocal-microscope-IN12-2026-09-12}.
   */
  private static String downloadFilename(CalendarSource source, TimeSlotBooking booking) {
    String slug =
        source
            .itemName()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-|-$", "");
    BookableTargetReference target = booking.getBookingConfiguration().getTarget();
    String globalId = "IN" + target.id();
    String date =
        DATE_FORMAT.withZone(zone(source.timeZone())).format(booking.getStartTime().toInstant());
    return (slug.isEmpty() ? "booking" : slug) + "-" + globalId + "-" + date + ".ics";
  }

  private static ZoneId zone(String timeZone) {
    try {
      return ZoneId.of(timeZone);
    } catch (DateTimeException ex) {
      return ZoneOffset.UTC;
    }
  }

  private Optional<CalendarSource> downloadSource(TimeSlotBooking booking, User subject) {
    BookingConfiguration configuration = booking.getBookingConfiguration();
    if (!hasCapability(configuration, subject, BookingResourceRoleScheme.READ_RESOURCE)) {
      return Optional.empty();
    }
    BookableTargetReference target = configuration.getTarget();
    if (target == null || target.type() != BookableTargetType.INSTRUMENT) {
      return Optional.empty();
    }
    var instrument = instrumentDao.getBookingSummaries(Set.of(target.id())).get(target.id());
    if (instrument == null || instrument.deleted()) {
      return Optional.empty();
    }
    CalendarEvent event = CalendarEvent.of(booking, null);
    return Optional.of(
        new CalendarSource(instrument.name(), configuration.getTimeZone(), List.of(event)));
  }

  private boolean hasCapability(
      BookingConfiguration configuration, User subject, String capability) {
    return BookingCalendarAccessChecks.hasCapability(
        accessManager, configuration, subject, capability);
  }

  private void requireFeatureRead(User subject) {
    BookingCalendarAccessChecks.requireFeatureRead(featureFlags, subject);
  }

  private static void requireActiveUser(User user) {
    BookingCalendarAccessChecks.requireActiveUser(user);
  }
}
