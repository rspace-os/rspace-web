package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InstrumentApiManager;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.testutils.TestFactory;
import javax.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Fast unit tests for the not-found contract of the inventory version/revision endpoints: a missing
 * version or revision must surface as 404 (NotFoundException), never a 200 null body. Successful
 * responses are covered by the MVC ITs.
 */
public class InventoryVersionEndpointsNotFoundTest {

  private final InventoryAuditApiManager auditMgr = mock(InventoryAuditApiManager.class);
  private final SampleApiManager sampleMgr = mock(SampleApiManager.class);
  private final SubSampleApiManager subSampleMgr = mock(SubSampleApiManager.class);
  private final ContainerApiManager containerMgr = mock(ContainerApiManager.class);
  private final InstrumentApiManager instrumentMgr = mock(InstrumentApiManager.class);
  private final MessageSourceUtils messages = mock(MessageSourceUtils.class);

  private final User user = TestFactory.createAnyUser("notFoundUser");

  @BeforeEach
  public void setUp() {
    lenient()
        .when(messages.getResourceNotFoundMessage(anyString(), anyLong()))
        .thenReturn("not found");
  }

  /** Wires the BaseApiInventoryController/BaseApiController fields every controller inherits. */
  private <T extends BaseApiInventoryController> T wireBase(T controller) {
    ReflectionTestUtils.setField(controller, "sampleApiMgr", sampleMgr);
    ReflectionTestUtils.setField(controller, "subSampleApiMgr", subSampleMgr);
    ReflectionTestUtils.setField(controller, "containerApiMgr", containerMgr);
    ReflectionTestUtils.setField(controller, "instrumentApiMgr", instrumentMgr);
    ReflectionTestUtils.setField(controller, "messages", messages);
    return controller;
  }

  /** Wires the controller-declared inventoryAuditMgr field on top of the base wiring. */
  private <T extends BaseApiInventoryController> T wire(T controller) {
    wireBase(controller);
    ReflectionTestUtils.setField(controller, "inventoryAuditMgr", auditMgr);
    return controller;
  }

  @Test
  public void sampleVersionEndpointThrows404ForMissingVersion() {
    SamplesApiController controller = wire(new SamplesApiController());
    Sample sample = TestFactory.createBasicSampleOutsideContainer(user);
    when(sampleMgr.assertUserCanReadSample(1L, user)).thenReturn(sample);
    when(auditMgr.getApiSampleVersion(sample, 9L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> controller.getSampleVersion(1L, 9L, user));
  }

  @Test
  public void sampleRevisionEndpointThrows404ForMissingRevision() {
    SamplesApiController controller = wire(new SamplesApiController());
    when(sampleMgr.assertUserCanReadSample(1L, user))
        .thenReturn(TestFactory.createBasicSampleOutsideContainer(user));
    when(auditMgr.getApiSampleRevision(1L, 9L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> controller.getSampleRevision(1L, 9L, user));
  }

  @Test
  public void subSampleVersionEndpointThrows404ForMissingVersion() {
    SubSamplesApiController controller = wire(new SubSamplesApiController());
    SubSample subSample =
        TestFactory.createBasicSampleOutsideContainer(user).getSubSamples().get(0);
    when(subSampleMgr.assertUserCanReadSubSample(1L, user)).thenReturn(subSample);
    when(auditMgr.getApiSubSampleVersion(subSample, 9L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> controller.getSubSampleVersion(1L, 9L, user));
  }

  @Test
  public void subSampleRevisionEndpointThrows404ForMissingRevision() {
    SubSamplesApiController controller = wire(new SubSamplesApiController());
    when(subSampleMgr.assertUserCanReadSubSample(1L, user))
        .thenReturn(TestFactory.createBasicSampleOutsideContainer(user).getSubSamples().get(0));
    when(auditMgr.getApiSubSampleRevision(1L, 9L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> controller.getSubSampleRevision(1L, 9L, user));
  }

  @Test
  public void containerVersionEndpointThrows404ForMissingVersion() throws Exception {
    ContainersApiController controller = wire(new ContainersApiController());
    Container container = TestFactory.createListContainer(user);
    when(containerMgr.assertUserCanReadContainer(1L, user)).thenReturn(container);
    when(auditMgr.getApiContainerVersion(container, 9L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> controller.getContainerVersion(1L, 9L, user));
  }

  @Test
  public void containerRevisionEndpointThrows404ForMissingRevision() throws Exception {
    ContainersApiController controller = wire(new ContainersApiController());
    when(containerMgr.assertUserCanReadContainer(1L, user))
        .thenReturn(TestFactory.createListContainer(user));
    when(auditMgr.getApiContainerRevision(1L, 9L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> controller.getContainerRevision(1L, 9L, user));
  }

  @Test
  public void instrumentVersionEndpointThrows404ForMissingVersion() {
    InstrumentsApiController controller = wiredInstrumentsController();
    Instrument instrument = new Instrument();
    instrument.setName("test instrument");
    instrument.setOwner(user);
    when(instrumentMgr.assertUserCanReadInstrument(1L, user)).thenReturn(instrument);
    when(auditMgr.getApiInstrumentVersion(instrument, 9L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> controller.getInstrumentVersion(1L, 9L, user));
  }

  @Test
  public void instrumentRevisionEndpointThrows404ForMissingRevision() {
    InstrumentsApiController controller = wiredInstrumentsController();
    Instrument instrument = new Instrument();
    instrument.setName("test instrument");
    instrument.setOwner(user);
    when(instrumentMgr.assertUserCanReadInstrument(1L, user)).thenReturn(instrument);
    when(auditMgr.getApiInstrumentRevision(1L, 9L)).thenReturn(null);

    assertThrows(NotFoundException.class, () -> controller.getInstrumentRevision(1L, 9L, user));
  }

  @Test
  public void templateVersionEndpointThrows404ForMissingVersion() {
    SampleTemplatesApiController controller = wireBase(new SampleTemplatesApiController());
    when(sampleMgr.getApiSampleTemplateVersion(1L, 9L, user)).thenReturn(null);

    assertThrows(
        NotFoundException.class, () -> controller.getSampleTemplateVersionById(1L, 9L, user));
  }

  private InstrumentsApiController wiredInstrumentsController() {
    InstrumentsApiController controller = wire(new InstrumentsApiController());
    ReflectionTestUtils.setField(controller, "inventoryInstrumentEnabled", true);
    return controller;
  }
}
