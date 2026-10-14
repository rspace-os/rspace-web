package com.researchspace.inventory.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.model.User;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
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

  private static Instrument instrument(long id) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    return instrument;
  }

  @Test
  void projectsTheAuthorizedScalarPageWithoutEntityOrLocationReadsForBasicFields() {
    ResourceRequest request =
        new ResourceRequest(
            null,
            List.of(new Sort("id", false)),
            new ResourceRequest.Page(2, 2),
            FieldSelection.include(Set.of("name")),
            IncludeTree.empty());
    when(manager.getReadableInstrumentSummaries(request, actor))
        .thenReturn(new ResourcePage<>(List.of(summary(2L, "two"), summary(1L, "one")), 5));

    ResourcePage<Map<String, Object>> result = operations.findReadDocuments(request, actor);

    assertEquals(5L, result.total());
    assertEquals(List.of(2L, 1L), result.resources().stream().map(row -> row.get("id")).toList());
    assertEquals("two", result.resources().get(0).get("name"));
    assertEquals("IN2", result.resources().get(0).get("globalId"));
    assertTrue(operations.usesReadDocuments());
    verify(manager, never()).getReadableInstruments(any(), any());
    verify(manager, never()).getParentLocationSummaries(any());
    verify(manager, never()).getReadableParentContainerIds(any(), any());
  }

  @Test
  void projectsLocationFieldsWithOneBatchAndMasksUnreadableAndMissingParents() {
    ResourceRequest request = ResourceRequest.unpaged(null);
    when(manager.getReadableInstrumentSummaries(request, actor))
        .thenReturn(
            new ResourcePage<>(
                List.of(
                    summary(1L, "one"),
                    summary(2L, "two"),
                    summary(3L, "three"),
                    summary(4L, "four")),
                4));
    when(manager.getParentLocationSummaries(Set.of(1L, 2L, 3L, 4L)))
        .thenReturn(
            Map.of(
                1L, new InstrumentParentLocationSummary(10L, "Bench", ContainerType.WORKBENCH),
                2L, new InstrumentParentLocationSummary(10L, "Bench", ContainerType.WORKBENCH),
                3L, new InstrumentParentLocationSummary(20L, "Private", ContainerType.LIST)));
    when(manager.getReadableParentContainerIds(Set.of(10L, 20L), actor)).thenReturn(Set.of(10L));

    List<Map<String, Object>> documents = operations.findReadDocuments(request, actor).resources();

    for (int index : List.of(0, 1)) {
      assertEquals("Bench", documents.get(index).get("parentContainerName"));
      assertEquals("BE10", documents.get(index).get("parentContainerGlobalId"));
    }
    for (int index : List.of(2, 3)) {
      assertNull(documents.get(index).get("parentContainerName"));
      assertNull(documents.get(index).get("parentContainerGlobalId"));
    }
    verify(manager).getParentLocationSummaries(Set.of(1L, 2L, 3L, 4L));
    verify(manager).getReadableParentContainerIds(Set.of(10L, 20L), actor);
    verify(manager, never()).getReadableInstruments(any(), any());
  }

  private static InstrumentReadSummary summary(Long id, String name) {
    return new InstrumentReadSummary(id, name, false, null, null, null);
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
}
