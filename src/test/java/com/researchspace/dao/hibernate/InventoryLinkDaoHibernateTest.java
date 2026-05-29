package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryLinkDaoHibernateTest extends SpringTransactionalTest {

  @Autowired private InventoryLinkDao dao;

  @Test
  public void saveAndGenerateId() {
    InventoryLink link = newLink("SA123", GlobalIdPrefix.SA, 123L, "References");
    InventoryLink saved = dao.save(link);

    assertNotNull(saved.getId());
    assertNotNull(saved.getCreatedAt());
    assertNotNull(saved.getModifiedAt());
  }

  @Test
  public void findByTargetGlobalIdReturnsMatchingLinks() {
    InventoryLink link1 = newLink("SA10", GlobalIdPrefix.SA, 10L, "IsCalibratedBy");
    InventoryLink link2 = newLink("SA10", GlobalIdPrefix.SA, 10L, "References");
    InventoryLink other = newLink("SA999", GlobalIdPrefix.SA, 999L, "References");
    dao.save(link1);
    dao.save(link2);
    dao.save(other);

    List<InventoryLink> found = dao.findByTargetGlobalId("SA10");

    assertEquals(2, found.size());
    assertTrue(found.stream().allMatch(l -> "SA10".equals(l.getTargetGlobalId())));
  }

  @Test
  public void findByTargetGlobalIdExcludesSoftDeleted() {
    InventoryLink active = newLink("SA20", GlobalIdPrefix.SA, 20L, "References");
    InventoryLink removed = newLink("SA20", GlobalIdPrefix.SA, 20L, "Cites");
    removed.setDeleted(true);
    dao.save(active);
    dao.save(removed);

    List<InventoryLink> found = dao.findByTargetGlobalId("SA20");

    assertEquals(1, found.size());
    assertEquals("References", found.get(0).getRelationType());
  }

  private InventoryLink newLink(
      String globalId, GlobalIdPrefix prefix, Long dbId, String relationType) {
    InventoryLink link = new InventoryLink();
    link.setTargetGlobalId(globalId);
    link.setTargetPrefix(prefix);
    link.setTargetDbId(dbId);
    link.setRelationType(relationType);
    return link;
  }
}
