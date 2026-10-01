package com.researchspace.inventory.api.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.dao.ExtraFieldDao;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.User;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResourceFieldSelections;
import com.researchspace.model.collection.ResourceRegistry;
import com.researchspace.model.collection.ResourceRenderer;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentParentLocationSummary;
import com.researchspace.service.inventory.InstrumentCustomFieldManager;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InstrumentReadAccess;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InstrumentParentLocationRedactionTest {

  private final InstrumentEntityApiManager manager = mock(InstrumentEntityApiManager.class);
  private final InstrumentResourceOperations operations =
      new InstrumentResourceOperations(
          manager,
          mock(InstrumentReadAccess.class),
          mock(InstrumentCustomFieldManager.class),
          mock(ExtraFieldDao.class));
  private final User actor = mock(User.class);

  @Test
  void resolvesDistinctCurrentParentsOnceAndMasksEveryUnresolvedLocation() {
    Instrument readable = instrument(1L);
    Instrument sameReadableParent = instrument(2L);
    Instrument unreadable = instrument(3L);
    Instrument missing = instrument(4L);
    Instrument stale = instrument(5L);
    when(manager.getParentLocationSummaries(Set.of(1L, 2L, 3L, 4L, 5L)))
        .thenReturn(
            Map.of(
                1L, new InstrumentParentLocationSummary(10L, "Readable", ContainerType.LIST),
                2L, new InstrumentParentLocationSummary(10L, "Readable", ContainerType.LIST),
                3L, new InstrumentParentLocationSummary(20L, "Unreadable", ContainerType.LIST)));
    when(manager.getReadableParentContainerIds(Set.of(10L, 20L), actor)).thenReturn(Set.of(10L));

    Map<Object, Map<String, Object>> overrides =
        operations.readOverrides(
            List.of(readable, sameReadableParent, unreadable, missing, stale), actor);

    assertEquals("Readable", overrides.get(1L).get("parentContainerName"));
    assertEquals("IC10", overrides.get(1L).get("parentContainerGlobalId"));
    assertEquals(overrides.get(1L), overrides.get(2L));
    assertMasked(overrides.get(3L));
    assertMasked(overrides.get(4L));
    assertMasked(overrides.get(5L));
    verify(manager).getParentLocationSummaries(Set.of(1L, 2L, 3L, 4L, 5L));
    verify(manager).getReadableParentContainerIds(Set.of(10L, 20L), actor);
  }

  @Test
  void doesNotAddLocationFieldsThatTheRequestDidNotSelect() {
    Instrument instrument = instrument(1L);
    when(manager.getParentLocationSummaries(Set.of(1L)))
        .thenReturn(
            Map.of(
                1L, new InstrumentParentLocationSummary(20L, "Hidden parent", ContainerType.LIST)));
    when(manager.getReadableParentContainerIds(Set.of(20L), actor)).thenReturn(Set.of());
    Map<String, Object> overrides = operations.readOverrides(List.of(instrument), actor).get(1L);
    ResourceRenderer renderer =
        new ResourceRenderer(new ResourceRegistry(List.of(ApiV2InstrumentResource.DESCRIPTION)));

    Map<String, Object> withoutLocation =
        renderer.render(
            instrument,
            ApiV2InstrumentResource.DESCRIPTION,
            ResourceFieldSelections.root(FieldSelection.include(Set.of("globalId"))),
            IncludeTree.empty(),
            (resource, id) -> java.util.Optional.empty(),
            overrides);
    Map<String, Object> withLocation =
        renderer.render(
            instrument,
            ApiV2InstrumentResource.DESCRIPTION,
            ResourceFieldSelections.root(
                FieldSelection.include(Set.of("parentContainerName", "parentContainerGlobalId"))),
            IncludeTree.empty(),
            (resource, id) -> java.util.Optional.empty(),
            overrides);

    assertFalse(withoutLocation.containsKey("parentContainerName"));
    assertFalse(withoutLocation.containsKey("parentContainerGlobalId"));
    assertTrue(withLocation.containsKey("parentContainerName"));
    assertTrue(withLocation.containsKey("parentContainerGlobalId"));
    assertNull(withLocation.get("parentContainerName"));
    assertNull(withLocation.get("parentContainerGlobalId"));
  }

  private static void assertMasked(Map<String, Object> values) {
    assertTrue(values.containsKey("parentContainerName"));
    assertTrue(values.containsKey("parentContainerGlobalId"));
    assertNull(values.get("parentContainerName"));
    assertNull(values.get("parentContainerGlobalId"));
  }

  private static Instrument instrument(long id) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    return instrument;
  }
}
