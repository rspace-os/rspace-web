package com.researchspace.maintenance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.maintenance.dao.MaintenanceDao;
import com.researchspace.maintenance.model.ApiV2MaintenanceResource;
import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription.WriteOperation;
import com.researchspace.model.collection.ParsedDocument;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.RsqlFilterParser;
import com.researchspace.service.CollectionMutationException;
import com.researchspace.service.MessageSourceUtils;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class MaintenanceManagerUnitTest {

  private final MaintenanceDao dao = mock(MaintenanceDao.class);
  private final User user = mock(User.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final MessageSourceUtils messages = mock(MessageSourceUtils.class);
  private final MaintenanceManager manager = new MaintenanceManager(dao, events, messages);

  @BeforeEach
  void setUp() {
    when(user.hasRole(Role.SYSTEM_ROLE)).thenReturn(true);
  }

  @Test
  void appliesStartBeforeAnExplicitLoginCutoff() {
    ScheduledMaintenance maintenance = maintenance(2);
    Date newStart = hoursFromNow(4);
    Date newEnd = hoursFromNow(6);
    Date explicitCutoff = hoursFromNow(3);
    ParsedDocument patch =
        ParsedDocument.update(
            Map.of(
                "startDate", newStart,
                "endDate", newEnd,
                "stopUserLoginDate", explicitCutoff,
                "message", "updated"));
    when(dao.getSafeNull(42L)).thenReturn(Optional.of(maintenance));
    when(dao.save(maintenance)).thenReturn(maintenance);

    manager.updateResource(42L, patch, user);

    assertEquals(newStart, maintenance.getStartDate());
    assertEquals(newEnd, maintenance.getEndDate());
    assertEquals(explicitCutoff, maintenance.getStopUserLoginDate());
    assertEquals("updated", maintenance.getMessage());
    verify(events).publishEvent(any(MaintenanceChangedEvent.class));
  }

  @Test
  void rejectsInvalidWindowsAndOversizedBulkMatchesBeforeSaving() {
    ScheduledMaintenance maintenance = maintenance(2);
    ParsedDocument invalid = ParsedDocument.update(Map.of("endDate", hoursFromNow(1)));
    when(dao.getSafeNull(42L)).thenReturn(Optional.of(maintenance));

    assertThrows(
        MaintenanceOperationException.class, () -> manager.updateResource(42L, invalid, user));
    verify(dao, never()).save(any());

    ResourceRequest query = request("message==test");
    when(dao.getResources(query, 1001)).thenReturn(Collections.nCopies(1001, maintenance));
    assertThrows(
        CollectionMutationException.class, () -> manager.updateResources(query, invalid, user));
    verify(dao, never()).save(any());
  }

  @Test
  void reportsAbsentWithoutWritingWhenTheIdDoesNotExist() {
    when(dao.getSafeNull(42L)).thenReturn(Optional.empty());
    ParsedDocument patch = ParsedDocument.update(Map.of("message", "updated"));

    assertTrue(manager.updateResource(42L, patch, user).isEmpty());
    assertTrue(manager.removeResource(42L, user).isEmpty());

    verify(dao, never()).save(any());
    verify(dao, never()).remove(any());
    verify(events, never()).publishEvent(any());
  }

  @Test
  void rejectsCreateDocumentsPassedToUpdate() {
    ScheduledMaintenance maintenance = maintenance(2);
    when(dao.getSafeNull(42L)).thenReturn(Optional.of(maintenance));
    ParsedDocument createDocument =
        new ParsedDocument(WriteOperation.CREATE, Map.of("message", "updated"));

    assertThrows(
        IllegalArgumentException.class, () -> manager.updateResource(42L, createDocument, user));

    verify(dao, never()).save(any());
  }

  @Test
  void rejectsInvalidWindowsOnCreate() {
    ScheduledMaintenance invalid = new ScheduledMaintenance(hoursFromNow(2), hoursFromNow(1));

    assertThrows(MaintenanceOperationException.class, () -> manager.createResource(invalid, user));

    verify(dao, never()).save(any());
  }

  @Test
  void rejectsUnfilteredBulkMutationsBeforeQuerying() {
    ResourceRequest unfiltered = ResourceRequest.unpaged(null);

    CollectionMutationException exception =
        assertThrows(
            CollectionMutationException.class, () -> manager.removeResources(unfiltered, user));

    assertEquals(CollectionMutationException.Reason.FILTER_REQUIRED, exception.getReason());
    verify(dao, never()).getResources(any(ResourceRequest.class), anyInt());
  }

  @Test
  void checksAuthorizationBeforeBulkQueries() {
    when(user.hasRole(Role.SYSTEM_ROLE)).thenReturn(false);
    ResourceRequest query = request("id==42");

    assertThrows(AuthorizationException.class, () -> manager.removeResources(query, user));

    verify(dao, never()).getResources(any(ResourceRequest.class), anyInt());
  }

  @Test
  void publishesOneChangeEventAfterSuccessfulBulkMutation() {
    ResourceRequest query = request("message==test");
    ScheduledMaintenance first = maintenance(2);
    ScheduledMaintenance second = maintenance(3);
    when(dao.getResources(query, 1001)).thenReturn(List.of(first, second));

    manager.updateResources(query, ParsedDocument.update(Map.of("message", "updated")), user);

    verify(events, times(1)).publishEvent(any(MaintenanceChangedEvent.class));
  }

  private static ResourceRequest request(String filter) {
    return ResourceRequest.unpaged(
        new RsqlFilterParser(ApiV2MaintenanceResource.DESCRIPTION).parse(filter));
  }

  private static ScheduledMaintenance maintenance(int startHoursFromNow) {
    return new ScheduledMaintenance(
        hoursFromNow(startHoursFromNow), hoursFromNow(startHoursFromNow + 1));
  }

  private static Date hoursFromNow(int hours) {
    Calendar calendar = Calendar.getInstance();
    calendar.add(Calendar.HOUR_OF_DAY, hours);
    return calendar.getTime();
  }
}
