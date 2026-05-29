package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class InventoryLinkManagerImplTest extends SpringTransactionalTest {

  @Autowired private InventoryLinkManager linkManager;
  @Autowired private InventoryLinkDao linkDao;

  @Test
  public void createLinkPersistsRowWithParsedPrefixAndDbId() {
    User user = doCreateAndInitUser("invlink-create");

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("IsCalibratedBy");
    api.setTargetGlobalId("SA123");

    InventoryLink saved = linkManager.createLink(api, user);

    assertNotNull(saved.getId());
    assertEquals(GlobalIdPrefix.SA, saved.getTargetPrefix());
    assertEquals(Long.valueOf(123), saved.getTargetDbId());
    assertEquals("IsCalibratedBy", saved.getRelationType());
    assertNull(saved.getVersionPin());
  }

  @Test
  public void createLinkExtractsVersionPinFromTargetSuffix() {
    User user = doCreateAndInitUser("invlink-version");

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId("SA456v5");

    InventoryLink saved = linkManager.createLink(api, user);

    assertEquals(Long.valueOf(5), saved.getVersionPin());
    assertEquals(Long.valueOf(456), saved.getTargetDbId());
  }

  @Test
  public void updateLinkChangesRelationAndPreservesCreatedAt() throws Exception {
    User user = doCreateAndInitUser("invlink-upd");

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId("SA10");
    InventoryLink saved = linkManager.createLink(api, user);
    java.util.Date originalCreated = saved.getCreatedAt();

    Thread.sleep(5);
    ApiInventoryLink update = new ApiInventoryLink();
    update.setRelationType("IsCalibratedBy");
    update.setTargetGlobalId("SA11");
    InventoryLink updated = linkManager.updateLink(saved, update, user);

    assertEquals("IsCalibratedBy", updated.getRelationType());
    assertEquals("SA11", updated.getTargetGlobalId());
    assertEquals(Long.valueOf(11), updated.getTargetDbId());
    assertEquals(originalCreated, updated.getCreatedAt());
  }

  @Test
  public void deleteLinkSoftDeletes() {
    User user = doCreateAndInitUser("invlink-del");

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId("SA20");
    InventoryLink saved = linkManager.createLink(api, user);

    linkManager.deleteLink(saved, user);

    InventoryLink reloaded = linkDao.get(saved.getId());
    assertTrue(reloaded.isDeleted());
  }

  @Test
  public void findReferencingItemsReturnsEmptyWhenNoSources() {
    User user = doCreateAndInitUser("invlink-empty-ref");
    List<ApiInventoryReferencingItem> rows = linkManager.findReferencingItems("SA9999", user);
    assertEquals(0, rows.size());
  }
}
