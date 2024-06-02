package com.researchspace.inv.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.events.ListOfMaterialsCreationEvent;
import com.researchspace.model.events.ListOfMaterialsDeleteEvent;
import com.researchspace.model.events.ListOfMaterialsEditingEvent;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.units.QuantityInfo;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ListOfMaterialsAuditTrailTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  @Captor ArgumentCaptor<GenericEvent> eventArgumentCaptor;

  @Mock AuditTrailService auditTrail;
  @InjectMocks ListOfMaterialsAuditTrail listener;

  User anyUser = TestFactory.createAnyUser("any");

  @Test
  public void lomCreateEventIsLogged() {
    ListOfMaterials lom = createTestLomWithSampleAndSubSample();
    StructuredDocument anyDocument = TestFactory.createAnySD();

    ListOfMaterialsCreationEvent event =
        new ListOfMaterialsCreationEvent(lom, anyUser, anyDocument);
    listener.listOfMaterialsCreated(event);
    verify(auditTrail).notify(eventArgumentCaptor.capture());
    assertEquals(
        "Added List of Materials LM123. Added inventory items: SA11, SS22.",
        eventArgumentCaptor.getValue().getDescription());

    Mockito.verifyNoMoreInteractions(auditTrail);
  }

  private ListOfMaterials createTestLomWithSampleAndSubSample() {
    Sample anySample = TestFactory.createBasicSampleOutsideContainer(anyUser);
    anySample.setId(11L);
    SubSample anySubSample = anySample.getSubSamples().get(0);
    anySubSample.setId(22L);
    ListOfMaterials lom = new ListOfMaterials();
    lom.setId(123L);
    lom.addMaterial(anySample, null);
    lom.addMaterial(anySubSample, QuantityInfo.of("1 ml"));
    lom.getMaterials().get(0).setId(1L);
    lom.getMaterials().get(1).setId(2L);
    return lom;
  }

  @Test
  public void lomEditEventIsLogged() {
    ListOfMaterials originalLom = createTestLomWithSampleAndSubSample();
    originalLom.getMaterials().remove(1);
    ListOfMaterials updatedLom = createTestLomWithSampleAndSubSample();
    updatedLom.getMaterials().remove(0);
    StructuredDocument anyDocument = TestFactory.createAnySD();

    ListOfMaterialsEditingEvent event =
        new ListOfMaterialsEditingEvent(
            originalLom, anyUser, anyDocument, updatedLom.getMaterials());
    listener.listOfMaterialsEdited(event);
    verify(auditTrail).notify(eventArgumentCaptor.capture());
    assertEquals(
        "Edited List of Materials LM123. Added inventory items: SA11. Removed inventory items:"
            + " SS22.",
        eventArgumentCaptor.getValue().getDescription());

    Mockito.verifyNoMoreInteractions(auditTrail);
  }

  @Test
  public void lomDeleteEventIsLogged() {
    ListOfMaterials lom = createTestLomWithSampleAndSubSample();
    StructuredDocument anyDocument = TestFactory.createAnySD();

    ListOfMaterialsDeleteEvent event = new ListOfMaterialsDeleteEvent(lom, anyUser, anyDocument);
    listener.listOfMaterialsDeleted(event);
    verify(auditTrail).notify(eventArgumentCaptor.capture());
    assertEquals(
        "Deleted List of Materials LM123. Removed inventory items: SA11, SS22.",
        eventArgumentCaptor.getValue().getDescription());

    Mockito.verifyNoMoreInteractions(auditTrail);
  }
}
