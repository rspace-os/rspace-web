package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import com.researchspace.testutils.TestFactory;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Fast unit tests for the contract of the instrument template revisions listing endpoint. Populated
 * revision lists (with links) are covered by the MVC ITs.
 */
@ExtendWith(MockitoExtension.class)
public class InstrumentTemplatesRevisionsEndpointTest {

  @Mock private InventoryAuditApiManager auditMgr;
  @Mock private InstrumentEntityApiManager instrumentMgr;

  private final User user = TestFactory.createAnyUser("templateRevisionsUser");

  private InstrumentTemplatesApiController controller;

  @BeforeEach
  public void setUp() {
    controller = new InstrumentTemplatesApiController();
    ReflectionTestUtils.setField(controller, "instrumentApiMgr", instrumentMgr);
    ReflectionTestUtils.setField(controller, "inventoryAuditMgr", auditMgr);
  }

  @Test
  public void templateRevisionsEndpointReturnsRevisionListForTemplate() {
    InstrumentTemplate template = new InstrumentTemplate();
    when(instrumentMgr.assertUserCanReadInstrumentTemplate(1L, user)).thenReturn(template);
    ApiInventoryRecordRevisionList revisions = new ApiInventoryRecordRevisionList();
    when(auditMgr.getInventoryRecordRevisions(template)).thenReturn(revisions);

    assertSame(revisions, controller.getInstrumentTemplateAllRevisions(1L, user));
  }

  @Test
  public void templateRevisionsEndpointThrows404ForNonTemplateId() {
    when(instrumentMgr.assertUserCanReadInstrumentTemplate(1L, user))
        .thenThrow(new NotFoundException("No instrument template with id: 1"));

    assertThrows(
        NotFoundException.class, () -> controller.getInstrumentTemplateAllRevisions(1L, user));
  }
}
