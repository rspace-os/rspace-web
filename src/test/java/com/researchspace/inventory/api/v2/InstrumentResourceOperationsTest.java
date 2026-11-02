package com.researchspace.inventory.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.model.User;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentReadSummary;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InstrumentResourceOperationsTest {

  private final InstrumentEntityApiManager manager = mock(InstrumentEntityApiManager.class);
  private final InstrumentResourceOperations operations =
      new InstrumentResourceOperations(
          manager,
          mock(InstrumentReadAccess.class),
          mock(InstrumentCustomFieldManager.class),
          mock(ExtraFieldDao.class));
  private final User actor = mock(User.class);

  @Test
  void projectsListAndRelationshipDocumentsWithParentIds() {
    ResourceRequest request = ResourceRequest.unpaged(null);
    var summary = new InstrumentReadSummary(1L, "Scope", false, 12L);
    when(manager.getReadableInstrumentSummaries(request, actor))
        .thenReturn(new ResourcePage<>(List.of(summary), 7));
    when(manager.getReadableInstrumentSummaries(Set.of(1L), actor)).thenReturn(Map.of(1L, summary));
    assertTrue(operations.usesReadDocuments());
    var page = operations.findReadDocuments(request, actor);
    assertEquals(7, page.total());
    assertEquals(
        Map.of(
            "id", 1L, "name", "Scope", "globalId", "IN1", "deleted", false, "parentContainer", 12L),
        page.resources().get(0));
    assertEquals(
        Map.of(1L, page.resources().get(0)),
        operations.relationshipReadDocuments(Set.of(1L), actor).orElseThrow());
    org.mockito.Mockito.verify(manager, org.mockito.Mockito.never())
        .getReadableInstruments(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void readsOneInstrumentThroughTheExistingPermissionCheck() {
    Instrument instrument = instrument(12L);
    when(manager.findReadableInstrument(12L, actor)).thenReturn(Optional.of(instrument));

    assertSame(instrument, operations.findById(12L, actor).orElseThrow());
  }

  @Test
  void hidesADeletedInstrumentFromASingleRead() {
    Instrument instrument = instrument(12L);
    instrument.setRecordDeleted(true);
    when(manager.findReadableInstrument(12L, actor)).thenReturn(Optional.of(instrument));

    assertTrue(operations.findById(12L, actor).isEmpty());
  }

  static InstrumentResourceOperations operationsFor(InstrumentEntityApiManager manager) {
    return new InstrumentResourceOperations(
        manager,
        mock(InstrumentReadAccess.class),
        mock(InstrumentCustomFieldManager.class),
        mock(ExtraFieldDao.class));
  }

  static Instrument instrument(long id) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    return instrument;
  }
}
