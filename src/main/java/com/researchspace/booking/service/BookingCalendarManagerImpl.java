package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.BookingCalendarFeedGenerator.CalendarGenerationException;
import com.researchspace.booking.service.BookingCalendarFeedGenerator.CalendarTooLargeException;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarEvent;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarSource;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarSourceTooLargeException;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.FeatureFlagManager;
import java.net.URI;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/** Transactional policy module for booking calendar subscriptions and downloads. */
@Service("bookingCalendarManager")
public class BookingCalendarManagerImpl implements BookingCalendarManager {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private final BookingCalendarSubscriptionDao subscriptionDao;
  private final BookingConfigurationDao configurationDao;
  private final TimeSlotBookingManager bookingManager;
  private final InstrumentDao instrumentDao;
  private final FeatureFlagManager featureFlags;
  private final ObjectProvider<ResourceRegistry> resourceRegistry;
  private final BookingCalendarFeedGenerator generator;
  private final BookingCalendarProperties limits;
  private final URI serverBaseUrl;
  private final Semaphore generationSlots;
  private final Supplier<String> tokenSupplier;

  @Autowired
  public BookingCalendarManagerImpl(
      @Qualifier("bookingCalendarSubscriptionDao") BookingCalendarSubscriptionDao subscriptionDao,
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurationDao,
      TimeSlotBookingManager bookingManager,
      InstrumentDao instrumentDao,
      FeatureFlagManager featureFlags,
      ObjectProvider<ResourceRegistry> resourceRegistry,
      BookingCalendarFeedGenerator generator,
      BookingCalendarProperties limits,
      IPropertyHolder properties) {
    this(
        subscriptionDao,
        configurationDao,
        bookingManager,
        instrumentDao,
        featureFlags,
        resourceRegistry,
        generator,
        limits,
        properties,
        () -> SecureStringUtils.getURLSafeSecureRandomString(32));
  }

  BookingCalendarManagerImpl(
      BookingCalendarSubscriptionDao subscriptionDao,
      BookingConfigurationDao configurationDao,
      TimeSlotBookingManager bookingManager,
      InstrumentDao instrumentDao,
      FeatureFlagManager featureFlags,
      ObjectProvider<ResourceRegistry> resourceRegistry,
      BookingCalendarFeedGenerator generator,
      BookingCalendarProperties limits,
      IPropertyHolder properties,
      Supplier<String> tokenSupplier) {
    this.subscriptionDao = subscriptionDao;
    this.configurationDao = configurationDao;
    this.bookingManager = bookingManager;
    this.instrumentDao = instrumentDao;
    this.featureFlags = featureFlags;
    this.resourceRegistry = resourceRegistry;
    this.generator = generator;
    this.limits = limits;
    this.serverBaseUrl = validatedBaseUrl(properties.getServerUrl());
    this.generationSlots = new Semaphore(limits.maxConcurrentGenerations(), true);
    this.tokenSupplier = tokenSupplier;
  }

  @Override
  public Status status(Long configurationId, User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeature(subject);
    requireReadableConfiguration(configurationId, subject);
    return subscriptionDao
        .findByUserIdAndConfigurationId(subject.getId(), configurationId)
        .map(subscription -> new Status(true, subscription.getUpdatedAt()))
        .orElseGet(() -> new Status(false, null));
  }

  @Override
  public Created createOrRotate(Long configurationId, User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeature(subject);
    BookingConfiguration configuration =
        configurationDao
            .lockById(configurationId)
            .orElseThrow(BookingCalendarNotFoundException::new);
    requireReadableConfiguration(configurationId, subject);
    Optional<BookableItemCalendarSubscription> existing =
        subscriptionDao.findByUserIdAndConfigurationId(subject.getId(), configurationId);
    String rawToken = tokenSupplier.get();
    Date updatedAt = new Date();
    BookableItemCalendarSubscription subscription;
    String action;
    if (existing.isPresent()) {
      subscription = existing.get();
      subscription.setTokenHash(CryptoUtils.hashToken(rawToken));
      subscription.setUpdatedAt(updatedAt);
      action = "replaced";
    } else {
      subscription =
          new BookableItemCalendarSubscription(
              configuration, subject, CryptoUtils.hashToken(rawToken), updatedAt);
      action = "created";
    }
    BookableItemCalendarSubscription saved = subscriptionDao.saveAndFlush(subscription);
    SECURITY_LOG.info(
        "Booking calendar subscription {} by actor [{}] for subject [{}], configuration [{}],"
            + " subscription [{}]",
        action,
        actor.getUsername(),
        subject.getUsername(),
        configurationId,
        saved.getId());
    Status status = new Status(true, saved.getUpdatedAt());
    String url =
        BookingCalendarFeedGenerator.appendPath(
                serverBaseUrl, "/public/booking/calendars/" + rawToken + ".ics")
            .toString();
    return new Created(status, url);
  }

  @Override
  public void revoke(Long configurationId, User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeature(subject);
    configurationDao.lockById(configurationId).orElseThrow(BookingCalendarNotFoundException::new);
    Optional<BookableItemCalendarSubscription> existing =
        subscriptionDao.findByUserIdAndConfigurationId(subject.getId(), configurationId);
    subscriptionDao.removeForUserAndConfiguration(subject.getId(), configurationId);
    existing.ifPresent(
        subscription ->
            SECURITY_LOG.info(
                "Booking calendar subscription revoked by actor [{}] for subject [{}],"
                    + " configuration [{}], subscription [{}]",
                actor.getUsername(),
                subject.getUsername(),
                configurationId,
                subscription.getId()));
  }

  @Override
  public int resetForConfiguration(Long configurationId, User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeature(subject);
    if (!subject.hasRole(Role.SYSTEM_ROLE)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
    configurationDao.lockById(configurationId).orElseThrow(BookingCalendarNotFoundException::new);
    int deleted = subscriptionDao.deleteByConfigurationId(configurationId);
    SECURITY_LOG.info(
        "Booking calendar subscriptions reset by actor [{}] for configuration [{}], revoked [{}]",
        actor.getUsername(),
        configurationId,
        deleted);
    return deleted;
  }

  @Override
  public Optional<Download> download(Long bookingId, User subject, Locale locale) {
    requireActiveUser(subject);
    requireFeature(subject);
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
          new Download(generator.generate(source.get(), serverBaseUrl, locale, limits.maxBytes())));
    } catch (CalendarTooLargeException | CalendarGenerationException ex) {
      return Optional.empty();
    }
  }

  @Override
  public FeedResult feed(String rawToken, Locale locale, Date refreshedAt) {
    Objects.requireNonNull(rawToken, "Calendar token");
    Objects.requireNonNull(refreshedAt, "Calendar refresh time");
    if (!generationSlots.tryAcquire()) {
      return new AtCapacity();
    }
    try {
      Optional<BookableItemCalendarSubscription> subscription =
          subscriptionDao.findByTokenHash(CryptoUtils.hashToken(rawToken));
      if (subscription.isEmpty()) {
        return new NotFound();
      }
      BookableItemCalendarSubscription value = subscription.get();
      User owner = value.getUser();
      if (!active(owner) || !featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)) {
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
      } catch (CalendarTooLargeException | CalendarSourceTooLargeException ex) {
        SECURITY_LOG.warn(
            "Booking calendar feed exceeds a safety limit for configuration [{}], subscription"
                + " [{}]",
            value.getBookingConfiguration().getId(),
            value.getId());
        return new Oversized();
      } catch (CalendarGenerationException ex) {
        SECURITY_LOG.warn(
            "Booking calendar feed generation failed for configuration [{}], subscription [{}]",
            value.getBookingConfiguration().getId(),
            value.getId());
        return new Oversized();
      }
    } catch (CalendarSourceTooLargeException ex) {
      return new Oversized();
    } catch (RuntimeException ex) {
      return new NotFound();
    } finally {
      generationSlots.release();
    }
  }

  private Optional<CalendarSource> downloadSource(TimeSlotBooking booking, User subject) {
    BookingConfiguration configuration = booking.getBookingConfiguration();
    BookableTargetReference target = configuration.getTarget();
    if (target == null || target.type() != BookableTargetType.INSTRUMENT) {
      return Optional.empty();
    }
    RelationshipReadAccess access = targetAccess(subject);
    Optional<Instrument> instrument =
        instrumentDao
            .getReadableResources(idRequest(target.id()), access.result("instruments"))
            .resources()
            .stream()
            .findFirst();
    if (instrument.isEmpty() || instrument.get().isDeleted()) {
      return Optional.empty();
    }
    CalendarEvent event =
        new CalendarEvent(
            booking.getId(),
            booking.getStartTime(),
            booking.getEndTime(),
            booking.getCreatedAt(),
            booking.getUpdatedAt(),
            booking.getPrivacy(),
            booking.getVisibleBookedBy(),
            booking.getVisiblePurpose(),
            booking.isCanEdit());
    return Optional.of(
        new CalendarSource(
            instrument.get().getName(), configuration.getTimeZone(), List.of(event)));
  }

  private BookingConfiguration requireReadableConfiguration(Long configurationId, User subject) {
    return configurationDao
        .getResources(idRequest(configurationId), 1, targetAccess(subject))
        .stream()
        .findFirst()
        .orElseThrow(BookingCalendarNotFoundException::new);
  }

  private RelationshipReadAccess targetAccess(User subject) {
    return RelationshipReadAccess.forActor(resourceRegistry.getObject(), subject);
  }

  private void requireFeature(User subject) {
    requireActiveUser(subject);
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private static void requirePersonalCaller(User subject, User actor) {
    requireActiveUser(subject);
    requireActiveUser(actor);
    if (!Objects.equals(subject.getId(), actor.getId())) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private static void requireActiveUser(User user) {
    if (!active(user)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private static boolean active(User user) {
    return user != null && user.isEnabled() && !user.isAccountLocked();
  }

  private static ResourceRequest idRequest(Long id) {
    return new ResourceRequest(
        new FilterExpression.Comparison("id", Operator.EQUAL, List.of(id), false),
        List.of(),
        new ResourceRequest.Page(1, 1),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  static URI validatedBaseUrl(String configured) {
    URI uri;
    try {
      uri = URI.create(configured).normalize();
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("server.urls.prefix must be an absolute URL", ex);
    }
    String host = uri.getHost();
    boolean loopback =
        "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
    boolean schemeAllowed = "https".equalsIgnoreCase(uri.getScheme());
    if (loopback) {
      schemeAllowed |= "http".equalsIgnoreCase(uri.getScheme());
    }
    if (host == null
        || !schemeAllowed
        || uri.getUserInfo() != null
        || uri.getFragment() != null
        || uri.getQuery() != null) {
      throw new IllegalArgumentException("server.urls.prefix is unsafe for calendar links");
    }
    return uri;
  }

  /** Generic internal not-found signal for authenticated management operations. */
  public static final class BookingCalendarNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;
  }
}
