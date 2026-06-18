package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import org.junit.jupiter.api.Test;

class ApiInventoryDOITest {

  @Test
  void typeNotMutatedOnExistingIdentifier() {
    DigitalObjectIdentifier existing = new DigitalObjectIdentifier(null, null);
    existing.setId(1L);
    existing.setType(IdentifierType.IGSN_DATACITE);

    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.setDoiType(IdentifierType.PIDINST_DATACITE.name());

    boolean changed = apiDoi.applyChangesToDatabaseDOI(existing);

    assertEquals(
        IdentifierType.IGSN_DATACITE,
        existing.getType(),
        "an existing identifier's type must not be mutated by an incoming payload");
    assertFalse(changed);
  }

  @Test
  void typeAppliedWhenCreatingNewIdentifier() {
    DigitalObjectIdentifier newDoi =
        new DigitalObjectIdentifier(null, null); // transient, id == null

    ApiInventoryDOI apiDoi = new ApiInventoryDOI();
    apiDoi.setDoiType(IdentifierType.PIDINST_DATACITE.name());

    boolean changed = apiDoi.applyChangesToDatabaseDOI(newDoi);

    assertEquals(
        IdentifierType.PIDINST_DATACITE,
        newDoi.getType(),
        "a new identifier must adopt the incoming type");
    assertTrue(changed);
  }
}
