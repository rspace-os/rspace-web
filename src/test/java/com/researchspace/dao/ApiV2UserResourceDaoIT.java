package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.Operator;
import com.researchspace.model.collection.ResourcePage;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.model.collection.Sort;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The users collection query against the real {@code User} mapping.
 *
 * <p>Every field in {@code ApiV2UserResource} is resolved to a Hibernate property by name, so a
 * rename in the entity breaks the Blaze-Persistence query at runtime and nowhere else; no amount of
 * mocking catches that.
 */
class ApiV2UserResourceDaoIT extends SpringTransactionalTest {

  @Autowired private UserDao userDao;

  private static ResourceRequest request(FilterExpression filter, List<Sort> sort) {
    return new ResourceRequest(
        filter, sort, new ResourceRequest.Page(1, 20), FieldSelection.all(), IncludeTree.empty());
  }

  @Test
  @DisplayName("every declared field is a queryable property of the entity")
  void everyDeclaredFieldIsQueryable() {
    createAndSaveUserIfNotExists("collectionqueryuser");

    ApiV2UserResource.DESCRIPTION
        .fields()
        .forEach(
            field ->
                assertTrue(
                    userDao.countUsers(request(null, List.of(new Sort(field.name(), true)))) > 0,
                    "sorting on " + field.name() + " must reach a real property"));
  }

  @Test
  @DisplayName("an id constraint narrows the page and the total together")
  void idConstraintNarrowsPageAndTotal() {
    User target = createAndSaveUserIfNotExists("collectionqueryone");
    createAndSaveUserIfNotExists("collectionquerytwo");

    ResourceRequest scoped =
        request(
            new FilterExpression.Comparison("id", Operator.EQUAL, List.of(target.getId()), false),
            ApiV2UserResource.DESCRIPTION.defaultSort());
    ResourcePage<User> page = userDao.getUsers(scoped);

    assertEquals(1, page.resources().size());
    assertEquals(target.getId(), page.resources().get(0).getId());
    // The total must respect the constraint too, or an ordinary caller's pagination would advertise
    // rows they cannot see.
    assertEquals(1, page.total());
    assertEquals(1L, userDao.countUsers(scoped));
  }

  @Test
  @DisplayName("an unfiltered query still counts, which getQueryRootCountQuery cannot do")
  void unfilteredQueryCounts() {
    createAndSaveUserIfNotExists("collectionqueryunfiltered");

    ResourceRequest unfiltered = request(null, ApiV2UserResource.DESCRIPTION.defaultSort());
    assertEquals(userDao.countUsers(unfiltered), userDao.getUsers(unfiltered).total());
  }

  @Test
  @DisplayName("a missing id is empty rather than an exception, so a get renders 404 not 500")
  void missingIdIsEmpty() {
    // GenericDao.get throws ObjectRetrievalFailureException here, which would escape as a 500.
    assertTrue(userDao.getSafeNull(Long.MAX_VALUE).isEmpty());
  }
}
