package com.researchspace.dao.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.blazebit.persistence.CriteriaBuilderFactory;
import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.ResourceRoleMembershipConstraint;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceAudience;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ResourceRoleMembershipQueryTest extends SpringTransactionalTest {

  private final CriteriaBuilderFactory criteria;

  @Autowired
  ResourceRoleMembershipQueryTest(CriteriaBuilderFactory criteria) {
    this.criteria = criteria;
  }

  @Test
  void matchesEachGrantIndependentlyAndRejectsAbsentAndDisabledUsers() throws Exception {
    User directUser = createAndSaveRandomUser();
    User groupUser = createAndSaveRandomUser();
    User audienceUser = createAndSaveRandomUser();
    User outsider = createAndSaveRandomUser();
    User disabledUser = createAndSaveRandomUser();
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi, groupUser);
    Group group = createGroup(getRandomAlphabeticString("group"), pi);
    addUsersToGroup(pi, group, groupUser);
    disabledUser.setEnabled(false);
    userDao.save(disabledUser);

    ResourceAccess direct =
        persist(directUser, ResourceRoleAssignment.forUser("READER", directUser));
    ResourceAccess grouped = persist(pi, ResourceRoleAssignment.forGroup("READER", group));
    ResourceAccess audience =
        persist(
            pi,
            ResourceRoleAssignment.forAudience("READER", ResourceAudience.ALL_USERS, "All users"));
    ResourceAccess disabled = persist(pi, ResourceRoleAssignment.forUser("READER", disabledUser));
    sessionFactory.getCurrentSession().flush();
    Set<Long> resources =
        Set.of(direct.getId(), grouped.getId(), audience.getId(), disabled.getId());

    assertEquals(Set.of(direct.getId()), matching(resources, directUser, Set.of(), false));
    assertEquals(
        Set.of(grouped.getId()), matching(resources, groupUser, Set.of(group.getId()), false));
    assertEquals(Set.of(audience.getId()), matching(resources, audienceUser, Set.of(), true));
    assertEquals(Set.of(), matching(resources, outsider, Set.of(), false));
    assertEquals(Set.of(), matching(resources, disabledUser, Set.of(), false));
  }

  private ResourceAccess persist(User actor, ResourceRoleAssignment assignment) {
    ResourceAccess access = new ResourceAccess("membership-test", actor, new Date());
    access.addAssignment(assignment);
    sessionFactory.getCurrentSession().persist(access);
    return access;
  }

  private Set<Long> matching(Set<Long> resources, User user, Set<Long> groups, boolean allUsers) {
    var description =
        new CollectionDescription<>(
            "resourceAccess",
            ResourceAccess.class,
            List.of(
                Field.readOnly(
                    "id", "id", CollectionFieldTypes.longNumber(), ResourceAccess::getId)),
            "id",
            List.of(new Sort("id", true)));
    var query = criteria.create(sessionFactory.getCurrentSession(), ResourceAccess.class, "access");
    query.where("id").in(resources);
    new RsqlCollectionQuery(description, "access")
        .translateTrusted(
            new ResourceRoleMembershipConstraint(
                "id", user.getId(), groups, Set.of("READER"), allUsers))
        .applyTo(query);
    return query.getResultList().stream().map(ResourceAccess::getId).collect(Collectors.toSet());
  }
}
