package com.researchspace.dao;

import static com.researchspace.core.util.TransformerUtils.toList;
import static com.researchspace.testutils.matchers.TotalSearchResults.totalSearchResults;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.researchspace.Constants;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.SortOrder;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.dtos.SharedRecordSearchCriteria;
import com.researchspace.model.record.ObjectToIdPropertyTransformer;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.TestFactory;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class RecordSharingDaoTest extends BaseDaoTestCase {

  private @Autowired RecordDao recordDao;
  private @Autowired RecordGroupSharingDao rShareDao;
  private @Autowired GroupDao grpDao;

  private User user;

  @Before
  public void setUp() throws Exception {
    String randomName = CoreTestUtils.getRandomName(10);
    user = createAndSaveUserIfNotExists(randomName, Constants.PI_ROLE);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testIsRecordAlreadySharedInGroup() {
    Group grp = TestFactory.createAnyGroup(user, new User[] {});
    grp.setUniqueName("any");
    grp = grpDao.save(grp);
    Record anyRecord = TestFactory.createAnyRecord(user);

    recordDao.save(anyRecord);

    boolean isShared = rShareDao.isRecordAlreadySharedInGroup(grp.getId(), anyRecord.getId());
    assertFalse(isShared);
    RecordGroupSharing rgs = new RecordGroupSharing(grp, anyRecord);
    assertNull(rgs.getCreationDate());
    rgs = rShareDao.save(rgs);
    flush(); // this is needed to get hibernate to set the creation date using
    // @CreationTimestamp
    assertNotNull(rgs.getCreationDate());
    isShared = rShareDao.isRecordAlreadySharedInGroup(grp.getId(), anyRecord.getId());
    assertTrue(isShared);
    // this methods returns the shared records, not objects
    assertEquals(
        anyRecord,
        rShareDao.findRecordsSharedWithUserOrGroup(grp.getId(), toList(anyRecord.getId())).get(0));
    // unshared record id returns empty set
    assertEquals(0, rShareDao.findRecordsSharedWithUserOrGroup(grp.getId(), toList(-200L)).size());
  }

  @Test
  public void testSharedRecordByUserAndGroup() {
    // create 2 groups, share with both only
    Group grp = TestFactory.createAnyGroup(user, new User[] {});
    grp.setUniqueName("any1");
    grp = grpDao.save(grp);
    Group grp2 = TestFactory.createAnyGroup(user, new User[] {});
    grp2.setUniqueName("any2");
    grp2 = grpDao.save(grp2);
    Record anyRecord = TestFactory.createAnyRecord(user);

    recordDao.save(anyRecord);
    RecordGroupSharing rgs1 = new RecordGroupSharing(grp, anyRecord);
    rShareDao.save(rgs1);
    RecordGroupSharing rgs2 = new RecordGroupSharing(grp2, anyRecord);
    rShareDao.save(rgs2);

    assertEquals(1, rShareDao.getRecordsSharedByUserToGroup(user, grp2).size());
    assertEquals(1, rShareDao.getRecordsSharedByUserToGroup(user, grp).size());

    // but only 1 record shared
    assertEquals(1, rShareDao.getSharedRecordsForUser(user).size());

    // when asked about all users or groups with access should return both
    assertEquals(2, rShareDao.getUsersOrGroupsWithRecordAccess(anyRecord.getId()).size());

    // when asked about sharing status should also return both groups
    assertEquals(2, rShareDao.getRecordGroupSharingsForRecord(anyRecord.getId()).size());
  }

  @Test
  public void listRecords() throws InterruptedException {
    Group grp2 = TestFactory.createAnyGroup(user, new User[] {});
    grp2.setUniqueName("any2");
    grp2 = grpDao.save(grp2);

    // ensure creationDate and name sort are distinguishable

    RecordGroupSharing rgs = createAndShareRecord(grp2, "xyyyz");
    flush();
    Thread.sleep(1001);
    RecordGroupSharing rg2s = createAndShareRecord(grp2, "abcdyyyefgh");
    flush();

    Thread.sleep(1001);
    RecordGroupSharing rg3s = createAndShareRecord(grp2, "mmyyymmm");

    PaginationCriteria<RecordGroupSharing> pg =
        PaginationCriteria.createDefaultForClass(RecordGroupSharing.class);
    SharedRecordSearchCriteria srchCrit = new SharedRecordSearchCriteria();
    srchCrit.setAllFields("abcd");
    pg.setSearchCriteria(srchCrit);

    assertThat(rShareDao.listSharedRecordsForUser(user, pg), totalSearchResults(1));
    srchCrit.setAllFields("bcdefddddd");
    assertThat(rShareDao.listSharedRecordsForUser(user, pg), totalSearchResults(0));
    String globalIdString = rgs.getShared().getGlobalIdentifier();
    srchCrit.setAllFields(rgs.getShared().getGlobalIdentifier());
    assertThat(rShareDao.listSharedRecordsForUser(user, pg), totalSearchResults(1));
    // requires exact match to global ID
    srchCrit.setAllFields(StringUtils.abbreviate(globalIdString, globalIdString.length() - 1));
    assertThat(rShareDao.listSharedRecordsForUser(user, pg), totalSearchResults(0));

    srchCrit.setAllFields(globalIdString + "ddd");
    assertThat(rShareDao.listSharedRecordsForUser(user, pg), totalSearchResults(0));

    srchCrit.setAllFields("");
    assertThat(rShareDao.listSharedRecordsForUser(user, pg), totalSearchResults(3));
    // search name asc/desc
    pg.setSortOrder(SortOrder.DESC);
    assertFirstHitNameStartsWith(pg, "x");
    pg.setSortOrder(SortOrder.ASC);
    assertFirstHitNameStartsWith(pg, "a");

    // now include search as well
    srchCrit.setAllFields("yyy"); // matches all names
    pg.setSortOrder(SortOrder.DESC);
    assertFirstHitNameStartsWith(pg, "x");
    pg.setSortOrder(SortOrder.ASC);
    assertFirstHitNameStartsWith(pg, "a");
    // rspac-1887
    pg.setOrderBy("creationDate");
    assertFirstHitNameStartsWith(pg, "x");
    pg.setSortOrder(SortOrder.DESC);
    assertFirstHitNameStartsWith(pg, "m");
  }

  private RecordGroupSharing createAndShareRecord(Group grp, String docname) {
    Record anyRecord = TestFactory.createAnyRecord(user);
    anyRecord.setName(docname);
    recordDao.save(anyRecord);
    RecordGroupSharing rgs1 = new RecordGroupSharing(grp, anyRecord);
    return rShareDao.save(rgs1);
  }

  private void assertFirstHitNameStartsWith(
      PaginationCriteria<RecordGroupSharing> pg, String prefix) {
    assertTrue(
        rShareDao
            .listSharedRecordsForUser(user, pg)
            .getFirstResult()
            .getShared()
            .getName()
            .startsWith(prefix));
  }

  @Test
  public void identifySharedRecords() {

    Group grp2 = TestFactory.createAnyGroup(user, new User[] {});
    grp2.setUniqueName("any2");
    grp2 = grpDao.save(grp2);
    Record r = TestFactory.createAnyRecord(user);

    recordDao.save(r);
    List<Long> recordIds =
        toList(r).stream().map(new ObjectToIdPropertyTransformer()).collect(Collectors.toList());
    List<Long> ids = rShareDao.findSharedRecords(recordIds);
    assertTrue(ids.isEmpty());

    RecordGroupSharing rgs2 = new RecordGroupSharing(grp2, r);
    rShareDao.save(rgs2);
    assertNotNull(rShareDao.get(rgs2.getId()));
    // assertEquals(1, rShareDao.getRecordsSharedByGroup(grp2.getId()));
    ids = rShareDao.findSharedRecords(recordIds);
    assertTrue(ids.size() == 1);
    assertEquals(ids.get(0), r.getId());
  }
}
