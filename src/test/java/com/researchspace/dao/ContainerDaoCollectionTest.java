package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.collection.AccessContext;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.service.inventory.ContainerReadAccess;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ContainerDaoCollectionTest extends SpringTransactionalTest {
  private final ContainerReadAccess access;

  @Autowired
  ContainerDaoCollectionTest(ContainerReadAccess access) {
    this.access = access;
  }

  private AccessResult readable(User user) {
    return access.check(new AccessContext(user, AccessContext.Operation.READ, "containers"));
  }

  private ResourceRequest request(String field, Operator operator, Object value, int size) {
    return new ResourceRequest(
        new FilterExpression.Comparison(field, operator, List.of(value), false),
        List.of(new Sort("name", true), new Sort("id", true)),
        new ResourceRequest.Page(1, size),
        FieldSelection.all(),
        IncludeTree.empty());
  }

  @Test
  public void pagesCountsAndReadsOnlyVisibleActiveContainers() {
    User owner = createInitAndLoginAnyUser();
    var beta = createBasicContainerForUser(owner, "Page Beta");
    var alpha = createBasicContainerForUser(owner, "Page Alpha");
    var trashed = containerDao.get(createBasicContainerForUser(owner, "Page Trashed").getId());
    trashed.setRecordDeleted(true);
    containerDao.save(trashed);
    User outsider = createInitAndLoginAnyUser();
    createBasicContainerForUser(outsider, "Page Private");
    var request = request("name", Operator.CONTAINS, "Page ", 1);
    var page = containerDao.getReadableResources(request, readable(owner));
    assertEquals(2, page.total());
    assertEquals(List.of(alpha.getId()), page.resources().stream().map(Container::getId).toList());
    assertEquals(2, containerDao.countReadableResources(request, readable(owner)));
    assertTrue(
        containerDao
            .getReadableResources(
                request("id", Operator.EQUAL, beta.getId(), 20), readable(outsider))
            .resources()
            .isEmpty());
  }

  @Test
  public void filtersWorkbenchesByTypeAndPreservesTheirGlobalIds() {
    User owner = createInitAndLoginAnyUser();
    Container workbench = containerDao.getWorkbenchForUser(owner);
    var page =
        containerDao.getReadableResources(
            request("containerType", Operator.EQUAL, ContainerType.WORKBENCH, 20), readable(owner));
    assertEquals(
        List.of(workbench.getId()), page.resources().stream().map(Container::getId).toList());
    assertEquals("BE" + workbench.getId(), page.resources().get(0).getGlobalIdentifier());
  }
}
