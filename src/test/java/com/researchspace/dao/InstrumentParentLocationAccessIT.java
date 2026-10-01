package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class InstrumentParentLocationAccessIT extends SpringTransactionalTest {

  @Autowired private InstrumentDao instrumentDao;

  @AfterEach
  void restoreContentInitializer() throws Exception {
    super.tearDown();
  }

  @Test
  public void resolvesDirectContainerAccessForEveryInventoryPermissionBranch() throws Exception {
    User owner =
        createAndSaveUserIfNotExists(getRandomAlphabeticString("owner"), Constants.PI_ROLE);
    User groupMember = createAndSaveUserIfNotExists(getRandomAlphabeticString("member"));
    User outsider = createAndSaveUserIfNotExists(getRandomAlphabeticString("outsider"));
    initialiseContentWithEmptyContent(owner, groupMember, outsider);
    Group group = createGroup(getRandomAlphabeticString("group"), owner);
    addUsersToGroup(owner, group, groupMember);

    ApiContainer owned = createBasicContainerForUser(owner, "Owned", List.of());
    ApiContainer groupShared = createBasicContainerForUser(owner, "Group shared");
    ApiContainer whitelisted = createBasicContainerForUser(outsider, "Whitelisted", List.of(group));
    ApiContainer roleVisible = createBasicContainerForUser(groupMember, "Role visible", List.of());
    Container roleVisibleEntity = containerDao.get(roleVisible.getId());
    roleVisibleEntity.setSharingMode(InventorySharingMode.OWNER_ONLY);
    containerDao.save(roleVisibleEntity);
    sessionFactory.getCurrentSession().flush();

    Set<Long> requested =
        Set.of(owned.getId(), groupShared.getId(), whitelisted.getId(), roleVisible.getId());
    assertEquals(
        requested, containerDao.getReadableActiveContainerIds(requested, getSysAdminUser()));
    assertEquals(requested, containerDao.getReadableActiveContainerIds(requested, owner));
    assertEquals(
        Set.of(groupShared.getId(), whitelisted.getId(), roleVisible.getId()),
        containerDao.getReadableActiveContainerIds(requested, groupMember));
  }

  @Test
  public void grantsAccessThroughEveryImmediateActiveDirectlySharedChildType() throws Exception {
    User actor = createAndSaveUserIfNotExists(getRandomAlphabeticString("actor"));
    User parentOwner = createAndSaveUserIfNotExists(getRandomAlphabeticString("parentOwner"));
    initialiseContentWithEmptyContent(actor, parentOwner);

    Container instrumentParent = privateParent(parentOwner, "Instrument parent");
    Instrument instrument =
        instrumentDao.get(createBasicInstrumentForUser(actor, "Immediate instrument").getId());
    instrument.moveToNewParent(instrumentParent);
    instrumentDao.save(instrument);

    Container containerParent = privateParent(parentOwner, "Container parent");
    Container childContainer =
        containerDao.get(createBasicContainerForUser(actor, "Immediate container").getId());
    childContainer.moveToNewParent(containerParent);
    containerDao.save(childContainer);

    Container subSampleParent = privateParent(parentOwner, "Subsample parent");
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(actor, "Immediate sample");
    Sample sample = sampleDao.get(createdSample.getId());
    SubSample subSample = sample.getSubSamples().get(0);
    subSample.moveToNewParent(subSampleParent);
    sampleDao.save(sample);
    sessionFactory.getCurrentSession().flush();

    Set<Long> requested =
        Set.of(instrumentParent.getId(), containerParent.getId(), subSampleParent.getId());
    assertEquals(requested, containerDao.getReadableActiveContainerIds(requested, actor));
  }

  @Test
  public void rejectsDeletedDetachedIndirectAndRoleVisibleChildren() throws Exception {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User groupMember = createAndSaveUserIfNotExists(getRandomAlphabeticString("member"));
    User parentOwner = createAndSaveUserIfNotExists(getRandomAlphabeticString("parentOwner"));
    initialiseContentWithEmptyContent(pi, groupMember, parentOwner);
    Group group = createGroup(getRandomAlphabeticString("group"), pi);
    addUsersToGroup(pi, group, groupMember);

    Container deletedChildParent = privateParent(parentOwner, "Deleted child parent");
    Instrument deletedChild =
        instrumentDao.get(createBasicInstrumentForUser(pi, "Deleted child").getId());
    deletedChild.moveToNewParent(deletedChildParent);
    deletedChild.setRecordDeleted(true);
    instrumentDao.save(deletedChild);

    Container detachedChildParent = privateParent(parentOwner, "Detached child parent");
    Instrument detachedChild =
        instrumentDao.get(createBasicInstrumentForUser(pi, "Detached child").getId());
    detachedChild.moveToNewParent(detachedChildParent);
    detachedChild.setParentLocation(null);
    instrumentDao.save(detachedChild);

    Container indirectParent = privateParent(parentOwner, "Indirect parent");
    Container indirectChildContainer = privateParent(parentOwner, "Indirect child container");
    indirectChildContainer.moveToNewParent(indirectParent);
    containerDao.save(indirectChildContainer);
    Instrument indirectGrandchild =
        instrumentDao.get(createBasicInstrumentForUser(pi, "Indirect grandchild").getId());
    indirectGrandchild.moveToNewParent(indirectChildContainer);
    instrumentDao.save(indirectGrandchild);

    Container roleVisibleParent = privateParent(parentOwner, "Role-visible child parent");
    Instrument roleVisibleChild =
        instrumentDao.get(createBasicInstrumentForUser(groupMember, "Role-visible child").getId());
    roleVisibleChild.setSharingMode(InventorySharingMode.OWNER_ONLY);
    roleVisibleChild.moveToNewParent(roleVisibleParent);
    instrumentDao.save(roleVisibleChild);
    sessionFactory.getCurrentSession().flush();

    Set<Long> requested =
        Set.of(
            deletedChildParent.getId(),
            detachedChildParent.getId(),
            indirectParent.getId(),
            roleVisibleParent.getId());
    assertTrue(containerDao.getReadableActiveContainerIds(requested, pi).isEmpty());
  }

  @Test
  public void excludesDeletedContainersForTheirOwner() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiContainer created = createBasicContainerForUser(owner, "Deleted parent");
    Container parent = containerDao.get(created.getId());
    parent.setRecordDeleted(true);
    containerDao.save(parent);
    sessionFactory.getCurrentSession().flush();

    assertTrue(
        containerDao.getReadableActiveContainerIds(Set.of(created.getId()), owner).isEmpty());
  }

  private Container privateParent(User owner, String name) {
    ApiContainer created = createBasicContainerForUser(owner, name, List.of());
    Container parent = containerDao.get(created.getId());
    parent.setSharingMode(InventorySharingMode.OWNER_ONLY);
    return containerDao.save(parent);
  }
}
