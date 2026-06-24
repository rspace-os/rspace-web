package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryRecordRevisionList;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InventoryAuditApiManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.testutils.TestFactory;
import javax.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Fast unit tests for the contract of the sample template revisions listing endpoint. Populated
 * revision lists (with links) are covered by the MVC ITs.
 */
public class SampleTemplatesRevisionsEndpointTest {

  private final InventoryAuditApiManager auditMgr = mock(InventoryAuditApiManager.class);
  private final SampleApiManager sampleMgr = mock(SampleApiManager.class);
  private final MessageSourceUtils messages = mock(MessageSourceUtils.class);

  private final User user = TestFactory.createAnyUser("templateRevisionsUser");

  private SampleTemplatesApiController controller;

  @BeforeEach
  public void setUp() {
    controller = new SampleTemplatesApiController();
    ReflectionTestUtils.setField(controller, "sampleApiMgr", sampleMgr);
    ReflectionTestUtils.setField(controller, "inventoryAuditMgr", auditMgr);
    ReflectionTestUtils.setField(controller, "messages", messages);
    lenient()
        .when(messages.getResourceNotFoundMessage(anyString(), anyLong()))
        .thenReturn("not found");
  }

  @Test
  public void templateRevisionsEndpointReturnsRevisionListForTemplate() {
    SampleTemplate template = new SampleTemplate();
    when(sampleMgr.assertUserCanReadSampleTemplate(1L, user)).thenReturn(template);
    ApiInventoryRecordRevisionList revisions = new ApiInventoryRecordRevisionList();
    when(auditMgr.getInventoryRecordRevisions(template)).thenReturn(revisions);

    assertSame(revisions, controller.getSampleTemplateAllRevisions(1L, user));
  }

  @Test
  public void templateRevisionsEndpointThrows404ForNonTemplateId() {
    // a plain sample id is not addressable through the template endpoint: the manager 404s
    when(sampleMgr.assertUserCanReadSampleTemplate(1L, user))
        .thenThrow(new NotFoundException("No sample template with id: 1"));

    assertThrows(NotFoundException.class, () -> controller.getSampleTemplateAllRevisions(1L, user));
  }
}
