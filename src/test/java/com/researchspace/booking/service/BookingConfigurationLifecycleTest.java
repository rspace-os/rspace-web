package com.researchspace.booking.service;

import static com.researchspace.booking.service.BookingConfigurationManagerTestFixtures.OVERSIZED_MUTATION_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.booking.dao.BookingCalendarSubscriptionDao;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.booking.service.BookingConfigurationManager.Patch;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.BookingConfigurationState;
import com.researchspace.model.booking.BookingSchedulingSettings;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.TimeSlotBooking;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.RelationshipReadAccess;
import com.researchspace.model.collection.ResourceRequest;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class BookingConfigurationLifecycleTest {

  private final BookingConfigurationManagerTestFixtures fixture =
      new BookingConfigurationManagerTestFixtures();
  private final BookingConfigurationDao dao = fixture.dao;
  private final BookingCalendarSubscriptionDao calendarSubscriptions =
      fixture.calendarSubscriptions;
  private final TimeSlotBookingDao timeSlotBookings = fixture.timeSlotBookings;
  private final User actor = fixture.actor;
  private final ApplicationEventPublisher events = fixture.events;
  private final LocalValidatorFactoryBean validator = fixture.validator;
  private final BookingConfigurationManager manager = fixture.manager;

  @BeforeEach
  void setUp() {
    fixture.setUp();
  }

  @AfterEach
  void closeValidatorFactory() {
    validator.close();
  }

  @Test
  void archivesAConfigurationInsteadOfDeletingItsReferencedRow() {
    BookingConfiguration configuration = configuration(42L, 12L);
    configuration.setEnabled(true);
    TimeSlotBooking first = new TimeSlotBooking();
    first.setState(BookingState.CONFIRMED);
    TimeSlotBooking second = new TimeSlotBooking();
    second.setState(BookingState.CONFIRMED);
    when(dao.lockById(42L)).thenReturn(Optional.of(configuration));
    when(timeSlotBookings.findFutureConfirmedByConfiguration(eq(42L), any(Date.class)))
        .thenReturn(List.of(first, second));

    BookingConfiguration removed =
        manager.archiveConfiguration(42L, 0L, actor, actor).orElseThrow();

    assertSame(configuration, removed);
    assertEquals(BookingConfigurationState.ARCHIVED, removed.getState());
    assertTrue(removed.isEnabled());
    assertSame(actor, removed.getUpdatedBy());
    assertTrue(removed.getUpdatedAt() != null);
    assertEquals(BookingState.CANCELLED, first.getState());
    assertEquals(BookingState.CANCELLED, second.getState());
    assertSame(actor, first.getUpdatedBy());
    assertSame(actor, second.getUpdatedBy());
    verify(dao).saveAndFlush(configuration);
    verify(dao, never()).remove(any());
    verify(calendarSubscriptions).deleteByConfigurationId(42L);
    verify(events).publishEvent(any(BookingConfigurationAuditEvent.class));
    verify(events, times(2))
        .publishEvent(
            (Object)
                argThat(
                    event ->
                        event instanceof TimeSlotBookingAuditEvent bookingEvent
                            && bookingEvent.action() == AuditAction.WRITE));
  }

  @Test
  void repeatedArchiveIsANoOpThatDoesNotRevokeOrAuditAgain() {
    BookingConfiguration configuration = configuration(42L, 12L);
    configuration.setState(BookingConfigurationState.ARCHIVED);
    when(dao.lockById(42L)).thenReturn(Optional.of(configuration));

    assertSame(configuration, manager.archiveConfiguration(42L, 0L, actor, actor).orElseThrow());

    verify(dao, never()).saveAndFlush(any());
    verify(dao, never()).remove(any());
    verify(calendarSubscriptions, never()).deleteByConfigurationId(any());
    verify(events, never()).publishEvent(any());
  }

  @Test
  void restoresArchivedConfigurationOnlyThroughAStateOnlyPatch() {
    BookingConfiguration configuration = configuration(42L, 12L);
    configuration.setEnabled(true);
    configuration.setState(BookingConfigurationState.ARCHIVED);
    when(dao.lockById(42L)).thenReturn(Optional.of(configuration));

    BookingConfiguration restored =
        manager
            .updateConfiguration(
                42L,
                new Patch(
                    null,
                    null,
                    BookingSchedulingSettings.Patch.empty(),
                    BookingConfigurationState.ACTIVE),
                0L,
                actor,
                actor)
            .orElseThrow();

    assertEquals(BookingConfigurationState.ACTIVE, restored.getState());
    assertTrue(restored.isEnabled());
    verify(calendarSubscriptions, never()).deleteByConfigurationId(any());
    verify(events).publishEvent(any(BookingConfigurationAuditEvent.class));
  }

  @Test
  void rejectsMixedRestoreAndSettingsWithoutWriting() {
    BookingConfiguration configuration = configuration(42L, 12L);
    configuration.setState(BookingConfigurationState.ARCHIVED);
    when(dao.lockById(42L)).thenReturn(Optional.of(configuration));

    assertThrows(
        BookingConfigurationLifecycleException.class,
        () ->
            manager.updateConfiguration(
                42L,
                new Patch(
                    false,
                    null,
                    BookingSchedulingSettings.Patch.empty(),
                    BookingConfigurationState.ACTIVE),
                0L,
                actor,
                actor));

    assertEquals(BookingConfigurationState.ARCHIVED, configuration.getState());
    verify(dao, never()).saveAndFlush(any());
    verify(events, never()).publishEvent(any());
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
    int limit = OVERSIZED_MUTATION_LIMIT;
    when(dao.lockResources(eq(request), eq(limit), any())).thenReturn(List.of(first, second));

    List<BookingConfiguration> removed = manager.archiveConfigurations(request, actor, actor);

    assertEquals(List.of(first, second), removed);
    assertTrue(
        removed.stream().allMatch(value -> value.getState() == BookingConfigurationState.ARCHIVED));
    assertTrue(removed.stream().allMatch(BookingConfiguration::isEnabled));
    assertTrue(removed.stream().allMatch(configuration -> configuration.getUpdatedBy() == actor));
    verify(dao, times(2)).saveAndFlush(any());
    verify(dao, never()).remove(any());
    verify(events, times(2)).publishEvent(any(BookingConfigurationAuditEvent.class));
  }

  @Test
  void bulkArchiveReturnsArchivedMatchesAsNoOpsWithoutRevokingOrAuditingAgain() {
    ResourceRequest request =
        ResourceRequest.unpaged(
            new FilterExpression.Comparison("id", Operator.IN, List.of(41L, 42L), false));
    BookingConfiguration active = configuration(41L, 11L);
    BookingConfiguration alreadyArchived = configuration(42L, 12L);
    alreadyArchived.setState(BookingConfigurationState.ARCHIVED);
    alreadyArchived.setConfigurationVersion(3L);
    int limit = OVERSIZED_MUTATION_LIMIT;
    when(dao.lockResources(eq(request), eq(limit), any()))
        .thenReturn(List.of(active, alreadyArchived));

    List<BookingConfiguration> archived = manager.archiveConfigurations(request, actor, actor);

    assertEquals(List.of(active, alreadyArchived), archived);
    assertEquals(BookingConfigurationState.ARCHIVED, active.getState());
    assertEquals(3L, alreadyArchived.getConfigurationVersion());
    verify(dao).saveAndFlush(active);
    verify(dao, never()).saveAndFlush(alreadyArchived);
    verify(calendarSubscriptions).deleteByConfigurationId(41L);
    verify(calendarSubscriptions, never()).deleteByConfigurationId(42L);
    verify(events).publishEvent(any(BookingConfigurationAuditEvent.class));
  }

  @Test
  void bulkSettingsRejectArchivedMatchesBeforeChangingAnyRows() {
    ResourceRequest request =
        ResourceRequest.unpaged(
            new FilterExpression.Comparison("id", Operator.IN, List.of(41L, 42L), false));
    BookingConfiguration active = configuration(41L, 11L);
    active.setEnabled(true);
    BookingConfiguration archived = configuration(42L, 12L);
    archived.setState(BookingConfigurationState.ARCHIVED);
    int limit = OVERSIZED_MUTATION_LIMIT;
    when(dao.lockResources(eq(request), eq(limit), any())).thenReturn(List.of(active, archived));

    assertThrows(
        BookingConfigurationLifecycleException.class,
        () -> manager.updateConfigurations(request, new Patch(false, null), actor, actor));

    assertTrue(active.isEnabled());
    verify(dao, never()).saveAndFlush(any());
    verify(events, never()).publishEvent(any());
  }

  @Test
  void permanentlyDeletesActiveConfigurationAndEveryLiveDependentAsDirectSysadmin() {
    BookingConfiguration configuration = configuration(42L, 12L);
    configuration.setConfigurationVersion(7L);
    when(dao.lockById(42L)).thenReturn(Optional.of(configuration));
    when(timeSlotBookings.removeAllByConfigurationId(42L)).thenReturn(3);
    when(calendarSubscriptions.deleteByConfigurationId(42L)).thenReturn(2);

    assertEquals(42L, manager.permanentlyDeleteConfiguration(42L, 7L, actor, actor).orElseThrow());

    verify(timeSlotBookings).removeAllByConfigurationId(42L);
    verify(calendarSubscriptions).deleteByConfigurationId(42L);
    verify(dao).removeConfigurationAndAccess(configuration);
    verify(events).publishEvent(any(BookingConfigurationPermanentDeleteAuditEvent.class));
  }

  @Test
  void rejectsPermanentDeleteForANonSysadmin() {
    BookingConfiguration configuration = configuration(42L, 12L);
    configuration.setState(BookingConfigurationState.ARCHIVED);
    configuration.setConfigurationVersion(7L);
    when(actor.hasSysadminRole()).thenReturn(false);

    assertThrows(
        AuthorizationException.class,
        () -> manager.permanentlyDeleteConfiguration(42L, 7L, actor, actor));

    verify(dao, never()).lockById(any());
    verify(timeSlotBookings, never()).removeAllByConfigurationId(any());
    verify(dao, never()).removeConfigurationAndAccess(any());
  }

  @Test
  void explicitlyAllowsRelationshipTargetsForSystemAuthorizedBulkSelection() {
    ResourceRequest request =
        ResourceRequest.unpaged(
            new FilterExpression.Comparison(
                "target.name", Operator.EQUAL, List.of("Microscope"), false));
    int limit = OVERSIZED_MUTATION_LIMIT;
    ArgumentCaptor<RelationshipReadAccess> access =
        ArgumentCaptor.forClass(RelationshipReadAccess.class);
    when(dao.lockResources(eq(request), eq(limit), any())).thenReturn(List.of());

    manager.archiveConfigurations(request, actor, actor);

    verify(dao).lockResources(eq(request), eq(limit), access.capture());
    assertFalse(access.getValue().result("booking-instruments").isDenied());
    assertTrue(access.getValue().result("booking-instruments").constraintOrEmpty().isEmpty());
  }

  private BookingConfiguration configuration(long id, long targetId) {
    return fixture.configuration(id, targetId);
  }
}
