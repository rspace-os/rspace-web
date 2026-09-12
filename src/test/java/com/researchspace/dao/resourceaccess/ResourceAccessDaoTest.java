package com.researchspace.dao.resourceaccess;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.booking.service.BookingResourceRoleScheme;
import com.researchspace.model.User;
import com.researchspace.model.resourceaccess.ResourceAccess;
import com.researchspace.model.resourceaccess.ResourceRoleAssignment;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
class ResourceAccessDaoTest extends SpringTransactionalTest {

  @Autowired private ResourceAccessDao resourceAccessDao;

  @Test
  void persistsAssignmentsAndBumpsVersionForSemanticChange() {
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("accessOwner"));
    ResourceAccess access = accessFor(owner);
    sessionFactory.getCurrentSession().persist(access);
    sessionFactory.getCurrentSession().flush();
    long initialVersion = access.getVersion();

    access.touch(owner, new Date(2_000));
    sessionFactory.getCurrentSession().flush();

    assertNotNull(access.getId());
    assertTrue(access.getVersion() > initialVersion);
    assertEquals(1, access.getAssignments().size());
  }

  @Test
  void aggregateRejectsDuplicateGranteeBeforePersistence() {
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("duplicateOwner"));
    ResourceAccess access = accessFor(owner);

    assertThrows(
        IllegalArgumentException.class,
        () ->
            access.addAssignment(
                ResourceRoleAssignment.forUser(BookingResourceRoleScheme.MANAGER, owner)));
  }

  @Test
  void resolvesOnlyLiveServerSidePrincipalKeys() {
    User owner = createAndSaveUserIfNotExists(getRandomAlphabeticString("resolvedOwner"));

    ResourceRoleAssignment resolved =
        resourceAccessDao.resolveAvailable(
            "user:" + owner.getId(), BookingResourceRoleScheme.BOOKER);

    assertEquals(owner.getId(), resolved.getUser().getId());
    assertEquals(BookingResourceRoleScheme.BOOKER, resolved.getRoleKey());
    assertEquals(
        "audience:all-users",
        resourceAccessDao
            .resolveAvailable("audience:all-users", BookingResourceRoleScheme.VIEWER, "All users")
            .getGranteeKey());
    assertNull(
        resourceAccessDao.resolveAvailable("user:not-an-id", BookingResourceRoleScheme.BOOKER));

    User seededSysadmin =
        sessionFactory
            .getCurrentSession()
            .createQuery("from User where username = :username", User.class)
            .setParameter("username", Constants.SYSADMIN_UNAME)
            .getSingleResult();
    assertTrue(seededSysadmin.getId() < 0);
    assertEquals(
        seededSysadmin.getId(),
        resourceAccessDao
            .resolveAvailable("user:" + seededSysadmin.getId(), BookingResourceRoleScheme.MANAGER)
            .getUser()
            .getId());
  }

  private static ResourceAccess accessFor(User owner) {
    ResourceAccess access =
        new ResourceAccess(BookingResourceRoleScheme.SCHEME_KEY, owner, new Date(1_000));
    access.addAssignment(ResourceRoleAssignment.forUser(BookingResourceRoleScheme.OWNER, owner));
    return access;
  }
}
