package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryReferencingItem;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.record.StructuredDocument;
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
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("IsCalibratedBy");
    api.setTargetGlobalId(target.getGlobalId());

    InventoryLink saved = linkManager.createLink(api, user);

    assertNotNull(saved.getId());
    assertEquals(GlobalIdPrefix.SA, saved.getTargetPrefix());
    assertEquals(target.getId(), saved.getTargetDbId());
    assertEquals("IsCalibratedBy", saved.getRelationType());
    assertNull(saved.getVersionPin());
  }

  @Test
  public void createLinkToElnDocumentResolvesAndPersists() {
    User user = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "linkable doc");

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId(doc.getOid().getIdString());

    InventoryLink saved = linkManager.createLink(api, user);

    assertNotNull(saved.getId());
    assertEquals(GlobalIdPrefix.SD, saved.getTargetPrefix());
    assertEquals(doc.getId(), saved.getTargetDbId());
  }

  @Test
  public void createLinkExtractsVersionPinFromTargetSuffix() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId(target.getGlobalId() + "v5");

    InventoryLink saved = linkManager.createLink(api, user);

    assertEquals(Long.valueOf(5), saved.getVersionPin());
    assertEquals(target.getId(), saved.getTargetDbId());
  }

  @Test
  public void updateLinkChangesRelationAndPreservesCreatedAt() throws Exception {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target1 = createBasicSampleForUser(user, "target1");
    ApiSampleWithFullSubSamples target2 = createBasicSampleForUser(user, "target2");

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId(target1.getGlobalId());
    InventoryLink saved = linkManager.createLink(api, user);
    java.util.Date originalCreated = saved.getCreatedAt();

    Thread.sleep(5);
    ApiInventoryLink update = new ApiInventoryLink();
    update.setRelationType("IsCalibratedBy");
    update.setTargetGlobalId(target2.getGlobalId());
    InventoryLink updated = linkManager.updateLink(saved, update, user);

    assertEquals("IsCalibratedBy", updated.getRelationType());
    assertEquals(target2.getGlobalId(), updated.getTargetGlobalId());
    assertEquals(target2.getId(), updated.getTargetDbId());
    assertEquals(originalCreated, updated.getCreatedAt());
  }

  @Test
  public void deleteLinkSoftDeletes() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    ApiInventoryLink api = new ApiInventoryLink();
    api.setRelationType("References");
    api.setTargetGlobalId(target.getGlobalId());
    InventoryLink saved = linkManager.createLink(api, user);

    linkManager.deleteLink(saved, user);

    InventoryLink reloaded = linkDao.get(saved.getId());
    assertTrue(reloaded.isDeleted());
  }

  @Test
  public void findReferencingItemsReturnsEmptyWhenNoSources() {
    User user = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(user);

    List<ApiInventoryReferencingItem> rows =
        linkManager.findReferencingItems(target.getGlobalId(), user);

    assertEquals(0, rows.size());
  }

  @Test
  public void findReferencingItemsRejectsTargetTheCallerCannotRead() {
    User owner = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples target = createBasicSampleForUser(owner);
    User stranger = createInitAndLoginAnyUser();

    // same error as a missing record, so the response does not confirm the target exists
    assertThrows(
        ApiRuntimeException.class,
        () -> linkManager.findReferencingItems(target.getGlobalId(), stranger));
  }

  @Test
  public void findReferencingItemsRejectsMissingTarget() {
    User user = createInitAndLoginAnyUser();
    assertThrows(ApiRuntimeException.class, () -> linkManager.findReferencingItems("SA9999", user));
  }
}
