package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit test for the restricted-search post-filter that keeps the default template owners' templates
 * visible while hiding their ordinary records. Pins the RSDEV-1219 fix that tracks <em>every</em>
 * default owner (sample and instrument), so that when the two defaults resolve to different owners
 * neither owner's non-template inventory leaks into another user's restricted search results.
 */
class FullTextSearcherDefaultOwnerFilterTest {

  private InventoryRecord recordOwnedBy(
      String owner, boolean sampleTemplate, boolean instrumentTemplate) {
    User user = Mockito.mock(User.class);
    when(user.getUsername()).thenReturn(owner);
    InventoryRecord rec = Mockito.mock(InventoryRecord.class);
    when(rec.getOwner()).thenReturn(user);
    when(rec.isSampleTemplate()).thenReturn(sampleTemplate);
    when(rec.isInstrumentTemplate()).thenReturn(instrumentTemplate);
    return rec;
  }

  @Test
  @DisplayName("With no default owners nothing is filtered out")
  void keepsEverythingWhenNoDefaultOwners() {
    InventoryRecord rec = recordOwnedBy("someone", false, false);
    assertTrue(FullTextSearcherImpl.isNotOwnedByDefaultTemplatesOwnerOrTemplate(rec, null));
    assertTrue(FullTextSearcherImpl.isNotOwnedByDefaultTemplatesOwnerOrTemplate(rec, Set.of()));
  }

  @Test
  @DisplayName("Records owned by a non-default user are kept")
  void keepsRecordsOwnedByANonDefaultUser() {
    InventoryRecord rec = recordOwnedBy("otherUser", false, false);
    assertTrue(
        FullTextSearcherImpl.isNotOwnedByDefaultTemplatesOwnerOrTemplate(
            rec, Set.of("sampleOwner", "instrumentOwner")));
  }

  @Test
  @DisplayName("Non-template records of every default owner are excluded, not just the last one")
  void excludesNonTemplateRecordsForEveryDefaultOwner() {
    Set<String> owners = Set.of("sampleOwner", "instrumentOwner");
    InventoryRecord sampleOwnersRecord = recordOwnedBy("sampleOwner", false, false);
    InventoryRecord instrumentOwnersRecord = recordOwnedBy("instrumentOwner", false, false);

    assertFalse(
        FullTextSearcherImpl.isNotOwnedByDefaultTemplatesOwnerOrTemplate(
            sampleOwnersRecord, owners));
    assertFalse(
        FullTextSearcherImpl.isNotOwnedByDefaultTemplatesOwnerOrTemplate(
            instrumentOwnersRecord, owners));
  }

  @Test
  @DisplayName("Each default owner's own templates remain visible")
  void keepsDefaultOwnersTemplates() {
    Set<String> owners = Set.of("sampleOwner", "instrumentOwner");
    InventoryRecord sampleTemplate = recordOwnedBy("sampleOwner", true, false);
    InventoryRecord instrumentTemplate = recordOwnedBy("instrumentOwner", false, true);

    assertTrue(
        FullTextSearcherImpl.isNotOwnedByDefaultTemplatesOwnerOrTemplate(sampleTemplate, owners));
    assertTrue(
        FullTextSearcherImpl.isNotOwnedByDefaultTemplatesOwnerOrTemplate(
            instrumentTemplate, owners));
  }
}
