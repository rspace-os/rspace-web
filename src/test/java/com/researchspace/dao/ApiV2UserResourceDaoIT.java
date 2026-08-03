package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.User;
import com.researchspace.model.collection.ApiV2UserResource;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.FieldSelection;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.IncludeTree;
import com.researchspace.model.collection.ResourceRequest;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * The users collection query against the real {@code User} mapping.
 *
 * <p>Every field in {@code ApiV2UserResource} is resolved to a Hibernate property by name, so a
 * rename in the entity breaks the Blaze-Persistence query at runtime and nowhere else; no amount of
 * mocking catches that.
 *
 * <p>Named {@code *IT} rather than {@code *Test} because it needs a database. The older Spring
 * {@code *Test} classes are safe under {@code -Dfast=true} only because they use JUnit 4
 * {@code @Test} and the vintage engine is gone, so Jupiter never discovers them. A Jupiter Spring
 * test named {@code *Test} would be discovered and would fail the fast suite trying to load a
 * context.
 */
// SpringTransactionalTest bottoms out in AbstractJUnit4SpringContextTests, whose runner Jupiter
// never invokes, so dependency injection has to be requested explicitly here. Without this the
// inherited @Autowired fields are silently null.
@ExtendWith(SpringExtension.class)
class ApiV2UserResourceDaoIT extends SpringTransactionalTest {

  @Autowired private UserDao userDao;

  private static ResourceRequest request(FilterExpression filter) {
    return new ResourceRequest(
        filter,
        ApiV2UserResource.DESCRIPTION.defaultSort(),
        new ResourceRequest.Page(1, 20),
        FieldSelection.all(),
        IncludeTree.empty());
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
                    userDao.countUsers(sortedBy(field.name())) > 0,
                    "sorting on " + field.name() + " must reach a real property"));
  }

  @Test
  @DisplayName("an id constraint narrows the page and the total together")
  void idConstraintNarrowsPageAndTotal() {
    User target = createAndSaveUserIfNotExists("collectionqueryone");
    createAndSaveUserIfNotExists("collectionquerytwo");

    ResourceRequest scoped =
        request(
            new FilterExpression.Comparison("id", Operator.EQUAL, List.of(target.getId()), false));
    ISearchResults<User> page = userDao.getUsers(scoped);

    assertEquals(1, page.getResults().size());
    assertEquals(target.getId(), page.getResults().get(0).getId());
    // The total must respect the constraint too, or an ordinary caller's pagination would advertise
    // rows they cannot see.
    assertEquals(1, page.getTotalHits().intValue());
    assertEquals(1L, userDao.countUsers(scoped));
  }

  @Test
  @DisplayName("an unfiltered query still counts, which getQueryRootCountQuery cannot do")
  void unfilteredQueryCounts() {
    createAndSaveUserIfNotExists("collectionqueryunfiltered");

    assertEquals(userDao.countUsers(request(null)), userDao.getUsers(request(null)).getTotalHits());
  }

  @Test
  @DisplayName("a missing id is empty rather than an exception, so a get renders 404 not 500")
  void missingIdIsEmpty() {
    // GenericDao.get throws ObjectRetrievalFailureException here, which would escape as a 500.
    assertTrue(userDao.getSafeNull(Long.MAX_VALUE).isEmpty());
  }

  private static ResourceRequest sortedBy(String field) {
    return new ResourceRequest(
        null,
        List.of(new Sort(field, true)),
        new ResourceRequest.Page(1, 20),
        FieldSelection.all(),
        IncludeTree.empty());
  }
}
