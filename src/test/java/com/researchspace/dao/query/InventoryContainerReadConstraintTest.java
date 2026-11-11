package com.researchspace.dao.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.inventory.model.ApiV2InstrumentResource;
import com.researchspace.model.collection.InventoryContainerReadConstraint;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryContainerReadConstraintTest {
  @Test
  void sysadminStillExcludesDeletedContainers() {
    var constraint =
        new InventoryContainerReadConstraint("admin", List.of(), List.of(), List.of(), true);
    assertEquals(
        "(c.deleted = false)",
        new RsqlCollectionQuery(ApiV2InstrumentResource.DESCRIPTION, "c")
            .translateTrusted(constraint)
            .expression());
  }
}
