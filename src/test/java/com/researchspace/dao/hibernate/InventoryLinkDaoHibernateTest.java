package com.researchspace.dao.hibernate;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.testutils.SpringTransactionalTest;
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
