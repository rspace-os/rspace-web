package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class CommunityDaoTest extends SpringTransactionalTest {

  @Autowired CommunityDao communityDao;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testBasicSave() {
    User admin = createAndSaveAdminUser();
    logoutAndLoginAs(admin);
    Community community = createAndSaveCommunity(admin, "id1");

    Community reloaded = communityDao.get(community.getId());
    assertEquals(reloaded, community);
    assertTrue(reloaded.getAdmins().contains(admin));
  }

  @Test
  public void testGetAdminsForCommunity() {
    User admin = createAndSaveAdminUser();
    User admin2 = createAndSaveAdminUser();
    User otheradmin = createAndSaveAdminUser();
    Community community = new Community();
    community.setUniqueName("id1");
    community.addAdmin(admin);
    community.addAdmin(admin2);
    communityDao.save(community);
    // now make another community
    logoutAndLoginAs(otheradmin);
    createAndSaveCommunity(otheradmin, "id2");

    List<User> admins = communityDao.listAdminsForCommunity(community.getId());
    assertEquals(2, admins.size());
    assertFalse(admins.contains(otheradmin));
  }

  @Test
  public void testAddRemoveLAbGroup() throws IllegalAddChildOperation {
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    Group group = createGroup("g1", pi);
    group = addUsersToGroup(pi, group, new User[] {});

    User admin2 = createAndSaveAdminUser();
    // create community with admin and a lab group
    Community community = new Community();
    community.addAdmin(admin2);
    community.setUniqueName("id1");

    Community defaultCommunity = communityDao.get(Community.DEFAULT_COMMUNITY_ID);
    defaultCommunity.removeLabGroup(group);
    communityDao.save(defaultCommunity);
    community.addAdmin(admin2);
    community = communityDao.save(community);
    community.addLabGroup(group);
    community = communityDao.save(community);

    // check was persisted
    Community reloaded = communityDao.get(community.getId());
    assertEquals(1, reloaded.getLabGroups().size());
    assertTrue(reloaded.getLabGroups().contains(group));
    // also check that can get community fro m group
    assertEquals(community, communityDao.getCommunityForGroup(group.getId()));
    // now check was removed
    assertTrue(reloaded.removeLabGroup(group));
    communityDao.save(reloaded);
    Community reloaded2 = communityDao.get(community.getId());
    assertEquals(0, reloaded2.getLabGroups().size());

    // test query for getWithLaodedGRoups (this doesn't test fetch strategy
    // as runs in single transaction
    assertNotNull(communityDao.getCommunityWithGroupsAndAdmins(community.getId()));
    group = reloadGroup(group);
    flushDatabaseState();
    // now group is null
    assertNull(communityDao.getCommunityForGroup(group.getId()));
  }

  @Test
  public void testGetCommunitiesForAdmin() {
    User admin = createAndSaveAdminUser();
    // if no acommunity, returns empty result
    List<Community> res = communityDao.listCommunitiesForAdmin(admin.getId());
    assertTrue(res.isEmpty());
    logoutAndLoginAs(admin);
    Community comm = createAndSaveCommunity(admin, "id1");
    List<Community> res2 = communityDao.listCommunitiesForAdmin(admin.getId());
    assertEquals(res2.get(0), comm);
  }

  @Test
  public void testPaginatedList() {
    User admin = createAndSaveAdminUser();
    // maybe there's some already in DB
    final int B4Count = communityDao.getAll().size();
    final int numCommunitesToCreate = 13;
    List<Community> created = createNCommunities(admin, numCommunitesToCreate);

    for (Community comm : created) {
      User otherAdmin = createAndSaveAdminUser();
      comm.addAdmin(otherAdmin);
      communityDao.save(comm);
    }
    // test pagination
    PaginationCriteria<Community> pgcrit =
        PaginationCriteria.createDefaultForClass(Community.class);
    pgcrit.setResultsPerPage(B4Count + numCommunitesToCreate);
    ISearchResults<Community> results = communityDao.listAll(admin, pgcrit);
    assertEquals(B4Count + numCommunitesToCreate, results.getHits().intValue());
    assertEquals(B4Count + numCommunitesToCreate, results.getTotalHits().intValue());
    // contains all results (default community + 13 created here)
    assertTrue(results.getResults().containsAll(created));
  }

  List<Community> createNCommunities(User admin, int n) {
    List<Community> rc = new ArrayList<Community>();
    for (int i = 0; i < n; i++) {
      Community community = new Community();
      community.addAdmin(admin);
      community.setUniqueName("id" + i);
      communityDao.save(community);
      rc.add(community);
    }
    return rc;
  }

  @Test
  public void testHasCommunity() {
    User admin = createAndSaveAdminUser();

    boolean hasCommunity = communityDao.hasCommunity(admin);
    assertFalse(hasCommunity);
    logoutAndLoginAs(admin);
    createAndSaveCommunity(admin, "id1");
    hasCommunity = communityDao.hasCommunity(admin);
    assertTrue(hasCommunity);
  }
}
