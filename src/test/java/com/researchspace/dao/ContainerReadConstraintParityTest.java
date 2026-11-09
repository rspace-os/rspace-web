package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.service.inventory.ContainerReadAccess;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContainerReadConstraintParityTest extends SpringTransactionalTest {

  private final InstrumentDao instrumentDao;
  private final CriteriaBuilderFactory criteria;
  private final ContainerReadAccess access;

  @Autowired
  ContainerReadConstraintParityTest(
      InstrumentDao instrumentDao, CriteriaBuilderFactory criteria, ContainerReadAccess access) {
    this.instrumentDao = instrumentDao;
    this.criteria = criteria;
    this.access = access;
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
    assertEquals(requested, readable(requested, getSysAdminUser()));
    assertEquals(requested, readable(requested, owner));
    assertEquals(
        Set.of(groupShared.getId(), whitelisted.getId(), roleVisible.getId()),
        readable(requested, groupMember));
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
    assertEquals(requested, readable(requested, actor));
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
    assertTrue(readable(requested, pi).isEmpty());
  }

  @Test
  public void excludesDeletedContainersForTheirOwner() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiContainer created = createBasicContainerForUser(owner, "Deleted parent");
    Container parent = containerDao.get(created.getId());
    parent.setRecordDeleted(true);
    containerDao.save(parent);
    sessionFactory.getCurrentSession().flush();

    assertTrue(readable(Set.of(created.getId()), owner).isEmpty());
  }

  @Test
  public void workbenchUsesTheSameReadRule() {
    User owner = createInitAndLoginAnyUser();
    Container workbench = containerDao.getWorkbenchForUser(owner);
    assertEquals(Set.of(workbench.getId()), readable(Set.of(workbench.getId()), owner));
  }

  private Set<Long> readable(Set<Long> ids, User user) {
    var description =
        new CollectionDescription<>(
            "containers",
            Container.class,
            List.of(
                Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Container::getId)),
            "id",
            List.of(new Sort("id", true)));
    var executor = new CollectionQueryExecutor<>(Container.class, description, "c");
    var filter = new FilterExpression.Comparison("id", Operator.IN, List.copyOf(ids), false);
    var request =
        new ResourceRequest(
            filter,
            description.defaultSort(),
            new ResourceRequest.Page(1, 100),
            FieldSelection.all(),
            IncludeTree.empty());
    var result = access.check(new AccessContext(user, AccessContext.Operation.READ, "containers"));
    Set<Long> actual =
        executor
            .page(
                criteria,
                sessionFactory.getCurrentSession(),
                request,
                executor.compileConstraint(result.constraintOrEmpty().orElseThrow()))
            .resources()
            .stream()
            .map(Container::getId)
            .collect(Collectors.toSet());
    assertEquals(containerDao.getReadableActiveContainerIds(ids, user), actual);
    return actual;
  }

  private Container privateParent(User owner, String name) {
    ApiContainer created = createBasicContainerForUser(owner, name, List.of());
    Container parent = containerDao.get(created.getId());
    parent.setSharingMode(InventorySharingMode.OWNER_ONLY);
    return containerDao.save(parent);
  }
}
