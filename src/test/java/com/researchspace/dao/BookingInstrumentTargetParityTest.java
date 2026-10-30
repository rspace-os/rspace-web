package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.Constants;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BookingInstrumentTargetParityTest extends SpringTransactionalTest {
  private final InstrumentDao instruments;
  private final BookingInstrumentQueryDao targets;
  private final ApiV2ResourceCatalog resources;

  @Autowired
  BookingInstrumentTargetParityTest(
      InstrumentDao instruments,
      BookingInstrumentQueryDao targets,
      ApiV2ResourceCatalog resources) {
    this.instruments = instruments;
    this.targets = targets;
    this.resources = resources;
  }

  @Test
  void comparesBookingTargetsWithInventoryAcrossSharingAndDeletion() throws Exception {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"));
    User member = createAndSaveUserIfNotExists(getRandomAlphabeticString("member"));
    User outsider = createAndSaveUserIfNotExists(getRandomAlphabeticString("outsider"));
    initialiseContentWithEmptyContent(pi, owner, member, outsider);
    var group = createGroup(getRandomAlphabeticString("group"), pi);
    addUsersToGroup(pi, group, owner, member);
    Instrument shared = instruments.get(createBasicInstrumentForUser(owner, "Shared").getId());
    Instrument privateItem =
        instruments.get(createBasicInstrumentForUser(owner, "Private").getId());
    privateItem.setSharingMode(InventorySharingMode.OWNER_ONLY);
    instruments.save(privateItem);
    Instrument deleted = instruments.get(createBasicInstrumentForUser(owner, "Deleted").getId());
    deleted.setRecordDeleted(true);
    instruments.save(deleted);
    long templateId = createBasicInstrumentTemplateForUser(owner).getId();
    sessionFactory.getCurrentSession().flush();
    Set<Long> ids = Set.of(shared.getId(), privateItem.getId(), deleted.getId());
    Set<Long> requested = Set.of(shared.getId(), privateItem.getId(), deleted.getId(), templateId);
    for (User actor : List.of(owner, member, pi, getSysAdminUser(), outsider)) {
      // Catalogue lookup deliberately ignores Inventory permissions; relationship resolution does
      // not.
      assertEquals(ids, targets.getBookingRelationshipTargets(requested).keySet());
      Set<Long> expected =
          actor.equals(outsider)
              ? Set.of()
              : actor.equals(member)
                  ? Set.of(shared.getId())
                  : Set.of(shared.getId(), privateItem.getId());
      assertEquals(
          expected,
          resources
              .find("instruments")
              .orElseThrow()
              .resolveReadable(Set.copyOf(requested), actor)
              .keySet(),
          actor.getUsername());
    }
  }
}
