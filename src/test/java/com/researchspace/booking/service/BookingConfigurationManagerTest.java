package com.researchspace.booking.service;

import static com.researchspace.booking.service.BookingConfigurationManagerTestFixtures.OVERSIZED_MUTATION_LIMIT;
import static com.researchspace.booking.service.BookingConfigurationManagerTestFixtures.defaults;
import static com.researchspace.booking.service.BookingConfigurationManagerTestFixtures.instrument;
import static com.researchspace.booking.service.BookingConfigurationManagerTestFixtures.target;
import static com.researchspace.testutils.BookingTestSchedules.hours;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.booking.api.v2.BookingConfigurationResourceOperations;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingConfigurationDefaultsDao;
import com.researchspace.booking.service.BookingConfigurationManager.Create;
import com.researchspace.booking.service.BookingConfigurationManager.Patch;
import com.researchspace.dao.AuditDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.featureflags.FeatureFlags;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingOpeningHoursEntry;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.WriteOperation;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.service.FeatureFlagManager;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.testutils.BookingResourceTestFixtures;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class BookingConfigurationManagerTest {

  private final BookingConfigurationManagerTestFixtures fixture =
      new BookingConfigurationManagerTestFixtures();
  private final AuditDao auditDao = fixture.auditDao;
  private final BookingConfigurationDao dao = fixture.dao;
  private final BookingConfigurationDefaultsDao defaultsDao = fixture.defaultsDao;
  private final InstrumentDao instrumentDao = fixture.instrumentDao;
  private final User actor = fixture.actor;
  private final ApplicationEventPublisher events = fixture.events;
  private final BookingItemPermissions itemPermissions = fixture.itemPermissions;
  private final LocalValidatorFactoryBean validator = fixture.validator;
  private final BookingConfigurationManager manager = fixture.manager;
  private final BookingNotificationSubscriptionManager notificationSubscriptions =
      fixture.notificationSubscriptions;

  @BeforeEach
  void setUp() {
    fixture.setUp();
  }

  @AfterEach
  void closeValidatorFactory() {
    validator.close();
  }

  @Test
  void onlySysadminCanResolveDeletedConfigurationForAudit() {
    BookingConfiguration deleted = configuration(42L, 11L);
    when(auditDao.getNewestRevisionForEntity(BookingConfiguration.class, 42L))
        .thenReturn(new AuditedEntity<>(deleted, 1));

    assertEquals(Optional.of(deleted), manager.getConfigurationForAudit(42L, actor));
    assertTrue(manager.getConfiguration(42L, actor).isEmpty());

    when(actor.hasSysadminRole()).thenReturn(false);
    assertTrue(manager.getConfigurationForAudit(42L, actor).isEmpty());
    verify(auditDao, times(1)).getNewestRevisionForEntity(BookingConfiguration.class, 42L);
  }

  @Test
  void auditDoesNotRecoverAnUnreadableLiveConfiguration() {
    BookingConfiguration unreadable = configuration(42L, 11L);
    when(dao.getSafeNull(42L)).thenReturn(Optional.of(unreadable));

    assertTrue(manager.getConfigurationForAudit(42L, actor).isEmpty());
    verify(auditDao, never()).getNewestRevisionForEntity(any(), any());
  }

  @Test
  void appliesTargetVisibilityOnlyToRelationshipFilters() {
    when(actor.hasSysadminRole()).thenReturn(false);
    BookingConfiguration readable = configuration(1L, 11L);
    ResourceRequest request = ResourceRequest.unpaged(null);
    when(dao.getResources(any(), any())).thenReturn(new ResourcePage<>(List.of(readable), 1));
    when(dao.countResources(any(), any())).thenReturn(1L);
    when(dao.getSafeNull(2L)).thenReturn(Optional.empty());

    ResourcePage<BookingConfiguration> page = manager.getConfigurations(request, actor);

    assertEquals(List.of(readable), page.resources());
    assertEquals(1, page.total());
    assertEquals(1, manager.countConfigurations(request, actor));
    assertTrue(manager.getConfiguration(2L, actor).isEmpty());
  }

  @Test
  void directBookingReadsRequireAnActor() {
    ResourceRequest request = ResourceRequest.unpaged(null);

    assertThrows(AuthorizationException.class, () -> manager.getConfigurations(request, null));
    assertThrows(AuthorizationException.class, () -> manager.countConfigurations(request, null));
    assertThrows(AuthorizationException.class, () -> manager.getConfiguration(1L, null));
  }

  @Test
  void directServiceCreatesUseSharedAuthorizationAndValidation() {
    BookingConfiguration created =
        manager.createConfiguration(new Create(true, "Europe/Berlin", target(12L)), actor, actor);

    assertTrue(created.isEnabled());
    assertEquals("Europe/Berlin", created.getTimeZone());
    assertEquals(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L), created.getTarget());
    assertSame(actor, created.getCreatedBy());
    assertSame(actor, created.getUpdatedBy());
    assertEquals(created.getCreatedAt(), created.getUpdatedAt());
    assertEquals(5, created.getSlotGranularityMinutes());
    assertEquals(BookingSchedulingSettings.DEFAULT_OPENING_HOURS, created.getOpeningHours());
    InOrder createOrder = inOrder(dao, notificationSubscriptions);
    createOrder.verify(dao).saveAndFlush(created);
    createOrder.verify(notificationSubscriptions).initializeForInstrument(any(Instrument.class));
    verify(events).publishEvent(any(BookingConfigurationAuditEvent.class));
  }

  @Test
  void persistsValidTimeZoneAliasesVerbatim() {
    List<String> timeZones = List.of("Asia/Calcutta", "UTC", "Etc/UTC", "US/Eastern", "Etc/GMT+1");
    long targetId = 20L;

    for (String timeZone : timeZones) {
      BookingConfiguration created =
          manager.createConfiguration(new Create(true, timeZone, target(targetId++)), actor, actor);

      assertEquals(timeZone, created.getTimeZone());
    }
  }

  @Test
  void unsupportedInstitutionClockDefaultIsRejectedByTheSharedManagerBeforeSaving() {
    FeatureFlagManager featureFlags = mock(FeatureFlagManager.class);
    when(featureFlags.isFeatureFlagEnabled(FeatureFlags.BOOKING_ENABLED, actor)).thenReturn(true);
    BookingConfigurationResourceOperations operations =
        new BookingConfigurationResourceOperations(
            manager,
            mock(BookingConfigurationProtectedResourceAccess.class),
            featureFlags,
            Clock.fixed(Instant.EPOCH, ZoneId.of("CST6CDT")),
            ApiV2BookingConfigurationResource.DESCRIPTION);
    ParsedDocument document =
        new ParsedDocument(
            WriteOperation.CREATE, Map.of("target", BookingResourceTestFixtures.resolved(12L)));

    InvalidBookingSchedulingSettingsException failure =
        assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () -> operations.create(document, ApiV2Caller.direct(actor)));

    assertEquals("errors.api.v2.bookingConfiguration.timeZone.invalid", failure.getMessage());
    assertEquals(InvalidBookingSchedulingSettingsException.Reason.TIME_ZONE, failure.reason());
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void copiesOneDefaultsSnapshotForBulkCreateAndHonoursExplicitOverrides() {
    BookingConfigurationDefaults defaults = defaults(15, "08:00", "18:00", 10, 20, 120, true);
    defaults.setOpeningHours(
        List.of(
            new BookingOpeningHoursEntry(1, "09:00", "17:00"),
            new BookingOpeningHoursEntry(2, "10:00", "16:00")));
    when(defaultsDao.getSafeNull(BookingConfigurationDefaults.SINGLETON_ID))
        .thenReturn(Optional.of(defaults));

    List<BookingConfiguration> created =
        manager.createConfigurations(
            List.of(
                new Create(true, "UTC", target(12L)),
                new Create(
                    true,
                    "UTC",
                    target(13L),
                    new BookingSchedulingSettings.Patch(
                        1L, hours("18:00", "24:00", 5), 2L, null, 60L, false))),
            actor,
            actor);

    assertEquals(15, created.get(0).getSlotGranularityMinutes());
    assertEquals(defaults.getOpeningHours(), created.get(0).getOpeningHours());
    assertEquals(10, created.get(0).getBufferBeforeMinutes());
    assertEquals(120, created.get(0).getMaxBookingDurationMinutes());
    assertTrue(created.get(0).isAllowDoubleBooking());
    assertEquals(1, created.get(1).getSlotGranularityMinutes());
    assertEquals(hours("18:00", "24:00", 5), created.get(1).getOpeningHours());
    assertEquals(2, created.get(1).getBufferBeforeMinutes());
    assertEquals(20, created.get(1).getBufferAfterMinutes());
    assertEquals(60, created.get(1).getMaxBookingDurationMinutes());
    assertFalse(created.get(1).isAllowDoubleBooking());
    verify(defaultsDao, times(1)).getSafeNull(BookingConfigurationDefaults.SINGLETON_ID);

    defaults.setOpeningHours(hours("00:00", "24:00"));
    assertEquals(
        List.of(
            new BookingOpeningHoursEntry(1, "09:00", "17:00"),
            new BookingOpeningHoursEntry(2, "10:00", "16:00")),
        created.get(0).getOpeningHours());
  }

  @Test
  void existingConfigurationsDoNotChangeWhenDefaultsChange() {
    BookingConfiguration first =
        manager.createConfiguration(new Create(true, "UTC", target(12L)), actor, actor);
    when(defaultsDao.getSafeNull(BookingConfigurationDefaults.SINGLETON_ID))
        .thenReturn(Optional.of(defaults(15, "08:00", "18:00", 10, 20, 120, true)));

    BookingConfiguration second =
        manager.createConfiguration(new Create(true, "UTC", target(13L)), actor, actor);

    assertEquals(5, first.getSlotGranularityMinutes());
    assertEquals(BookingSchedulingSettings.DEFAULT_OPENING_HOURS, first.getOpeningHours());
    assertFalse(first.isAllowDoubleBooking());
    assertEquals(0, first.getMaxBookingDurationMinutes());
    assertEquals(15, second.getSlotGranularityMinutes());
    assertEquals(hours("08:00", "18:00"), second.getOpeningHours());
    assertTrue(second.isAllowDoubleBooking());
    assertEquals(120, second.getMaxBookingDurationMinutes());
  }

  @ParameterizedTest
  @MethodSource("invalidSchedulingCreates")
  void rejectsInvalidSchedulingSettingsBeforeSaving(
      Create invalid, InvalidBookingSchedulingSettingsException.Reason reason) {
    assertTrue(BookingSchedulingSettings.isOpeningIntervalValid("08:00", "24:00"));
    InvalidBookingSchedulingSettingsException failure =
        assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () -> manager.createConfiguration(invalid, actor, actor));

    assertEquals(reason, failure.reason());
    verify(dao, never()).saveAndFlush(any());
  }

  private static Stream<Arguments> invalidSchedulingCreates() {
    return Stream.of(
        Arguments.of(
            new Create(
                true,
                "UTC",
                target(12L),
                new BookingSchedulingSettings.Patch(
                    0L, hours("18:00", "08:00"), -1L, 10_081L, 8L, false)),
            InvalidBookingSchedulingSettingsException.Reason.GRANULARITY),
        Arguments.of(
            new Create(
                true,
                "UTC",
                target(12L),
                new BookingSchedulingSettings.Patch(null, null, null, null, 7L, null)),
            InvalidBookingSchedulingSettingsException.Reason.MAXIMUM_DURATION),
        Arguments.of(
            new Create(
                true,
                "UTC",
                target(12L),
                new BookingSchedulingSettings.Patch(null, List.of(), null, null, null, null)),
            InvalidBookingSchedulingSettingsException.Reason.OPENING_HOURS));
  }

  @ParameterizedTest
  @MethodSource("invalidTimeZoneCreates")
  void rejectsUnsupportedTimeZoneCreatesWithControlledValidation(String timeZone) {
    InvalidBookingSchedulingSettingsException failure =
        assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () ->
                manager.createConfiguration(new Create(true, timeZone, target(12L)), actor, actor));

    assertEquals("errors.api.v2.bookingConfiguration.timeZone.invalid", failure.getMessage());
    assertEquals(InvalidBookingSchedulingSettingsException.Reason.TIME_ZONE, failure.reason());
    verify(dao, never()).saveAndFlush(any());
  }

  private static Stream<Arguments> invalidTimeZoneCreates() {
    return Stream.of(
        Arguments.of("UT"),
        Arguments.of("UTC+01"),
        Arguments.of("GMT+01"),
        Arguments.of("UTC+01:00"),
        Arguments.of("GMT+01:00"),
        Arguments.of("Z"),
        Arguments.of("+01"),
        Arguments.of("+0100"),
        Arguments.of("+01:00"),
        Arguments.of("SystemV/CST6CDT"),
        Arguments.of("CST6CDT"),
        Arguments.of("EST5EDT"),
        Arguments.of("MST7MDT"),
        Arguments.of("PST8PDT"),
        Arguments.of(""),
        Arguments.of(" \t"),
        Arguments.of((Object) null));
  }

  @Test
  void validatesMaximumDurationAgainstTheSystemCapAndGranularity() {
    assertTrue(BookingSchedulingSettings.isGranularityValid(1));
    assertTrue(BookingSchedulingSettings.isGranularityValid(5));
    assertTrue(BookingSchedulingSettings.isGranularityValid(10));
    assertTrue(BookingSchedulingSettings.isGranularityValid(15));
    assertFalse(BookingSchedulingSettings.isGranularityValid(7));
    assertTrue(BookingSchedulingSettings.isMaximumDurationValid(0, 5));
    assertTrue(BookingSchedulingSettings.isMaximumDurationValid(5, 5));
    assertTrue(BookingSchedulingSettings.isMaximumDurationValid(527_040, 15));
    assertFalse(BookingSchedulingSettings.isMaximumDurationValid(-1, 5));
    assertFalse(BookingSchedulingSettings.isMaximumDurationValid(4, 5));
    assertFalse(BookingSchedulingSettings.isMaximumDurationValid(7, 5));
    assertFalse(BookingSchedulingSettings.isMaximumDurationValid(527_041, 1));
    assertFalse(BookingSchedulingSettings.isMaximumDurationValid(5, 0));
  }

  @Test
  void bulkCreateValidatesTheWholeBatchBeforeSavingAndPreservesOrder() {
    List<BookingConfiguration> created =
        manager.createConfigurations(
            List.of(
                new Create(true, "Europe/Berlin", target(12L)),
                new Create(false, "UTC", target(13L))),
            actor,
            actor);

    assertEquals(2, created.size());
    assertEquals(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L),
        created.get(0).getTarget());
    assertEquals(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 13L),
        created.get(1).getTarget());
    verify(dao).saveAndFlush(created.get(0));
    verify(dao).saveAndFlush(created.get(1));

    assertThrows(
        InvalidBookingSchedulingSettingsException.class,
        () ->
            manager.createConfigurations(
                List.of(
                    new Create(true, "UTC", target(14L)),
                    new Create(true, "Not/A_Zone", target(15L))),
                actor,
                actor));
    verify(dao, never())
        .findByTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 14L));
  }

  @Test
  void mixedBulkCreateRejectsUnsupportedTimeZoneBeforeSavingAnyConfiguration() {
    InvalidBookingSchedulingSettingsException failure =
        assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () ->
                manager.createConfigurations(
                    List.of(
                        new Create(true, "Europe/Berlin", target(12L)),
                        new Create(true, "UTC+01:00", target(13L))),
                    actor,
                    actor));

    assertEquals("errors.api.v2.bookingConfiguration.timeZone.invalid", failure.getMessage());
    assertEquals(InvalidBookingSchedulingSettingsException.Reason.TIME_ZONE, failure.reason());
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void bulkCreateRejectsDuplicateTargetsBeforeSaving() {
    assertThrows(
        BookingConfigurationTargetConflictException.class,
        () ->
            manager.createConfigurations(
                List.of(
                    new Create(true, "UTC", target(12L)),
                    new Create(false, "Europe/Berlin", target(12L))),
                actor,
                actor));

    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void rejectsUnauthorizedAndInvalidDirectServiceWritesBeforeSaving() {
    User owner = mock(User.class);
    when(owner.getId()).thenReturn(2L);
    when(instrumentDao.lockById(12L)).thenReturn(Optional.of(instrument(12L, owner)));
    when(actor.hasSysadminRole()).thenReturn(false);
    when(itemPermissions.resolveForMutation(any(BookingConfiguration.class), eq(actor)))
        .thenReturn(ResolvedResourceAccess.none())
        .thenReturn(
            new ResolvedResourceAccess(
                Optional.of(BookingResourceRoleScheme.OWNER),
                Set.of(BookingResourceRoleScheme.EDIT_CONFIGURATION),
                List.of()));
    assertThrows(
        AuthorizationException.class,
        () ->
            manager.createConfiguration(
                new Create(true, "Europe/Berlin", target(12L)), actor, actor));

    when(actor.hasSysadminRole()).thenReturn(true);
    assertThrows(
        InvalidBookingSchedulingSettingsException.class,
        () ->
            manager.createConfiguration(new Create(true, "Not/A_Zone", target(12L)), actor, actor));

    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void rechecksTargetOwnershipFromTheLockedInstrument() {
    User formerOwner = mock(User.class);
    User currentOwner = mock(User.class);
    when(formerOwner.getId()).thenReturn(1L);
    when(formerOwner.isEnabled()).thenReturn(true);
    when(formerOwner.hasSysadminRole()).thenReturn(false);
    when(currentOwner.getId()).thenReturn(2L);
    Instrument staleRelationshipTarget = instrument(12L, formerOwner);
    Instrument lockedTarget = instrument(12L, currentOwner);
    when(itemPermissions.resolveForMutation(any(BookingConfiguration.class), eq(formerOwner)))
        .thenReturn(ResolvedResourceAccess.none());
    when(instrumentDao.lockById(12L)).thenReturn(Optional.of(lockedTarget));

    assertThrows(
        AuthorizationException.class,
        () ->
            manager.createConfiguration(
                new Create(
                    true,
                    "UTC",
                    new ResolvedBookableTarget(
                        new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L),
                        staleRelationshipTarget)),
                formerOwner,
                formerOwner));

    verify(instrumentDao).lockById(12L);
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void directServicePatchesPreserveFieldsThatWereNotProvided() {
    BookingConfiguration configuration = configuration(42L, 12L);
    configuration.setTimeZone("Europe/Berlin");
    List<BookingOpeningHoursEntry> openingHours = hours("09:00", "17:00", 1, 3);
    configuration.setOpeningHours(openingHours);
    when(dao.lockById(42L)).thenReturn(Optional.of(configuration));

    BookingConfiguration updated =
        manager
            .updateConfiguration(
                42L,
                new Patch(
                    true,
                    null,
                    new BookingSchedulingSettings.Patch(10L, null, null, null, null, null)),
                actor,
                actor)
            .orElseThrow();

    assertTrue(updated.isEnabled());
    assertEquals(10, updated.getSlotGranularityMinutes());
    assertEquals(openingHours, updated.getOpeningHours());
    assertEquals("Europe/Berlin", updated.getTimeZone());
    assertEquals(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L), updated.getTarget());
    assertSame(actor, updated.getUpdatedBy());
    assertTrue(updated.getUpdatedAt() != null);
    verify(itemPermissions).resolveForMutation(configuration, actor);
    verify(events).publishEvent(any(BookingConfigurationAuditEvent.class));
  }

  @Test
  void rejectsChangedUnsupportedTimeZoneBeforeApplyingOrSavingDirectServicePatch() {
    BookingConfiguration configuration = configuration(42L, 12L);
    configuration.setEnabled(true);
    configuration.setTimeZone("Europe/Berlin");
    when(dao.lockById(42L)).thenReturn(Optional.of(configuration));

    InvalidBookingSchedulingSettingsException failure =
        assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () -> manager.updateConfiguration(42L, new Patch(false, "UTC+01:00"), actor, actor));

    assertEquals("errors.api.v2.bookingConfiguration.timeZone.invalid", failure.getMessage());
    assertEquals(InvalidBookingSchedulingSettingsException.Reason.TIME_ZONE, failure.reason());
    assertTrue(configuration.isEnabled());
    assertEquals("Europe/Berlin", configuration.getTimeZone());
    verify(dao, never()).saveAndFlush(any());
    verify(events, never()).publishEvent(any());
  }

  @Test
  void acceptsChangedValidTimeZoneAliasWithoutCanonicalizingIt() {
    BookingConfiguration configuration = configuration(42L, 12L);
    configuration.setTimeZone("Europe/Berlin");
    when(dao.lockById(42L)).thenReturn(Optional.of(configuration));

    BookingConfiguration updated =
        manager
            .updateConfiguration(42L, new Patch(null, "Asia/Calcutta"), actor, actor)
            .orElseThrow();

    assertEquals("Asia/Calcutta", updated.getTimeZone());
    verify(dao).saveAndFlush(updated);
  }

  @Test
  void bulkCreateRejectsOversizedBatchBeforeValidationOrPersistence() {
    Create create = new Create(true, "UTC", target(12L));

    assertThrows(
        CollectionMutationException.class,
        () ->
            manager.createConfigurations(
                Collections.nCopies(CollectionMutationLimits.MAX_BULK_CREATE_ROWS + 1, create),
                actor,
                actor));

    verify(dao, never()).findByTarget(any());
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void reportsAnAbsentConfigurationWithoutWriting() {
    when(dao.lockById(42L)).thenReturn(Optional.empty());

    assertFalse(manager.updateConfiguration(42L, new Patch(true, null), actor, actor).isPresent());
    assertFalse(manager.archiveConfiguration(42L, 0L, actor, actor).isPresent());

    verify(dao, never()).save(any());
    verify(dao, never()).remove(any());
  }

  @Test
  void rejectsUnauthorizedAndOversizedBulkWritesBeforeChangingRows() {
    ResourceRequest request =
        ResourceRequest.unpaged(
            new FilterExpression.Comparison("id", Operator.EQUAL, List.of(42L), false));
    when(actor.hasSysadminRole()).thenReturn(false);

    assertThrows(
        AuthorizationException.class,
        () -> manager.updateConfigurations(request, new Patch(true, null), actor, actor));
    verify(dao, never()).lockResources(any(ResourceRequest.class), anyInt(), any());

    when(actor.hasSysadminRole()).thenReturn(true);
    BookingConfiguration configuration = new BookingConfiguration();
    int oversizedBatch = OVERSIZED_MUTATION_LIMIT;
    when(dao.lockResources(eq(request), eq(oversizedBatch), any()))
        .thenReturn(Collections.nCopies(oversizedBatch, configuration));

    assertThrows(
        CollectionMutationException.class,
        () -> manager.updateConfigurations(request, new Patch(true, null), actor, actor));
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void rejectsDeletedTargetsBeforeSaving() {
    Instrument deleted = new Instrument();
    deleted.setId(12L);
    deleted.setRecordDeleted(true);
    when(instrumentDao.lockById(12L)).thenReturn(Optional.of(deleted));
    ResolvedBookableTarget target =
        new ResolvedBookableTarget(
            new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L), deleted);

    assertThrows(
        InvalidBookableTargetException.class,
        () -> manager.createConfiguration(new Create(true, "UTC", target), actor, actor));
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void mapsOnlyTheTargetUniqueConstraintRaceToAConflict() {
    DataIntegrityViolationException targetRace =
        new DataIntegrityViolationException(
            "write failed", new RuntimeException("UK_BookingConfiguration_target"));
    when(dao.saveAndFlush(any())).thenThrow(targetRace);

    assertThrows(
        BookingConfigurationTargetConflictException.class,
        () -> manager.createConfiguration(new Create(true, "UTC", target(12L)), actor, actor));

    DataIntegrityViolationException otherFailure =
        new DataIntegrityViolationException("another constraint");
    doThrow(otherFailure).when(dao).saveAndFlush(any());

    assertSame(
        otherFailure,
        assertThrows(
            DataIntegrityViolationException.class,
            () -> manager.createConfiguration(new Create(true, "UTC", target(13L)), actor, actor)));
  }

  private BookingConfiguration configuration(long id, long targetId) {
    return fixture.configuration(id, targetId);
  }

  @Test
  void rejectsAnEmptyReplacementBeforeSaving() {
    Create invalid =
        new Create(
            true,
            "UTC",
            target(12L),
            new BookingSchedulingSettings.Patch(null, List.of(), null, null, null, null));
    InvalidBookingSchedulingSettingsException failure =
        assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () -> manager.createConfiguration(invalid, actor, actor));
    assertEquals(InvalidBookingSchedulingSettingsException.Reason.OPENING_HOURS, failure.reason());
    verify(dao, never()).saveAndFlush(any(BookingConfiguration.class));
  }
}
