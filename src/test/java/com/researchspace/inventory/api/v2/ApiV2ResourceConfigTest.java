package com.researchspace.inventory.api.v2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.researchspace.api.v2.resource.*;
import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.collection.*;
import com.researchspace.service.inventory.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiV2ResourceConfigTest {
  @Test
  void registersContainersAsTheInstrumentParentTarget() {
    var access = new ContainerReadAccess(mock(InventoryPermissionUtils.class));
    var containers =
        new ContainerResourceOperations(mock(ContainerReadManager.class), access)
            .containerApiV2Resource();
    var instruments =
        new ApiV2ResourceSpec<>(
            ApiV2InstrumentResource.DESCRIPTION,
            mock(ResourceOperations.class),
            Long::valueOf,
            "create",
            "update");
    var catalog = new ApiV2ResourceCatalog(List.of(containers, instruments));
    assertTrue(catalog.find("containers").orElseThrow().supports(ResourceOperation.READ));
    assertFalse(catalog.find("containers").orElseThrow().supports(ResourceOperation.UPDATE));
    assertSame(access, containers.description().accessPolicy().readAccess());
    assertEquals(
        "containers",
        catalog
            .find("instruments")
            .orElseThrow()
            .description()
            .requireRelationship("parentContainer")
            .targets()
            .get(0)
            .resourceName());
  }
}
