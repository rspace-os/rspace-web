package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.BookingConfigurationDefaultsDao;
import com.researchspace.booking.service.BookingConfigurationManager.Create;
import com.researchspace.booking.service.BookingConfigurationManager.Patch;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.booking.ApiV2BookingConfigurationResource;
import com.researchspace.model.booking.ApiV2BookingInstrumentResource;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationDefaults;
import com.researchspace.model.booking.BookingDefaultAccessGrantee;
import com.researchspace.model.booking.BookingDefaultSharedWith;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import com.researchspace.service.resourceaccess.ResourceAccessException;
import com.researchspace.service.resourceaccess.ResourceAccessManager;
import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class BookingConfigurationManagerTest {

  private final BookingConfigurationDao dao = mock(BookingConfigurationDao.class);
  private final BookingConfigurationDefaultsDao defaultsDao =
      mock(BookingConfigurationDefaultsDao.class);
  private final InstrumentDao instrumentDao = mock(InstrumentDao.class);
  private final BookingCalendarSubscriptionDao calendarSubscriptions =
      mock(BookingCalendarSubscriptionDao.class);

  private final User actor = mock(User.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final ObjectProvider<ResourceRegistry> resourceRegistry = mock(ObjectProvider.class);
  private final ResourceAccessManager accessManager = mock(ResourceAccessManager.class);
  private final LocalValidatorFactoryBean validator = validator();
  private final BookingConfigurationManager manager =
      new BookingConfigurationManagerImpl(
          dao,
          defaultsDao,
          instrumentDao,
          validator,
          events,
          resourceRegistry,
          ApiV2BookingConfigurationResource.DESCRIPTION,
          accessManager,
          new MessageSourceUtils(new JsonMessageSource()),
          calendarSubscriptions);

  @BeforeEach
  void setUp() {
    when(actor.hasSysadminRole()).thenReturn(true);
    when(actor.isEnabled()).thenReturn(true);
    when(actor.getId()).thenReturn(1L);
    when(actor.getDisplayName()).thenReturn("System Administrator");
    when(actor.getUsername()).thenReturn("sysadmin");
    when(defaultsDao.getSafeNull(BookingConfigurationDefaults.SINGLETON_ID))
        .thenReturn(Optional.of(defaults(5, "00:00", "24:00", 0, 0, 0, false)));
    when(instrumentDao.lockById(any()))
        .thenAnswer(invocation -> Optional.of(instrument(invocation.getArgument(0), actor)));
    when(resourceRegistry.getObject())
        .thenReturn(
            new ResourceRegistry(
                List.of(
                    ApiV2BookingConfigurationResource.DESCRIPTION,
                    ApiV2BookingInstrumentResource.DESCRIPTION,
                    ApiV2InstrumentResource.DESCRIPTION,
                    ApiV2UserResource.DESCRIPTION)));
    when(accessManager.resolve(nullable(ResourceAccess.class), eq(actor)))
        .thenReturn(
            new ResolvedResourceAccess(
                Optional.of(BookingResourceRoleScheme.OWNER),
                Set.of(
                    BookingResourceRoleScheme.READ_RESOURCE,
                    BookingResourceRoleScheme.EDIT_CONFIGURATION,
                    BookingResourceRoleScheme.ARCHIVE_CONFIGURATION),
                List.of()));
  }

  @AfterEach
  void closeValidatorFactory() {
    validator.close();
  }

  private static LocalValidatorFactoryBean validator() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.setValidationMessageSource(new JsonMessageSource());
    validator.afterPropertiesSet();
    return validator;
  }

  @Test
  void exposesTheServiceContractThroughAJdkProxy() {
    ProxyFactory proxyFactory = new ProxyFactory(manager);
    proxyFactory.setProxyTargetClass(false);

    assertInstanceOf(BookingConfigurationManager.class, proxyFactory.getProxy());
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
    when(dao.saveAndFlush(any(BookingConfiguration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

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
    assertEquals("00:00", created.getOpeningStart());
    assertEquals("24:00", created.getOpeningEnd());
    assertEquals(BookingResourceRoleScheme.SCHEME_KEY, created.getResourceAccess().getSchemeKey());
    assertEquals(2, created.getResourceAccess().getAssignments().size());
    assertTrue(
        created.getResourceAccess().getAssignments().stream()
            .anyMatch(
                assignment ->
                    assignment.getRoleKey().equals(BookingResourceRoleScheme.OWNER)
                        && assignment.getGranteeKey().equals("user:1")));
    assertTrue(
        created.getResourceAccess().getAssignments().stream()
            .anyMatch(
                assignment ->
                    assignment.getRoleKey().equals(BookingResourceRoleScheme.BOOKER)
                        && assignment.getGranteeKey().equals("audience:all-users")));
    verify(dao).saveAndFlush(created);
    verify(events).publishEvent(any(BookingConfigurationAuditEvent.class));
  }

  @Test
  void copiesOneDefaultsSnapshotForBulkCreateAndHonoursExplicitOverrides() {
    when(defaultsDao.getSafeNull(BookingConfigurationDefaults.SINGLETON_ID))
        .thenReturn(Optional.of(defaults(15, "08:00", "18:00", 10, 20, 120, true)));
    when(dao.saveAndFlush(any(BookingConfiguration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    List<BookingConfiguration> created =
        manager.createConfigurations(
            List.of(
                new Create(true, "UTC", target(12L)),
                new Create(
                    true,
                    "UTC",
                    target(13L),
                    new BookingSchedulingSettings.Patch(1L, null, null, 2L, null, 60L, false))),
            actor,
            actor);

    assertEquals(15, created.get(0).getSlotGranularityMinutes());
    assertEquals("08:00", created.get(0).getOpeningStart());
    assertEquals(10, created.get(0).getBufferBeforeMinutes());
    assertEquals(120, created.get(0).getMaxBookingDurationMinutes());
    assertTrue(created.get(0).isAllowDoubleBooking());
    assertEquals(1, created.get(1).getSlotGranularityMinutes());
    assertEquals("18:00", created.get(1).getOpeningEnd());
    assertEquals(2, created.get(1).getBufferBeforeMinutes());
    assertEquals(20, created.get(1).getBufferAfterMinutes());
    assertEquals(60, created.get(1).getMaxBookingDurationMinutes());
    assertFalse(created.get(1).isAllowDoubleBooking());
    verify(defaultsDao, times(1)).getSafeNull(BookingConfigurationDefaults.SINGLETON_ID);
  }

  @Test
  void existingConfigurationsDoNotChangeWhenDefaultsChange() {
    when(dao.saveAndFlush(any(BookingConfiguration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    BookingConfiguration first =
        manager.createConfiguration(new Create(true, "UTC", target(12L)), actor, actor);
    when(defaultsDao.getSafeNull(BookingConfigurationDefaults.SINGLETON_ID))
        .thenReturn(Optional.of(defaults(15, "08:00", "18:00", 10, 20, 120, true)));

    BookingConfiguration second =
        manager.createConfiguration(new Create(true, "UTC", target(13L)), actor, actor);

    assertEquals(5, first.getSlotGranularityMinutes());
    assertEquals("24:00", first.getOpeningEnd());
    assertFalse(first.isAllowDoubleBooking());
    assertEquals(0, first.getMaxBookingDurationMinutes());
    assertEquals(15, second.getSlotGranularityMinutes());
    assertEquals("18:00", second.getOpeningEnd());
    assertTrue(second.isAllowDoubleBooking());
    assertEquals(120, second.getMaxBookingDurationMinutes());
  }

  @Test
  void rejectsInvalidSchedulingSettingsBeforeSaving() {
    assertFalse(BookingSchedulingSettings.areOpeningHoursValid("08:00", "24:00"));
    Create invalid =
        new Create(
            true,
            "UTC",
            target(12L),
            new BookingSchedulingSettings.Patch(7L, "18:00", "08:00", -1L, 10_081L, 8L, false));

    InvalidBookingSchedulingSettingsException failure =
        assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () -> manager.createConfiguration(invalid, actor, actor));

    assertEquals(InvalidBookingSchedulingSettingsException.Reason.GRANULARITY, failure.reason());
    verify(dao, never()).saveAndFlush(any());
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

    InvalidBookingSchedulingSettingsException failure =
        assertThrows(
            InvalidBookingSchedulingSettingsException.class,
            () ->
                manager.createConfiguration(
                    new Create(
                        true,
                        "UTC",
                        target(12L),
                        new BookingSchedulingSettings.Patch(
                            null, null, null, null, null, 7L, null)),
                    actor,
                    actor));

    assertEquals(
        InvalidBookingSchedulingSettingsException.Reason.MAXIMUM_DURATION, failure.reason());
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void bulkCreateValidatesTheWholeBatchBeforeSavingAndPreservesOrder() {
    when(dao.saveAndFlush(any(BookingConfiguration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

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
        ConstraintViolationException.class,
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
    assertThrows(
        AuthorizationException.class,
        () ->
            manager.createConfiguration(
                new Create(true, "Europe/Berlin", target(12L)), actor, actor));

    when(actor.hasSysadminRole()).thenReturn(true);
    assertThrows(
        ConstraintViolationException.class,
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
    when(dao.lockById(42L)).thenReturn(Optional.of(configuration));
    when(dao.saveAndFlush(configuration)).thenReturn(configuration);

    BookingConfiguration updated =
        manager.updateConfiguration(42L, new Patch(true, null), actor, actor).orElseThrow();

    assertTrue(updated.isEnabled());
    assertEquals("UTC", updated.getTimeZone());
    assertEquals(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L), updated.getTarget());
    assertSame(actor, updated.getUpdatedBy());
    assertTrue(updated.getUpdatedAt() != null);
    verify(events).publishEvent(any(BookingConfigurationAuditEvent.class));
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
    assertFalse(manager.removeConfiguration(42L, actor, actor).isPresent());

    verify(dao, never()).save(any());
    verify(dao, never()).remove(any());
  }

  @Test
  void archivesAConfigurationInsteadOfDeletingItsReferencedRow() {
    BookingConfiguration configuration = configuration(42L, 12L);
    configuration.setEnabled(true);
    when(dao.lockById(42L)).thenReturn(Optional.of(configuration));
    when(dao.saveAndFlush(configuration)).thenReturn(configuration);

    BookingConfiguration removed = manager.removeConfiguration(42L, actor, actor).orElseThrow();

    assertSame(configuration, removed);
    assertTrue(removed.isDeleted());
    assertFalse(removed.isEnabled());
    assertSame(actor, removed.getUpdatedBy());
    assertTrue(removed.getUpdatedAt() != null);
    verify(dao).saveAndFlush(configuration);
    verify(dao, never()).remove(any());
    verify(calendarSubscriptions).deleteByConfigurationId(42L);
    verify(events).publishEvent(any(BookingConfigurationAuditEvent.class));
  }

  @Test
  void treatsAnArchivedConfigurationAsAbsent() {
    BookingConfiguration configuration = configuration(42L, 12L);
    configuration.setDeleted(true);
    when(dao.lockById(42L)).thenReturn(Optional.of(configuration));

    assertTrue(manager.getConfiguration(42L, actor).isEmpty());
    assertTrue(manager.updateConfiguration(42L, new Patch(true, null), actor, actor).isEmpty());
    assertTrue(manager.removeConfiguration(42L, actor, actor).isEmpty());

    verify(dao, never()).saveAndFlush(any());
    verify(dao, never()).remove(any());
  }

  @Test
  void bulkRemovalArchivesEveryMatchedConfiguration() {
    ResourceRequest request =
        ResourceRequest.unpaged(
            new FilterExpression.Comparison("id", Operator.IN, List.of(41L, 42L), false));
    BookingConfiguration first = configuration(41L, 11L);
    BookingConfiguration second = configuration(42L, 12L);
    first.setEnabled(true);
    second.setEnabled(true);
    int limit = ApiV2BookingConfigurationResource.MUTATION_LIMITS.maxBulkUpdateDeleteRows() + 1;
    when(dao.lockResources(eq(request), eq(limit), any())).thenReturn(List.of(first, second));
    when(dao.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    List<BookingConfiguration> removed = manager.removeConfigurations(request, actor, actor);

    assertEquals(List.of(first, second), removed);
    assertTrue(removed.stream().allMatch(BookingConfiguration::isDeleted));
    assertTrue(removed.stream().noneMatch(BookingConfiguration::isEnabled));
    assertTrue(removed.stream().allMatch(configuration -> configuration.getUpdatedBy() == actor));
    verify(dao, times(2)).saveAndFlush(any());
    verify(dao, never()).remove(any());
    verify(events, times(2)).publishEvent(any(BookingConfigurationAuditEvent.class));
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
    int oversizedBatch =
        ApiV2BookingConfigurationResource.MUTATION_LIMITS.maxBulkUpdateDeleteRows() + 1;
    when(dao.lockResources(eq(request), eq(oversizedBatch), any()))
        .thenReturn(Collections.nCopies(oversizedBatch, configuration));

    assertThrows(
        CollectionMutationException.class,
        () -> manager.updateConfigurations(request, new Patch(true, null), actor, actor));
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void explicitlyAllowsRelationshipTargetsForSystemAuthorizedBulkSelection() {
    ResourceRequest request =
        ResourceRequest.unpaged(
            new FilterExpression.Comparison(
                "target.name", Operator.EQUAL, List.of("Microscope"), false));
    int limit = ApiV2BookingConfigurationResource.MUTATION_LIMITS.maxBulkUpdateDeleteRows() + 1;
    ArgumentCaptor<RelationshipReadAccess> access =
        ArgumentCaptor.forClass(RelationshipReadAccess.class);
    when(dao.lockResources(eq(request), eq(limit), any())).thenReturn(List.of());

    manager.removeConfigurations(request, actor, actor);

    verify(dao).lockResources(eq(request), eq(limit), access.capture());
    assertFalse(access.getValue().result("booking-instruments").isDenied());
    assertTrue(access.getValue().result("booking-instruments").constraintOrEmpty().isEmpty());
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
  void selectedDefaultsCannotCreateMoreThanOneHundredNamedAssignments() {
    BookingConfigurationDefaults defaults = defaults(5, "00:00", "24:00", 0, 0, 0, false);
    defaults.setDefaultSharedWith(BookingDefaultSharedWith.SELECTED);
    for (long id = 2; id <= 101; id++) {
      User selected = mock(User.class);
      when(selected.getId()).thenReturn(id);
      when(selected.getDisplayName()).thenReturn("Selected " + id);
      when(selected.getUsername()).thenReturn("selected-" + id);
      defaults.addSelectedAccessGrantee(BookingDefaultAccessGrantee.forUser(selected));
    }
    when(defaultsDao.getSafeNull(BookingConfigurationDefaults.SINGLETON_ID))
        .thenReturn(Optional.of(defaults));

    ResourceAccessException error =
        assertThrows(
            ResourceAccessException.class,
            () -> manager.createConfiguration(new Create(true, "UTC", target(12L)), actor, actor));

    assertEquals(ResourceAccessException.Reason.ASSIGNMENT_LIMIT, error.reason());
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

  private static ResolvedBookableTarget target(long id) {
    Instrument instrument = instrument(id, null);
    return new ResolvedBookableTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, id), instrument);
  }

  private static Instrument instrument(long id, User owner) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    instrument.setOwner(owner);
    return instrument;
  }

  private BookingConfiguration configuration(long id, long targetId) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(id);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, targetId));
    ResourceAccess access =
        new ResourceAccess(BookingResourceRoleScheme.SCHEME_KEY, null, new java.util.Date());
    access.setId(targetId);
    access.addAssignment(ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, actor));
    configuration.setResourceAccess(access);
    return configuration;
  }

  private static BookingConfigurationDefaults defaults(
      long granularity,
      String openingStart,
      String openingEnd,
      long before,
      long after,
      long maximumDuration,
      boolean doubleBooking) {
    BookingConfigurationDefaults defaults = new BookingConfigurationDefaults();
    defaults.setId(BookingConfigurationDefaults.SINGLETON_ID);
    defaults.setSlotGranularityMinutes(granularity);
    defaults.setOpeningStart(openingStart);
    defaults.setOpeningEnd(openingEnd);
    defaults.setBufferBeforeMinutes(before);
    defaults.setBufferAfterMinutes(after);
    defaults.setMaxBookingDurationMinutes(maximumDuration);
    defaults.setAllowDoubleBooking(doubleBooking);
    return defaults;
  }
}
