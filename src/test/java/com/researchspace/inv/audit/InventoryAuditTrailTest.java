package com.researchspace.inv.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.events.InventoryDeleteEvent;
import com.researchspace.model.events.InventoryMoveEvent;
import com.researchspace.model.events.InventoryRestoreEvent;
import com.researchspace.model.events.InventoryTransferEvent;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.record.TestFactory;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class InventoryAuditTrailTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Captor ArgumentCaptor<GenericEvent> eventArgumentCaptor;

  @Mock AuditTrailService auditTrail;
  @InjectMocks InventoryAuditTrail listener;

  User anyUser = TestFactory.createAnyUser("any");

  @Test
  public void sampleCreatedEventIsLogged() {
    Sample anySample = TestFactory.createBasicSampleOutsideContainer(anyUser);
    InventoryCreationEvent event = new InventoryCreationEvent(anySample, anyUser);
    listener.inventoryRecordCreated(event);
    verify(auditTrail).notify(Mockito.any(GenericEvent.class));
    Mockito.verifyNoMoreInteractions(auditTrail);
  }

  @Test
  public void sampleAccessedEventIsLogged() {
    Sample anySample = TestFactory.createBasicSampleOutsideContainer(anyUser);
    InventoryAccessEvent event = new InventoryAccessEvent(anySample, anyUser);
    listener.inventoryRecordAccessed(event);
    verify(auditTrail).notify(Mockito.any(GenericEvent.class));
    Mockito.verifyNoMoreInteractions(auditTrail);
  }

  @Test
  public void sampleDeletedEventIsLogged() {
    Sample anySample = TestFactory.createBasicSampleOutsideContainer(anyUser);
    InventoryDeleteEvent event = new InventoryDeleteEvent(anySample, anyUser);
    listener.inventoryRecordDeleted(event);
    verify(auditTrail).notify(Mockito.any(GenericEvent.class));
  }

  @Test
  public void sampleRestoreEventIsLogged() {
    Sample anySample = TestFactory.createBasicSampleOutsideContainer(anyUser);
    InventoryRestoreEvent event = new InventoryRestoreEvent(anySample, anyUser);
    listener.inventoryRecordRestored(event);
    verify(auditTrail).notify(Mockito.any(GenericEvent.class));
    Mockito.verifyNoMoreInteractions(auditTrail);
  }

  @Test
  public void sampleTransferredEventIsLogged() {
    Sample anySample = TestFactory.createBasicSampleOutsideContainer(anyUser);
    User anotherUser = TestFactory.createAnyUser("another");
    InventoryTransferEvent event =
        new InventoryTransferEvent(anySample, anyUser, anyUser, anotherUser);
    listener.inventoryRecordTransferred(event);
    verify(auditTrail).notify(Mockito.any(GenericEvent.class));
    Mockito.verifyNoMoreInteractions(auditTrail);
  }

  @Test
  public void containerMoveEventIsLoggedWithProperDescription() throws IOException {
    Container box = TestFactory.createListContainer(anyUser);
    Container fridge = TestFactory.createGridContainer(anyUser, 1, 1);
    fridge.setId(5L);
    Container workbench = TestFactory.createWorkbench(anyUser);
    workbench.setId(1L);

    // move from workbench to fridge
    listener.inventoryRecordMoved(new InventoryMoveEvent(box, workbench, fridge, anyUser));
    // move from fridge to top-level
    listener.inventoryRecordMoved(new InventoryMoveEvent(box, fridge, null, anyUser));
    // move from top-level to workbench
    listener.inventoryRecordMoved(new InventoryMoveEvent(box, null, workbench, anyUser));

    verify(auditTrail, Mockito.times(3)).notify(eventArgumentCaptor.capture());
    assertEquals(
        "from: Workbench of first last (BE1) to: c2 (IC5)",
        eventArgumentCaptor.getAllValues().get(0).getDescription());
    assertEquals("from: c2 (IC5)", eventArgumentCaptor.getAllValues().get(1).getDescription());
    assertEquals(
        "to: Workbench of first last (BE1)",
        eventArgumentCaptor.getAllValues().get(2).getDescription());
    Mockito.verifyNoMoreInteractions(auditTrail);
  }
}
