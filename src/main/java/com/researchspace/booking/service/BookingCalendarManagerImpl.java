package com.researchspace.booking.service;

import static com.researchspace.featureflags.FeatureFlags.BOOKING_ENABLED;

import com.researchspace.booking.dao.BookingCalendarPermissionGateDao;
import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.CurrentItemCalendarSubscription;
import com.researchspace.booking.dao.UserBookingCalendarSubscriptionDao;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.dao.BookingInstrumentQueryDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableItemCalendarSubscription;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.UserBookingCalendarSubscription;
import com.researchspace.model.inventory.InstrumentReadSummary;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import jakarta.persistence.OptimisticLockException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

  private final BookingCalendarCreationTransaction creationTransaction;
  private final BookingCalendarPermissionGateDao permissionGate;
  private final BookingCalendarSubscriptionDao subscriptionDao;
  private final UserBookingCalendarSubscriptionDao userSubscriptionDao;
  private final BookingConfigurationDao configurationDao;
  private final TimeSlotBookingManager bookingManager;
  private final BookingInstrumentQueryDao instrumentDao;
  private final FeatureFlagManager featureFlags;
  private final BookingItemPermissions accessManager;
  private final BookingCalendarFeedGenerator generator;
  private final BookingCalendarProperties limits;
  private final URI serverBaseUrl;
  private final Semaphore generationSlots;
  private final Supplier<String> tokenSupplier;
  private final BookingCalendarFeedManager feedManager;

  @Autowired
  public BookingCalendarManagerImpl(
      @Qualifier("bookingCalendarSubscriptionDao") BookingCalendarSubscriptionDao subscriptionDao,
      @Qualifier("userBookingCalendarSubscriptionDao")
          UserBookingCalendarSubscriptionDao userSubscriptionDao,
      @Qualifier("bookingConfigurationDao") BookingConfigurationDao configurationDao,
      TimeSlotBookingManager bookingManager,
      BookingInstrumentQueryDao instrumentDao,
      FeatureFlagManager featureFlags,
      BookingItemPermissions accessManager,
      BookingCalendarFeedGenerator generator,
      BookingCalendarCreationTransaction creationTransaction,
      BookingCalendarPermissionGateDao permissionGate,
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
        creationTransaction,
        permissionGate,
        limits,
        properties,
        () -> SecureStringUtils.getURLSafeSecureRandomString(32));
  }

  BookingCalendarManagerImpl(
      BookingCalendarSubscriptionDao subscriptionDao,
      UserBookingCalendarSubscriptionDao userSubscriptionDao,
      BookingConfigurationDao configurationDao,
      TimeSlotBookingManager bookingManager,
      BookingInstrumentQueryDao instrumentDao,
      FeatureFlagManager featureFlags,
      BookingItemPermissions accessManager,
      BookingCalendarFeedGenerator generator,
      BookingCalendarCreationTransaction creationTransaction,
      BookingCalendarPermissionGateDao permissionGate,
      BookingCalendarProperties limits,
      IPropertyHolder properties,
      Supplier<String> tokenSupplier) {
    this.creationTransaction = creationTransaction;
    this.permissionGate = permissionGate;
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
    this.feedManager =
        new BookingCalendarFeedManager(
            this.subscriptionDao,
            this.userSubscriptionDao,
            this.bookingManager,
            this.instrumentDao,
            this.featureFlags,
            this.accessManager,
            this.generator,
            this.limits,
            this.serverBaseUrl,
            this.generationSlots);
  }

  @Override
  public Status status(Long configurationId, User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeatureRead(subject);
    BookingConfiguration configuration = requireReadableConfiguration(configurationId, subject);
    if (configuration.getState() == BookingConfigurationState.ARCHIVED) {
      return new Status(false, null, null, INACTIVE_USER_ETAG);
    }
    return subscriptionDao
        .findByUserIdAndConfigurationId(subject.getId(), configurationId)
        .map(this::itemStatus)
        .orElseGet(() -> new Status(false, null, null, INACTIVE_USER_ETAG));
  }

  private Status itemStatus(BookableItemCalendarSubscription subscription) {
    return new Status(
        true,
        subscription.getUpdatedAt(),
        subscriptionUrl(subscription.getRawToken()),
        itemEtag(subscription));
  }

  @Override
  public Created create(Long configurationId, User subject, User actor) {
    permissionGate.assertNoSuspendedAncestor();
    if (permissionGate.hasActiveGate()) {
      requirePersonalIdentity(subject, actor);
      return issueItemLink(configurationId, subject, null);
    }
    requirePersonalCaller(subject, actor);
    return creationTransaction.create(
        subject.getId(), freshSubject -> issueItemLink(configurationId, freshSubject, null));
  }

  @Override
  public Created rotate(Long configurationId, User subject, User actor, String expectedEtag) {
    permissionGate.assertNoSuspendedAncestor();
    Objects.requireNonNull(expectedEtag, "Expected ETag");
    if (permissionGate.hasActiveGate()) {
      requirePersonalIdentity(subject, actor);
      return issueItemLink(configurationId, subject, expectedEtag);
    }
    requirePersonalCaller(subject, actor);
    return creationTransaction.create(
        subject.getId(),
        freshSubject -> issueItemLink(configurationId, freshSubject, expectedEtag));
  }

  /** Issues or returns the caller's item link; rotation requires the current ETag. */
  private Created issueItemLink(Long configurationId, User subject, String rotateFromEtag) {
    if (permissionGate.hasActiveGate()) {
      requireFeatureEnabled(subject);
    } else {
      requireFeatureMutation(subject);
    }
    BookingConfiguration configuration =
        configurationDao
            .lockById(configurationId)
            .orElseThrow(BookingCalendarNotFoundException::new);
    // lockById acquires the shared permission gate before the target and configuration locks.
    requireMutationCapability(
        configuration, subject, BookingResourceRoleScheme.CREATE_CALENDAR_SUBSCRIPTION);
    if (configuration.getState() == BookingConfigurationState.ARCHIVED) {
      throw new BookingConfigurationLifecycleException();
    }
    Optional<CurrentItemCalendarSubscription> existing =
        subscriptionDao.lockCurrent(subject.getId(), configurationId);
    if (rotateFromEtag == null && existing.isPresent()) {
      CurrentItemCalendarSubscription current = existing.get();
      String currentUrl = current.rawToken() == null ? null : subscriptionUrl(current.rawToken());
      Status status = new Status(true, current.updatedAt(), currentUrl, itemEtag(current));
      return new Created(status, currentUrl, false);
    }
    if (rotateFromEtag != null
        && (existing.isEmpty()
            || !Objects.equals(rotateFromEtag, itemEtag(existing.orElseThrow())))) {
      throw new UserSubscriptionConflictException();
    }
    String rawToken = tokenSupplier.get();
    Date updatedAt = new Date();
    CurrentItemCalendarSubscription saved;
    String action;
    if (existing.isPresent()) {
      try {
        saved =
            subscriptionDao.rotateCurrent(
                existing.get(),
                existing.get().tokenHash(),
                CryptoUtils.hashToken(rawToken),
                rawToken,
                updatedAt);
      } catch (OptimisticLockException | OptimisticLockingFailureException conflict) {
        SECURITY_LOG.warn(
            "Booking calendar subscription rotation conflicted for subject [{}], configuration"
                + " [{}]",
            subject.getUsername(),
            configurationId,
            conflict);
        throw new UserSubscriptionConflictException(conflict);
      }
      action = "replaced";
    } else {
      BookableItemCalendarSubscription subscription =
          new BookableItemCalendarSubscription(
              configuration, subject, CryptoUtils.hashToken(rawToken), rawToken, updatedAt);
      subscriptionDao.saveAndFlush(subscription);
      saved = subscriptionDao.reconcileCreated(subject.getId(), configurationId);
      action = "created";
    }
    SECURITY_LOG.info(
        "Booking calendar subscription {} by actor [{}] for subject [{}], configuration [{}],"
            + " subscription [{}]",
        action,
        subject.getUsername(),
        subject.getUsername(),
        configurationId,
        saved.id());
    String url = subscriptionUrl(rawToken);
    Status status = new Status(true, saved.updatedAt(), url, itemEtag(saved));
    return new Created(status, url, true);
  }

  private static String itemEtag(BookableItemCalendarSubscription subscription) {
    return "\"" + subscription.getTokenHash() + "\"";
  }

  private static String itemEtag(CurrentItemCalendarSubscription subscription) {
    return "\"" + subscription.tokenHash() + "\"";
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
        .map(this::userStatus)
        .orElseGet(() -> new Status(false, null, null, INACTIVE_USER_ETAG));
  }

  private Status userStatus(UserBookingCalendarSubscription subscription) {
    return new Status(
        true,
        subscription.getUpdatedAt(),
        subscriptionUrl(subscription.getRawToken()),
        userEtag(subscription));
  }

  @Override
  public Created createUser(User subject, User actor) {
    return createUserLink(subject, actor, null);
  }

  @Override
  public Created rotateUser(User subject, User actor, String expectedEtag) {
    Objects.requireNonNull(expectedEtag, "Expected ETag");
    return createUserLink(subject, actor, expectedEtag);
  }

  private Created createUserLink(User subject, User actor, String rotateFromEtag) {
    permissionGate.assertNoSuspendedAncestor();
    requirePersonalCaller(subject, actor);
    if (permissionGate.hasActiveGate()) {
      permissionGate.markRollbackOnly();
      throw new IllegalStateException(
          "User-wide calendar links cannot be created or rotated inside a gated transaction");
    }
    return creationTransaction.create(
        subject.getId(), freshSubject -> issueUserLink(freshSubject, actor, rotateFromEtag));
  }

  /** User-wide counterpart of {@link #issueItemLink}, serialized on the user row lock. */
  private Created issueUserLink(User subject, User actor, String rotateFromEtag) {
    requireFeatureMutation(subject);
    User lockedUser = userSubscriptionDao.lockUser(subject.getId());
    if (lockedUser == null) {
      throw new AuthorizationException("Active personal caller is required");
    }
    Optional<UserBookingCalendarSubscription> existing =
        userSubscriptionDao.findByUserId(subject.getId());
    if (rotateFromEtag == null && existing.isPresent()) {
      Status current = userStatus(existing.get());
      return new Created(current, current.subscriptionUrl(), false);
    }
    if (rotateFromEtag != null
        && (existing.isEmpty() || !Objects.equals(rotateFromEtag, userEtag(existing.get())))) {
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
    return new Created(new Status(true, saved.getUpdatedAt(), url, userEtag(saved)), url, true);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ItemLink> itemLinks(User subject, User actor) {
    requirePersonalCaller(subject, actor);
    requireFeatureRead(subject);
    List<BookableItemCalendarSubscription> eligible =
        subscriptionDao.findByUserId(subject.getId()).stream()
            .filter(subscription -> listable(subscription.getBookingConfiguration()))
            .toList();
    Map<Long, ResolvedResourceAccess> resolved =
        accessManager.resolveAll(
            eligible.stream()
                .map(BookableItemCalendarSubscription::getBookingConfiguration)
                .toList(),
            subject);
    List<BookableItemCalendarSubscription> listable =
        eligible.stream()
            .filter(
                subscription ->
                    resolved
                        .getOrDefault(
                            subscription.getBookingConfiguration().getId(),
                            ResolvedResourceAccess.none())
                        .hasCapability(BookingResourceRoleScheme.READ_RESOURCE))
            .toList();
    Map<Long, InstrumentReadSummary> instruments =
        instrumentDao.getBookingSummaries(
            listable.stream()
                .map(subscription -> subscription.getBookingConfiguration().getTarget().id())
                .collect(Collectors.toSet()));
    return listable.stream()
        .flatMap(
            subscription -> {
              Long targetId = subscription.getBookingConfiguration().getTarget().id();
              InstrumentReadSummary instrument = instruments.get(targetId);
              if (instrument == null || instrument.deleted()) {
                return Stream.empty();
              }
              return Stream.of(
                  new ItemLink(
                      subscription.getBookingConfiguration().getId(),
                      targetId,
                      instrument.name(),
                      subscription.getUpdatedAt(),
                      subscriptionUrl(subscription.getRawToken())));
            })
        .sorted(Comparator.comparing(ItemLink::itemName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  /** Filters configurations that cannot produce an item link before resolving permissions. */
  private boolean listable(BookingConfiguration configuration) {
    BookableTargetReference target = configuration.getTarget();
    return configuration.getState() != BookingConfigurationState.ARCHIVED
        && target != null
        && target.type() == BookableTargetType.INSTRUMENT
        && target.id() != null;
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
    return "\"subscription-" + subscription.getId() + "-" + subscription.getVersion() + "\"";
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
    return feedManager.download(bookingId, subject, locale);
  }

  @Override
  public FeedResult feed(String rawToken, Locale locale, Date refreshedAt) {
    return feedManager.feed(rawToken, locale, refreshedAt);
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
    return BookingCalendarAccessChecks.hasCapability(
        accessManager, configuration, subject, capability);
  }

  private void requireMutationCapability(
      BookingConfiguration configuration, User subject, String capability) {
    ResolvedResourceAccess access = accessManager.resolveForMutation(configuration, subject);
    if (!access.hasCapability(BookingResourceRoleScheme.READ_RESOURCE)) {
      throw new BookingCalendarNotFoundException();
    }
    if (!access.hasCapability(capability)) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private void requireFeatureRead(User subject) {
    BookingCalendarAccessChecks.requireFeatureRead(featureFlags, subject);
  }

  private void requireFeatureMutation(User subject) {
    requireActiveUser(subject);
    requireFeatureEnabled(subject);
  }

  private void requireFeatureEnabled(User subject) {
    if (!featureFlags.isFeatureFlagEnabled(BOOKING_ENABLED, subject)) {
      throw new BookingCalendarNotFoundException();
    }
  }

  private static void requirePersonalCaller(User subject, User actor) {
    requireActiveUser(subject);
    requireActiveUser(actor);
    if (!Objects.equals(subject.getId(), actor.getId())) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private static void requirePersonalIdentity(User subject, User actor) {
    if (subject == null
        || actor == null
        || subject.getId() == null
        || !Objects.equals(subject.getId(), actor.getId())) {
      throw new AuthorizationException("errors.api.v2.forbidden");
    }
  }

  private static void requireActiveUser(User user) {
    BookingCalendarAccessChecks.requireActiveUser(user);
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
