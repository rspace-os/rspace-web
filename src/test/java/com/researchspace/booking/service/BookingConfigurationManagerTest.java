package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.service.BookingConfigurationManager.Create;
import com.researchspace.booking.service.BookingConfigurationManager.Patch;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionMutationLimits;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.service.CollectionMutationException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

class BookingConfigurationManagerTest {

  @SuppressWarnings("unchecked")
  private final BookingConfigurationDao dao = mock(BookingConfigurationDao.class);

  private final User actor = mock(User.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
  private final BookingConfigurationManager manager =
      new BookingConfigurationManager(dao, validatorFactory.getValidator(), events);

  @BeforeEach
  void setUp() {
    when(actor.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    when(actor.getUsername()).thenReturn("sysadmin");
  }

  @AfterEach
  void closeValidatorFactory() {
    validatorFactory.close();
  }

  @Test
  void directServiceCreatesUseSharedAuthorizationAndValidation() {
    when(dao.saveAndFlush(any(BookingConfiguration.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    BookingConfiguration created =
        manager.createConfiguration(new Create(true, "Europe/Berlin", target(12L)), actor);

    assertTrue(created.isEnabled());
    assertEquals("Europe/Berlin", created.getTimeZone());
    assertEquals(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L), created.getTarget());
    assertSame(actor, created.getCreatedBy());
    assertSame(actor, created.getUpdatedBy());
    assertEquals(created.getCreatedAt(), created.getUpdatedAt());
    verify(dao).saveAndFlush(created);
    verify(events).publishEvent(any(BookingConfigurationAuditEvent.class));
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
                actor));

    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void rejectsUnauthorizedAndInvalidDirectServiceWritesBeforeSaving() {
    when(actor.hasRole(Role.SYSTEM_ROLE)).thenReturn(false);
    assertThrows(
        AuthorizationException.class,
        () -> manager.createConfiguration(new Create(true, "Europe/Berlin", target(12L)), actor));

    when(actor.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    assertThrows(
        ConstraintViolationException.class,
        () -> manager.createConfiguration(new Create(true, "Not/A_Zone", target(12L)), actor));

    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void directServicePatchesPreserveFieldsThatWereNotProvided() {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L));
    when(dao.getSafeNull(42L)).thenReturn(Optional.of(configuration));
    when(dao.saveAndFlush(configuration)).thenReturn(configuration);

    BookingConfiguration updated =
        manager.updateConfiguration(42L, new Patch(true, null, null), actor).orElseThrow();

    assertTrue(updated.isEnabled());
    assertEquals("UTC", updated.getTimeZone());
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
                actor));

    verify(dao, never()).findByTarget(any());
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void reportsAnAbsentConfigurationWithoutWriting() {
    when(dao.getSafeNull(42L)).thenReturn(Optional.empty());

    assertFalse(manager.updateConfiguration(42L, new Patch(true, null, null), actor).isPresent());
    assertFalse(manager.removeConfiguration(42L, actor).isPresent());

    verify(dao, never()).save(any());
    verify(dao, never()).remove(any());
  }

  @Test
  void rejectsUnauthorizedAndOversizedBulkWritesBeforeChangingRows() {
    ResourceRequest request =
        ResourceRequest.unpaged(
            new FilterExpression.Comparison("id", Operator.EQUAL, List.of(42L), false));
    when(actor.hasRole(Role.SYSTEM_ROLE)).thenReturn(false);

    assertThrows(
        AuthorizationException.class,
        () -> manager.updateConfigurations(request, new Patch(true, null, null), actor));
    verify(dao, never()).getResources(any(ResourceRequest.class), anyInt());

    when(actor.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
    BookingConfiguration configuration = new BookingConfiguration();
    int oversizedBatch = CollectionMutationLimits.MAX_BULK_UPDATE_DELETE_ROWS + 1;
    when(dao.getResources(request, oversizedBatch))
        .thenReturn(Collections.nCopies(oversizedBatch, configuration));

    assertThrows(
        CollectionMutationException.class,
        () -> manager.updateConfigurations(request, new Patch(true, null, null), actor));
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void replacesTheTargetAtomicallyAndRejectsTargetsUsedByAnotherConfiguration() {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(42L);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L));
    when(dao.getSafeNull(42L)).thenReturn(Optional.of(configuration));
    when(dao.saveAndFlush(configuration)).thenReturn(configuration);

    BookingConfiguration updated =
        manager.updateConfiguration(42L, new Patch(null, null, target(13L)), actor).orElseThrow();

    assertEquals(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 13L), updated.getTarget());

    BookingConfiguration other = new BookingConfiguration();
    other.setId(99L);
    when(dao.findByTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 14L)))
        .thenReturn(Optional.of(other));

    assertThrows(
        BookingConfigurationTargetConflictException.class,
        () -> manager.updateConfiguration(42L, new Patch(null, null, target(14L)), actor));
  }

  @Test
  void rejectsDeletedTargetsBeforeSaving() {
    Instrument deleted = new Instrument();
    deleted.setId(12L);
    deleted.setRecordDeleted(true);
    ResolvedBookableTarget target =
        new ResolvedBookableTarget(
            new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L), deleted);

    assertThrows(
        InvalidBookableTargetException.class,
        () -> manager.createConfiguration(new Create(true, "UTC", target), actor));
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void doesNotSaveWhenTheOnlyPatchValueIsTheCurrentTarget() {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(42L);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L));
    when(dao.getSafeNull(42L)).thenReturn(Optional.of(configuration));

    BookingConfiguration result =
        manager.updateConfiguration(42L, new Patch(null, null, target(12L)), actor).orElseThrow();

    assertEquals(configuration, result);
    verify(dao, never()).saveAndFlush(any());
  }

  @Test
  void bulkTargetPatchRequiresAtMostOneMatchingConfiguration() {
    ResourceRequest request =
        ResourceRequest.unpaged(
            new FilterExpression.Comparison("id", Operator.IN, List.of(1L, 2L), false));
    BookingConfiguration first = configuration(1L, 11L);
    BookingConfiguration second = configuration(2L, 12L);
    when(dao.getResources(request, CollectionMutationLimits.MAX_BULK_UPDATE_DELETE_ROWS + 1))
        .thenReturn(List.of(first, second));

    assertThrows(
        BookingConfigurationTargetConflictException.class,
        () -> manager.updateConfigurations(request, new Patch(null, null, target(13L)), actor));

    assertEquals(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 11L), first.getTarget());
    assertEquals(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, 12L), second.getTarget());
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
        () -> manager.createConfiguration(new Create(true, "UTC", target(12L)), actor));

    DataIntegrityViolationException otherFailure =
        new DataIntegrityViolationException("another constraint");
    doThrow(otherFailure).when(dao).saveAndFlush(any());

    assertSame(
        otherFailure,
        assertThrows(
            DataIntegrityViolationException.class,
            () -> manager.createConfiguration(new Create(true, "UTC", target(13L)), actor)));
  }

  private static ResolvedBookableTarget target(long id) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    return new ResolvedBookableTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, id), instrument);
  }

  private static BookingConfiguration configuration(long id, long targetId) {
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setId(id);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(
        new BookableTargetReference(BookableTargetType.INSTRUMENT, targetId));
    return configuration;
  }
}
