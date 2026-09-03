package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.UserBookingCalendarSubscriptionDao;
import com.researchspace.booking.service.BookingCalendarFeedGenerator.CalendarGenerationException;
import com.researchspace.booking.service.BookingCalendarFeedGenerator.CalendarTooLargeException;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarEvent;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarSource;
import com.researchspace.booking.service.TimeSlotBookingManager.CalendarSourceTooLargeException;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.booking.UserBookingCalendarSubscription;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import jakarta.persistence.OptimisticLockException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import org.apache.shiro.authz.AuthorizationException;
import org.hibernate.StaleObjectStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.orm.hibernate5.HibernateJdbcException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional policy module for booking calendar subscriptions and downloads. */
@Service("bookingCalendarManager")
@Transactional
public class BookingCalendarManagerImpl implements BookingCalendarManager {

  private static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);
  private static final String INACTIVE_USER_ETAG = "\"inactive\"";
  private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final BookingCalendarSubscriptionDao subscriptionDao;
  private final UserBookingCalendarSubscriptionDao userSubscriptionDao;
  private final BookingConfigurationDao configurationDao;
  private final TimeSlotBookingManager bookingManager;
  private final InstrumentDao instrumentDao;
  private final FeatureFlagManager featureFlags;
  private final ResourceAccessManager accessManager;
  private final BookingCalendarFeedGenerator generator;
  private final BookingCalendarProperties limits;
  private final URI serverBaseUrl;
  private final Semaphore generationSlots;
  private final Supplier<String> tokenSupplier;

  @Autowired
  public BookingCalendarManagerImpl(
      @Qualifier("bookingCalendarSubscriptionDao") BookingCalendarSubscriptionDao subscriptionDao,
      @Qualifier("userBookingCalendarSubscriptionDao")
          UserBookingCalendarSubscriptionDao userSubscriptionDao,
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurationDao,
      TimeSlotBookingManager bookingManager,
      InstrumentDao instrumentDao,
      FeatureFlagManager featureFlags,
      ResourceAccessManager accessManager,
      BookingCalendarFeedGenerator generator,
      BookingCalendarProperties limits,
      IPropertyHolder properties) {
    this(
        subscriptionDao,
        userSubscriptionDao,
        configurationDao,
        bookingManager,
        instrumentDao,
        featureFlags,
        accessManager,
        generator,
        limits,
        properties,
        () -> SecureStringUtils.getURLSafeSecureRandomString(32));
  }

  BookingCalendarManagerImpl(
      BookingCalendarSubscriptionDao subscriptionDao,
      UserBookingCalendarSubscriptionDao userSubscriptionDao,
      BookingConfigurationDao configurationDao,
      TimeSlotBookingManager bookingManager,
      InstrumentDao instrumentDao,
      FeatureFlagManager featureFlags,
      ResourceAccessManager accessManager,
      BookingCalendarFeedGenerator generator,
      BookingCalendarProperties limits,
      IPropertyHolder properties,
      Supplier<String> tokenSupplier) {
    this.subscriptionDao = subscriptionDao;
    this.userSubscriptionDao = userSubscriptionDao;
    this.configurationDao = configurationDao;
    this.bookingManager = bookingManager;
    this.instrumentDao = instrumentDao;
    this.featureFlags = featureFlags;
    this.accessManager = accessManager;
    this.generator = generator;
    this.limits = limits;
    this.serverBaseUrl = validatedBaseUrl(properties.getServerUrl());
    this.generationSlots = new Semaphore(limits.maxConcurrentGenerations(), true);
    this.tokenSupplier = tokenSupplier;
  }

  @Override
  public Status status(Long configurationId, User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeatureRead(subject);
    BookingConfiguration configuration = requireReadableConfiguration(configurationId, subject);
    if (configuration.getState() == BookingConfigurationState.ARCHIVED) {
      return new Status(false, null, null);
    }
    return subscriptionDao
        .findByUserIdAndConfigurationId(subject.getId(), configurationId)
        .map(
            subscription ->
                new Status(
                    true,
                    subscription.getUpdatedAt(),
                    subscription.getRawToken() == null
                        ? null
                        : subscriptionUrl(subscription.getRawToken())))
        .orElseGet(() -> new Status(false, null, null));
  }

  @Override
  public Created createOrRotate(Long configurationId, User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeatureMutation(subject);
    BookingConfiguration configuration =
        configurationDao
            .lockById(configurationId)
            .orElseThrow(BookingCalendarNotFoundException::new);
    if (configuration.getState() == BookingConfigurationState.ARCHIVED) {
      throw new BookingConfigurationLifecycleException();
    }
    requireCapability(
        configuration, subject, BookingResourceRoleScheme.CREATE_CALENDAR_SUBSCRIPTION);
    Optional<BookableItemCalendarSubscription> existing =
        subscriptionDao.findByUserIdAndConfigurationId(subject.getId(), configurationId);
    String rawToken = tokenSupplier.get();
    Date updatedAt = new Date();
    BookableItemCalendarSubscription subscription;
    String action;
    if (existing.isPresent()) {
      subscription = existing.get();
      subscription.setTokenHash(CryptoUtils.hashToken(rawToken));
      subscription.setRawToken(rawToken);
      subscription.setUpdatedAt(updatedAt);
      action = "replaced";
    } else {
      subscription =
          new BookableItemCalendarSubscription(
              configuration, subject, CryptoUtils.hashToken(rawToken), rawToken, updatedAt);
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
    String url = subscriptionUrl(rawToken);
    Status status = new Status(true, saved.getUpdatedAt(), url);
    return new Created(status, url);
  }

  private String subscriptionUrl(String rawToken) {
    return BookingCalendarFeedGenerator.appendPath(
                serverBaseUrl, "/public/booking/calendars/feed.ics")
            .toString()
        + "?token="
        + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
  }

  @Override
  public void revoke(Long configurationId, User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeatureMutation(subject);
    BookingConfiguration configuration =
        configurationDao
            .lockActiveById(configurationId)
            .orElseThrow(BookingCalendarNotFoundException::new);
    requireCapability(configuration, subject, BookingResourceRoleScheme.READ_RESOURCE);
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
  public Status userStatus(User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeatureRead(subject);
    return userSubscriptionDao
        .findByUserId(subject.getId())
        .map(
            subscription ->
                new Status(
                    true,
                    subscription.getUpdatedAt(),
                    subscriptionUrl(subscription.getRawToken()),
                    userEtag(subscription)))
        .orElseGet(() -> new Status(false, null, null, INACTIVE_USER_ETAG));
  }

  @Override
  public Created createOrRotateUser(User subject, User actor, String expectedEtag) {
    requirePersonalCaller(subject, actor);
    requireFeatureMutation(subject);
    User lockedUser = userSubscriptionDao.lockUser(subject.getId());
    if (lockedUser == null) {
      throw new AuthorizationException("Active personal caller is required");
    }
    Optional<UserBookingCalendarSubscription> existing =
        userSubscriptionDao.findByUserId(subject.getId());
    String currentEtag =
        existing.map(BookingCalendarManagerImpl::userEtag).orElse(INACTIVE_USER_ETAG);
    if (!Objects.equals(expectedEtag, currentEtag)) {
      throw new UserSubscriptionConflictException();
    }
    String rawToken = tokenSupplier.get();
    Date updatedAt = new Date();
    UserBookingCalendarSubscription subscription;
    String action;
    if (existing.isPresent()) {
      subscription = existing.get();
      subscription.setTokenHash(CryptoUtils.hashToken(rawToken));
      subscription.setRawToken(rawToken);
      subscription.setUpdatedAt(updatedAt);
      action = "replaced";
    } else {
      subscription =
          new UserBookingCalendarSubscription(
              lockedUser, CryptoUtils.hashToken(rawToken), rawToken, updatedAt);
      action = "created";
    }
    UserBookingCalendarSubscription saved;
    try {
      saved = userSubscriptionDao.saveAndFlush(subscription);
    } catch (OptimisticLockException
        | StaleObjectStateException
        | ConstraintViolationException
        | DataIntegrityViolationException
        | OptimisticLockingFailureException
        | HibernateJdbcException ex) {
      throw new UserSubscriptionConflictException(ex);
    }
    SECURITY_LOG.info(
        "User booking calendar subscription {} by actor [{}] for subject [{}], subscription [{}]",
        action,
        actor.getUsername(),
        subject.getUsername(),
        saved.getId());
    String url = subscriptionUrl(rawToken);
    return new Created(new Status(true, saved.getUpdatedAt(), url, userEtag(saved)), url);
  }

  @Override
  public void revokeUser(User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeatureMutation(subject);
    userSubscriptionDao.lockUser(subject.getId());
    Optional<UserBookingCalendarSubscription> existing =
        userSubscriptionDao.findByUserId(subject.getId());
    userSubscriptionDao.removeForUser(subject.getId());
    existing.ifPresent(
        subscription ->
            SECURITY_LOG.info(
                "User booking calendar subscription revoked by actor [{}] for subject [{}],"
                    + " subscription [{}]",
                actor.getUsername(),
                subject.getUsername(),
                subscription.getId()));
  }

  private static String userEtag(UserBookingCalendarSubscription subscription) {
    return "\"subscription-" + subscription.getVersion() + "\"";
  }

  /** A user subscription changed after the caller read its status. */
  public static class UserSubscriptionConflictException extends RuntimeException {

    public UserSubscriptionConflictException() {}

    UserSubscriptionConflictException(Throwable cause) {
      super(cause);
    }
  }

  @Override
  public int resetForConfiguration(Long configurationId, User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeatureMutation(subject);
    BookingConfiguration configuration =
        configurationDao
            .lockActiveById(configurationId)
            .orElseThrow(BookingCalendarNotFoundException::new);
    requireMutationCapability(configuration, subject, BookingResourceRoleScheme.MANAGE_ALL_EVENTS);
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
              generator.generate(source.get(), serverBaseUrl, locale, limits.maxBytes()),
              downloadFilename(source.get(), booking.get())));
    } catch (CalendarTooLargeException | CalendarGenerationException ex) {
      SECURITY_LOG.warn(
          "Booking calendar download generation failed for booking [{}]", bookingId, ex);
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
      if (subscription.isPresent()) {
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
      if (!active(owner) || !featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, owner)) {
        SECURITY_LOG.debug(
            "User booking calendar subscription [{}] is currently unavailable", value.getId());
        return new NotFound();
      }
      CalendarSource source =
          bookingManager.getUserCalendarSource(owner, refreshedAt, limits.maxEvents());
      try {
        return new Available(generator.generate(source, serverBaseUrl, locale, limits.maxBytes()));
      } catch (CalendarTooLargeException | CalendarSourceTooLargeException ex) {
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
    var instrument =
        instrumentDao.getBookingSummaries(java.util.Set.of(target.id())).get(target.id());
    if (instrument == null || instrument.deleted()) {
      return Optional.empty();
    }
    CalendarEvent event =
        new CalendarEvent(
            booking.getId(),
            booking.getStartTime(),
            booking.getEndTime(),
            booking.getCreatedAt(),
            booking.getUpdatedAt(),
            booking.getKind(),
            booking.getPrivacy(),
            booking.getVisibleBookedBy(),
            booking.getVisibleCreatedBy(),
            booking.getVisiblePurpose(),
            booking.isCanEdit());
    return Optional.of(
        new CalendarSource(instrument.name(), configuration.getTimeZone(), List.of(event)));
  }

  private BookingConfiguration requireReadableConfiguration(Long configurationId, User subject) {
    BookingConfiguration configuration =
        configurationDao
            .getSafeNull(configurationId)
            .orElseThrow(BookingCalendarNotFoundException::new);
    requireCapability(configuration, subject, BookingResourceRoleScheme.READ_RESOURCE);
    return configuration;
  }

  private void requireCapability(
      BookingConfiguration configuration, User subject, String capability) {
    if (!hasCapability(configuration, subject, capability)) {
      throw new BookingCalendarNotFoundException();
    }
  }

  private boolean hasCapability(
      BookingConfiguration configuration, User subject, String capability) {
    ResolvedResourceAccess access =
        accessManager.resolve(configuration.getResourceAccess(), subject);
    return access.hasCapability(capability);
  }

  private void requireMutationCapability(
      BookingConfiguration configuration, User subject, String capability) {
    ResolvedResourceAccess access =
        accessManager.resolve(configuration.getResourceAccess(), subject);
    if (!access.hasCapability(BookingResourceRoleScheme.READ_RESOURCE)) {
      throw new BookingCalendarNotFoundException();
    }
    if (!access.hasCapability(capability)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private void requireFeatureRead(User subject) {
    requireActiveUser(subject);
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)) {
      throw new BookingCalendarNotFoundException();
    }
  }

  private void requireFeatureMutation(User subject) {
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
