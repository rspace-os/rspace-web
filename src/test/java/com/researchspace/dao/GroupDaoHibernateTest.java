package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.dtos.GroupSearchCriteria;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.views.UserView;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.Arrays;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class GroupDaoHibernateTest extends SpringTransactionalTest {
  @Autowired GroupDao grpDao;
  @Autowired UserGroupDao ugDao;
  PaginationCriteria<Group> pgCrit;

  @Before
  public void setUp() throws Exception {
    pgCrit = PaginationCriteria.createDefaultForClass(Group.class);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testFindGroupByUname() {
    User u1 = createAndSaveUserIfNotExists("u2", Constants.PI_ROLE);
    String uniqueName = getRandomAlphabeticString("grp");
    assertNull(grpdao.getByUniqueName(uniqueName));
    Group g = new Group(uniqueName, u1);
    g.setGroupType(GroupType.COLLABORATION_GROUP);
    g.addMember(u1, RoleInGroup.PI);
    grpDao.save(g);
    assertNotNull(grpdao.getByUniqueName(uniqueName));
    assertEquals(1, ugDao.findByUserId(u1.getId()).size());
  }

  @Test
  public void testGetByCommunitiresAlsoReturnsGroupsWithNoCommunity() {
    User u1 = createAndSaveUserIfNotExists("u2", Constants.PI_ROLE);
    Group g = new Group("uiwfiwf", u1);
    g.setGroupType(GroupType.COLLABORATION_GROUP);

    grpDao.save(g);

    Group g2 = grpdao.getGroupWithCommunities(g.getId());
    assertNotNull(g2);
  }

  @Test
  public void testListByFilter() throws IllegalAddChildOperation, InterruptedException {
    GroupSearchCriteria filter = new GroupSearchCriteria();
    pgCrit.setSearchCriteria(filter);
    assertEquals(0, grpDao.list(pgCrit).getTotalHits().intValue());
    User u1 = createAndSaveUserIfNotExists("u1", Constants.PI_ROLE);
    User u2 = createAndSaveUserIfNotExists("u2", Constants.PI_ROLE);

    Group grp = createGroup("group", u1);
    Group grp2 = createGroup("group2", u2);
    Group collabGroup = createGroup("collab1", u1);
    collabGroup.setGroupType(GroupType.COLLABORATION_GROUP);
    collabGroup.setDisplayName("collabDisplay");
    grpDao.save(collabGroup);

    filter.setUniqueName("group");
    assertEquals(2, grpDao.list(pgCrit).getTotalHits().intValue());
    assertEquals(grp, grpDao.list(pgCrit).getResults().get(0));

    // reset, should get all groups
    filter.reset();
    assertEquals(3, grpDao.list(pgCrit).getTotalHits().intValue());

    // filter by group type:
    filter.reset();
    filter.setGroupType(GroupType.COLLABORATION_GROUP);
    assertEquals(1, grpDao.list(pgCrit).getTotalHits().intValue());
    assertEquals(collabGroup, grpDao.list(pgCrit).getResults().get(0));

    // filter by display name partial
    filter.reset();
    filter.setDisplayName("labDis");
    pgCrit.setSearchCriteria(filter);
    assertEquals(1, grpDao.list(pgCrit).getTotalHits().intValue());
    assertEquals(collabGroup, grpDao.list(pgCrit).getResults().get(0));

    // reset, check order by owner's name
    filter.reset();
    pgCrit.setOrderBy("owner.username");
    pgCrit.setSortOrder(SortOrder.DESC);
    assertEquals(u2, grpDao.list(pgCrit).getFirstResult().getOwner());
    pgCrit.setSortOrder(SortOrder.ASC);
    assertEquals(u1, grpDao.list(pgCrit).getFirstResult().getOwner());

    // check pagination
    for (int i = 0; i < 12; i++) {
      createGroup(getRandomAlphabeticString("grp") + i, u1);
      Thread.sleep(1);
    }
    filter.reset();
    filter.setGroupType(GroupType.LAB_GROUP);
    filter.setLoadCommunity(true);
    pgCrit.setResultsPerPage(10);
    ISearchResults<Group> grps = grpDao.list(pgCrit);

    assertEquals(10, grps.getResults().size());

    Group grpX = grpDao.getGroupWithCommunities(grp.getId());
    // tests query syntax; avoidance of lazyloading needs truemulti-transaction test
    assertNotNull(grpX.getCommunity().getDisplayName());
  }

  @Test
  public void testGetByIdList() throws IllegalAddChildOperation {
    User u1 = createAndSaveUserIfNotExists("u1", Constants.PI_ROLE);
    User u2 = createAndSaveUserIfNotExists("u2", Constants.PI_ROLE);

    Group grp = createGroup("group", u1);
    Group grp2 = createGroup("group2", u2);
    List<Group> results = grpDao.getGroups(Arrays.asList(new Long[] {grp2.getId(), grp.getId()}));
    assertEquals(2, results.size());
  }

  @Test
  public void testFindGroup() throws IllegalAddChildOperation {
    User u1 = createAndSaveUserIfNotExists("u1", Constants.PI_ROLE);
    User u2 = createAndSaveUserIfNotExists("u2");
    User u3 = createAndSaveUserIfNotExists("u3", Constants.PI_ROLE);
    User u4 = createAndSaveUserIfNotExists("u4");
    User u5 = createAndSaveUserIfNotExists("u5", Constants.PI_ROLE);
    User u6 = createAndSaveUserIfNotExists("u6");

    initialiseContentWithEmptyContent(u1);
    initialiseContentWithEmptyContent(u2);
    initialiseContentWithEmptyContent(u3);
    initialiseContentWithEmptyContent(u4);
    initialiseContentWithEmptyContent(u5);
    initialiseContentWithEmptyContent(u6);

    Group lg1 = createGroup("lg1", u1);
    addUsersToGroup(u1, lg1, u2);
    Group lg2 = createGroup("lg2", u3);
    addUsersToGroup(u3, lg2, u4);
    Group unrelatedLabGroup = createGroup("unrelated", u5);
    addUsersToGroup(u5, unrelatedLabGroup, u6);

    Group cg1 = createGroup("cg1", u1);
    cg1.setGroupType(GroupType.COLLABORATION_GROUP);
    cg1.addMember(u1, RoleInGroup.PI);
    cg1.addMember(u3, RoleInGroup.PI);
    grpDao.save(cg1);

    // flushDatabaseState();
    // so cg has 2 members; u2 and u4 should be available to add
    // but not u5, who does not exist in a group
    List<UserView> users = grpDao.getCandidateMembersOfCollabGroup(cg1.getId());
    assertEquals(2, users.size());
    // these are the two lab group members that aren't currently in the CG.
    assertTrue(userViewHasUser(u2, users));
    assertTrue(userViewHasUser(u4, users));
  }

  private boolean userViewHasUser(User user, List<UserView> users) {
    return users.stream()
        .filter(uv -> uv.getUniqueName().equals(user.getUsername()))
        .findFirst()
        .isPresent();
  }
}
