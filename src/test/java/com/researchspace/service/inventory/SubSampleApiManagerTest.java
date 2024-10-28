package com.researchspace.service.inventory;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.SearchUtils;
import com.researchspace.Constants;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiInventoryEditLock;
import com.researchspace.api.v1.model.ApiInventoryEditLock.ApiInventoryEditLockStatus;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleInfo;
import com.researchspace.api.v1.model.ApiSubSampleInfoWithSampleInfo;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.api.v1.model.ApiSubSampleSearchResult;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.SubSampleDao;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.events.InventoryAccessEvent;
import com.researchspace.model.events.InventoryCreationEvent;
import com.researchspace.model.events.InventoryDeleteEvent;
import com.researchspace.model.events.InventoryEditingEvent;
import com.researchspace.model.events.InventoryMoveEvent;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.service.inventory.impl.SubSampleDuplicateConfig;
import com.researchspace.testutils.SpringTransactionalTest;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

public class SubSampleApiManagerTest extends SpringTransactionalTest {

  private ApplicationEventPublisher mockPublisher;

  private User testUser;
  private @Autowired SubSampleDao dao;

  @Before
  public void setUp() throws Exception {
    super.setUp();

    sampleDao.resetDefaultTemplateOwner();
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());

    mockPublisher = Mockito.mock(ApplicationEventPublisher.class);
    sampleApiMgr.setPublisher(mockPublisher);
    subSampleApiMgr.setPublisher(mockPublisher);
  }

  @Test
  public void subSampleRetrievalAndPagination() {
    /* let's create another user, as first one ever created may be creator of
     * default templates which would break pagination assertions */
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);

    // create two samples with 6 subsamples each
    ApiSampleWithFullSubSamples newSample1 = new ApiSampleWithFullSubSamples("mySample 1");
    newSample1.setSubSamples(Collections.nCopies(6, new ApiSubSample()));
    sampleApiMgr.createNewApiSample(newSample1, testUser);

    ApiSampleWithFullSubSamples newSample2 = new ApiSampleWithFullSubSamples("mySample 2");
    newSample2.setSubSamples(Collections.nCopies(6, new ApiSubSample()));
    sampleApiMgr.createNewApiSample(newSample2, testUser);

    // default pagination criteria
    ApiSubSampleSearchResult defaultSubSamplesResult =
        subSampleApiMgr.getSubSamplesForUser(null, null, null, testUser);
    assertEquals(12, defaultSubSamplesResult.getTotalHits().intValue());
    assertEquals(10, defaultSubSamplesResult.getSubSamples().size());
    assertEquals(0, defaultSubSamplesResult.getPageNumber().intValue());
    assertEquals("mySample 2.06", defaultSubSamplesResult.getSubSamples().get(0).getName());
    assertEquals("mySample 1.03", defaultSubSamplesResult.getSubSamples().get(9).getName());

    // delete one of the subsamples
    subSampleApiMgr.markSubSampleAsDeleted(
        defaultSubSamplesResult.getSubSamples().get(0).getId(), testUser, false);

    // try retrieving all active subsamples
    PaginationCriteria<SubSample> pgCrit =
        PaginationCriteria.createDefaultForClass(SubSample.class);
    pgCrit.setGetAllResults();
    ApiSubSampleSearchResult allSubSamplesResult =
        subSampleApiMgr.getSubSamplesForUser(pgCrit, null, null, testUser);
    assertEquals(11, allSubSamplesResult.getTotalHits().intValue());
    assertEquals(11, allSubSamplesResult.getSubSamples().size());
    assertEquals(0, allSubSamplesResult.getPageNumber().intValue());
    assertEquals("mySample 2.05", allSubSamplesResult.getSubSamples().get(0).getName());
    assertEquals("mySample 1.01", allSubSamplesResult.getSubSamples().get(10).getName());

    // ordered by global id asc, including deleted items
    pgCrit.setOrderBy(SearchUtils.ORDER_BY_GLOBAL_ID);
    pgCrit.setSortOrder(SortOrder.ASC);
    allSubSamplesResult =
        subSampleApiMgr.getSubSamplesForUser(
            pgCrit, null, InventorySearchDeletedOption.INCLUDE, testUser);
    assertEquals(12, allSubSamplesResult.getTotalHits().intValue());
    assertEquals(12, allSubSamplesResult.getSubSamples().size());
    assertEquals(0, allSubSamplesResult.getPageNumber().intValue());
    assertEquals("mySample 1.01", allSubSamplesResult.getSubSamples().get(0).getName());
    assertEquals("mySample 2.06", allSubSamplesResult.getSubSamples().get(11).getName());
  }

  @Test
  public void groupOwnedSubSampleVisibilityInsideAndOutsideGroup() {

    // create a pi, with a group
    User pi = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, testUser);

    // add the group to a new community managed by commAdmin
    User commAdmin = createAndSaveAdminUser();
    Community comm = createAndSaveCommunity(commAdmin, getRandomAlphabeticString("newCommunity"));
    logoutAndLoginAs(commAdmin);
    communityMgr.addGroupToCommunity(group.getId(), comm.getId(), commAdmin);

    // create user outside group
    User otherUser = createAndSaveUserIfNotExists("other");
    initialiseContentWithEmptyContent(otherUser);

    // create basic sample for each user
    ApiSubSample testUserSubSample =
        createBasicSampleForUser(testUser, "testUser's sample").getSubSamples().get(0);
    ApiSubSample piSubSample = createBasicSampleForUser(pi, "pi's sample").getSubSamples().get(0);
    ApiSubSample otherUserSubSample =
        createBasicSampleForUser(otherUser, "otherUser's sample").getSubSamples().get(0);

    /*
     * check visibility for 'testUser' group member
     */

    // testUser see pi's subsample in default listing
    ApiSubSampleSearchResult userSubSamplesResult =
        subSampleApiMgr.getSubSamplesForUser(null, null, null, testUser);
    assertEquals(2, userSubSamplesResult.getTotalHits().intValue());
    ApiSubSampleInfo retrievedTestUserSubSample = userSubSamplesResult.getSubSamples().get(0);
    assertEquals(testUserSubSample.getName(), retrievedTestUserSubSample.getName());
    ApiSubSampleInfo retrievedPiSubSample = userSubSamplesResult.getSubSamples().get(1);
    assertEquals(piSubSample.getName(), retrievedPiSubSample.getName());
    // testUser can query full details of pi's subsample
    ApiSubSample fullPiSubSample =
        subSampleApiMgr.getApiSubSampleById(retrievedPiSubSample.getId(), testUser);
    assertFalse(fullPiSubSample.isClearedForPublicView());
    // testUser have update permission to pi's subsample
    assertEquals(2, fullPiSubSample.getPermittedActions().size());
    // testUser can only see public details of other user's subsample
    ApiSubSample subSampleRetrievedByTestUser =
        subSampleApiMgr.getApiSubSampleById(otherUserSubSample.getId(), testUser);
    assertTrue(subSampleRetrievedByTestUser.isClearedForPublicView());

    /*
     * check visibility for 'pi' group member
     */

    // pi see user's subsample in default listing
    ApiSubSampleSearchResult piSubSamplesResult =
        subSampleApiMgr.getSubSamplesForUser(null, null, null, pi);
    assertEquals(2, piSubSamplesResult.getTotalHits().intValue());
    retrievedTestUserSubSample = piSubSamplesResult.getSubSamples().get(0);
    assertEquals(testUserSubSample.getName(), retrievedTestUserSubSample.getName());
    retrievedPiSubSample = piSubSamplesResult.getSubSamples().get(1);
    assertEquals(piSubSample.getName(), retrievedPiSubSample.getName());
    // pi can query full details of testUsers's subsample
    ApiSubSample fullTestUserSubSample =
        subSampleApiMgr.getApiSubSampleById(retrievedTestUserSubSample.getId(), pi);
    assertEquals(testUserSubSample.getName(), fullTestUserSubSample.getName());
    // pi have update and transfer permission to user's subsample
    assertEquals(3, fullTestUserSubSample.getPermittedActions().size());
    // pi only see public details of other user's subsample
    ApiSubSample subSampleRetrievedByPi =
        subSampleApiMgr.getApiSubSampleById(otherUserSubSample.getId(), pi);
    assertTrue(subSampleRetrievedByPi.isClearedForPublicView());

    /*
     * check visibility for 'other user' who is outside any group
     */

    // other user only see own subsample in default listing
    ApiSubSampleSearchResult otherUserSamplesResult =
        subSampleApiMgr.getSubSamplesForUser(null, null, null, otherUser);
    assertEquals(1, otherUserSamplesResult.getTotalHits().intValue());
    assertEquals(
        otherUserSubSample.getName(), otherUserSamplesResult.getSubSamples().get(0).getName());
    // will only have public details for other subsample
    ApiSubSample subSampleRetrievedByOtherUser =
        subSampleApiMgr.getApiSubSampleById(testUserSubSample.getId(), otherUser);
    assertTrue(subSampleRetrievedByOtherUser.isClearedForPublicView());
    subSampleRetrievedByOtherUser =
        subSampleApiMgr.getApiSubSampleById(piSubSample.getId(), otherUser);
    assertTrue(subSampleRetrievedByOtherUser.isClearedForPublicView());

    /*
     * check visibility for community admin administering the group
     */

    // community admin see all subsamples in the community
    ApiSubSampleSearchResult commAdminSubSamplesResult =
        subSampleApiMgr.getSubSamplesForUser(null, null, null, commAdmin);
    assertEquals(2, commAdminSubSamplesResult.getTotalHits().intValue());
    // community admin can limit to individual user's subsample
    commAdminSubSamplesResult =
        subSampleApiMgr.getSubSamplesForUser(null, testUser.getUsername(), null, commAdmin);
    assertEquals(1, commAdminSubSamplesResult.getTotalHits().intValue());
    assertEquals(
        testUserSubSample.getName(), commAdminSubSamplesResult.getSubSamples().get(0).getName());
    // community admin can query details of a subsample within community
    fullTestUserSubSample =
        subSampleApiMgr.getApiSubSampleById(testUserSubSample.getId(), commAdmin);
    assertEquals(testUserSubSample.getName(), fullTestUserSubSample.getName());
    // community admin only have read access to subsample within community
    assertEquals(1, fullTestUserSubSample.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.READ,
        fullTestUserSubSample.getPermittedActions().get(0));
    // community admin will only have public details for subsample of user outside the community
    ApiSubSample subSampleRetrievedByCommAdmin =
        subSampleApiMgr.getApiSubSampleById(otherUserSubSample.getId(), commAdmin);
    assertTrue(subSampleRetrievedByCommAdmin.isClearedForPublicView());

    /*
     * check visibility for system admin
     */
    User sysadmin = createAndSaveSysadminUser();
    // system admin see all subsamples in the system
    ApiSubSampleSearchResult systemAdminSubSamplesResult =
        subSampleApiMgr.getSubSamplesForUser(null, null, null, sysadmin);
    assertTrue(systemAdminSubSamplesResult.getTotalHits().intValue() > 2);
    // system admin can limit to individual user's subsamples
    systemAdminSubSamplesResult =
        subSampleApiMgr.getSubSamplesForUser(null, testUser.getUsername(), null, sysadmin);
    assertEquals(1, systemAdminSubSamplesResult.getTotalHits().intValue());
    assertEquals(
        testUserSubSample.getName(), systemAdminSubSamplesResult.getSubSamples().get(0).getName());
    // system admin can query details of any subsample
    fullTestUserSubSample =
        subSampleApiMgr.getApiSubSampleById(testUserSubSample.getId(), sysadmin);
    assertEquals(testUserSubSample.getName(), fullTestUserSubSample.getName());
    ApiSubSample fullOtherUserSubSample =
        subSampleApiMgr.getApiSubSampleById(otherUserSubSample.getId(), sysadmin);
    assertEquals(otherUserSubSample.getName(), fullOtherUserSubSample.getName());
    // system admin have only read/transfer access to any subsample
    assertEquals(2, fullOtherUserSubSample.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.READ,
        fullOtherUserSubSample.getPermittedActions().get(0));
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.CHANGE_OWNER,
        fullOtherUserSubSample.getPermittedActions().get(1));
  }

  @Test
  public void whitelistedSubSampleVisibilityInGroups() {
    // create users and two groups
    User pi1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User pi2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi2"), Constants.PI_ROLE);
    User user1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user1"));
    User user2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user2"));
    User user3 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user3"));
    User user4 = createAndSaveUserIfNotExists(getRandomAlphabeticString("user4"));
    initialiseContentWithEmptyContent(pi1, pi2, user1, user2, user3, user4);

    Group groupA = createGroup("groupA", pi1);
    addUsersToGroup(pi1, groupA, user1, user2);
    Group groupB = createGroup("groupB", pi2);
    addUsersToGroup(pi2, groupB, user3, user4);

    // add lab admins with and without view all permissions to group1
    User labAdminForGroupA = createAndSaveUserIfNotExists("labAdmin");
    User labAdminForGroupAWithViewAll = createAndSaveUserIfNotExists("labAdminWithViewAll1");
    initialiseContentWithEmptyContent(labAdminForGroupA, labAdminForGroupAWithViewAll);
    logoutAndLoginAs(pi1);
    grpMgr.addMembersToGroup(
        groupA.getId(),
        Arrays.asList(new User[] {labAdminForGroupA}),
        "",
        labAdminForGroupA.getUsername(),
        pi1);
    grpMgr.addMembersToGroup(
        groupA.getId(),
        Arrays.asList(new User[] {labAdminForGroupAWithViewAll}),
        "",
        labAdminForGroupAWithViewAll.getUsername(),
        pi1);
    labAdminForGroupAWithViewAll =
        grpMgr.authorizeLabAdminToViewAll(
            labAdminForGroupAWithViewAll.getId(), pi1, groupA.getId(), true);

    // create (sub)sample owned by user1, whitelisted just for groupB
    ApiSubSampleInfo user1SubSample =
        createBasicSampleForUser(user1, "user1 sample", "user1 subsample", List.of(groupB))
            .getSubSamples()
            .get(0);
    assertNotNull(user1SubSample.getGlobalId());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST, user1SubSample.getSharingMode());
    // create (sub)sample owned by user3, group-shared by default
    ApiSubSampleInfo user3SubSample =
        createBasicSampleForUser(user3, "user3 sample", "user3 subsample", null)
            .getSubSamples()
            .get(0);
    assertNotNull(user3SubSample.getGlobalId());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.OWNER_GROUPS,
        user3SubSample.getSharingMode());
    // create (sub)sample owned by user4, whitelisted as private
    ApiSubSampleInfo user4SubSample =
        createBasicSampleForUser(
                user4, "user4 private sample", "user4 private subsample", List.of())
            .getSubSamples()
            .get(0);
    assertNotNull(user4SubSample.getGlobalId());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST, user4SubSample.getSharingMode());

    // user1 should find own subsample fine
    ApiSubSampleSearchResult visibleSubSamples =
        subSampleApiMgr.getSubSamplesForUser(
            PaginationCriteria.createDefaultForClass(SubSample.class), null, null, user1);
    assertEquals(1, visibleSubSamples.getTotalHits());
    assertEquals(
        user1SubSample.getGlobalId(), visibleSubSamples.getSubSamples().get(0).getGlobalId());
    assertEquals(
        3,
        visibleSubSamples
            .getSubSamples()
            .get(0)
            .getPermittedActions()
            .size()); // read/edit/transfer

    // user2 is in groupA, shouldn't see any subsamples
    visibleSubSamples =
        subSampleApiMgr.getSubSamplesForUser(
            PaginationCriteria.createDefaultForClass(SubSample.class), null, null, user2);
    assertEquals(0, visibleSubSamples.getTotalHits());

    // user3 is in groupB, should see own subsample and user1's which is whitelisted for groupB
    visibleSubSamples =
        subSampleApiMgr.getSubSamplesForUser(
            PaginationCriteria.createDefaultForClass(SubSample.class), null, null, user3);
    assertEquals(2, visibleSubSamples.getTotalHits());
    assertEquals(
        user3SubSample.getGlobalId(), visibleSubSamples.getSubSamples().get(0).getGlobalId());
    assertEquals(
        3,
        visibleSubSamples
            .getSubSamples()
            .get(0)
            .getPermittedActions()
            .size()); // read/edit/transfer
    assertEquals(
        user1SubSample.getGlobalId(), visibleSubSamples.getSubSamples().get(1).getGlobalId());
    assertEquals(
        2, visibleSubSamples.getSubSamples().get(1).getPermittedActions().size()); // read/update

    // user4 is in groupB, should see own subsample, user3's which is group-shared, and user1's
    // which is whitelisted for groupB
    visibleSubSamples =
        subSampleApiMgr.getSubSamplesForUser(
            PaginationCriteria.createDefaultForClass(SubSample.class), null, null, user4);
    assertEquals(3, visibleSubSamples.getTotalHits());
    assertEquals(
        user4SubSample.getGlobalId(), visibleSubSamples.getSubSamples().get(0).getGlobalId());
    assertEquals(
        3,
        visibleSubSamples
            .getSubSamples()
            .get(0)
            .getPermittedActions()
            .size()); // read/edit/transfer
    assertEquals(
        user3SubSample.getGlobalId(), visibleSubSamples.getSubSamples().get(1).getGlobalId());
    assertEquals(
        2, visibleSubSamples.getSubSamples().get(1).getPermittedActions().size()); // read/update
    assertEquals(
        user1SubSample.getGlobalId(), visibleSubSamples.getSubSamples().get(2).getGlobalId());
    assertEquals(
        2, visibleSubSamples.getSubSamples().get(2).getPermittedActions().size()); // read/update

    // pi1 should see user1's subsample, because they are the user's PI
    visibleSubSamples =
        subSampleApiMgr.getSubSamplesForUser(
            PaginationCriteria.createDefaultForClass(SubSample.class), null, null, pi1);
    assertEquals(1, visibleSubSamples.getTotalHits());
    assertEquals(
        user1SubSample.getGlobalId(), visibleSubSamples.getSubSamples().get(0).getGlobalId());
    assertEquals(
        2, visibleSubSamples.getSubSamples().get(0).getPermittedActions().size()); // read/transfer

    // pi2 should see user3's and user4's subsample, because they are PI of their group, and user1's
    // subsample, as it's whitelisted
    visibleSubSamples =
        subSampleApiMgr.getSubSamplesForUser(
            PaginationCriteria.createDefaultForClass(SubSample.class), null, null, pi2);
    assertEquals(3, visibleSubSamples.getTotalHits());
    assertEquals(
        user4SubSample.getGlobalId(), visibleSubSamples.getSubSamples().get(0).getGlobalId());
    assertEquals(
        2, visibleSubSamples.getSubSamples().get(0).getPermittedActions().size()); // read/transfer
    assertEquals(
        user3SubSample.getGlobalId(), visibleSubSamples.getSubSamples().get(1).getGlobalId());
    assertEquals(
        3,
        visibleSubSamples
            .getSubSamples()
            .get(1)
            .getPermittedActions()
            .size()); // read/edit/transfer
    assertEquals(
        user1SubSample.getGlobalId(), visibleSubSamples.getSubSamples().get(2).getGlobalId());
    assertEquals(
        2, visibleSubSamples.getSubSamples().get(2).getPermittedActions().size()); // read/edit

    // labAdmin is treated as a regular member of groupA, shouldn't see any subsamples
    visibleSubSamples =
        subSampleApiMgr.getSubSamplesForUser(
            PaginationCriteria.createDefaultForClass(SubSample.class),
            null,
            null,
            labAdminForGroupA);
    assertEquals(0, visibleSubSamples.getTotalHits());

    // labAdminWithViewAll1 should see user1's subsample, same as PI of group1
    visibleSubSamples =
        subSampleApiMgr.getSubSamplesForUser(
            PaginationCriteria.createDefaultForClass(SubSample.class),
            null,
            null,
            labAdminForGroupAWithViewAll);
    assertEquals(1, visibleSubSamples.getTotalHits());
    assertEquals(
        user1SubSample.getGlobalId(), visibleSubSamples.getSubSamples().get(0).getGlobalId());
    assertEquals(
        2, visibleSubSamples.getSubSamples().get(0).getPermittedActions().size()); // read/transfer
  }

  @Test
  public void templateSubSampleIgnoredBySearch() {

    User user = createInitAndLoginAnyUser();

    // create two samples, one from newly created template
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(user);
    ApiSampleWithFullSubSamples sampleFromCustomTemplate = createBasicSampleTemplateAndSample(user);

    // search for all subsamples should match subsamples of both samples, but not one from template
    // (PRT-553)
    PaginationCriteria<SubSample> pgCrit =
        PaginationCriteria.createDefaultForClass(SubSample.class);
    pgCrit.setGetAllResults();
    ApiSubSampleSearchResult allSubSamplesResult =
        subSampleApiMgr.getSubSamplesForUser(pgCrit, null, null, user);
    assertEquals(2, allSubSamplesResult.getTotalHits().intValue());
    assertEquals(2, allSubSamplesResult.getSubSamples().size());
    assertEquals(0, allSubSamplesResult.getPageNumber().intValue());
    assertEquals(
        "sample from junit test template.01", allSubSamplesResult.getSubSamples().get(0).getName());
    assertEquals("mySubSample", allSubSamplesResult.getSubSamples().get(1).getName());
  }

  @Test
  public void saveUpdateNewBasicSubSample() {
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(testUser);
    assertEquals(1, createdSample.getSubSamples().size());
    ApiSubSampleInfo createdSubSample = createdSample.getSubSamples().get(0);
    assertEquals("mySubSample", createdSubSample.getName());
    Long subSampleId = createdSubSample.getId();
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));

    assertTrue(subSampleApiMgr.exists(subSampleId));
    ApiSubSample retrievedSubSample = subSampleApiMgr.getApiSubSampleById(subSampleId, testUser);
    assertNotNull(retrievedSubSample.getSampleInfo());
    assertEquals(createdSample.getGlobalId(), retrievedSubSample.getSampleInfo().getGlobalId());
    assertEquals(0, retrievedSubSample.getNotes().size());
    assertEquals("5 g", retrievedSubSample.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(testUser.getFullName(), retrievedSubSample.getModifiedByFullName());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryAccessEvent.class));

    // update metadata information
    ApiSubSample subSampleUpdates = new ApiSubSample();
    subSampleUpdates.setId(retrievedSubSample.getId());
    subSampleUpdates.setName("subsample renamed");
    subSampleApiMgr.updateApiSubSample(subSampleUpdates, testUser);
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryEditingEvent.class));

    // add two notes
    subSampleApiMgr.addSubSampleNote(subSampleId, new ApiSubSampleNote("note #1"), testUser);
    subSampleApiMgr.addSubSampleNote(subSampleId, new ApiSubSampleNote("note #2"), testUser);
    Mockito.verify(mockPublisher, Mockito.times(3))
        .publishEvent(Mockito.any(InventoryEditingEvent.class));

    // register usage
    ApiSubSample subSampleUsage = new ApiSubSample();
    subSampleUsage.setId(retrievedSubSample.getId());
    QuantityInfo quantity1mg = QuantityInfo.of(BigDecimal.ONE, RSUnitDef.MILLI_GRAM);
    subSampleApiMgr.registerApiSubSampleUsage(subSampleUsage, quantity1mg, testUser);
    Mockito.verify(mockPublisher, Mockito.times(4))
        .publishEvent(Mockito.any(InventoryEditingEvent.class));

    // retrieve updated subsample
    retrievedSubSample = subSampleApiMgr.getApiSubSampleById(subSampleId, testUser);
    assertEquals("subsample renamed", retrievedSubSample.getName());
    assertEquals(2, retrievedSubSample.getNotes().size());
    assertEquals("4.999 g", retrievedSubSample.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(testUser.getFullName(), retrievedSubSample.getModifiedByFullName());
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryAccessEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void saveUsagesOfSubSample() {
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(testUser);
    assertEquals(1, createdSample.getSubSamples().size());
    ApiSubSampleInfo createdSubSample = createdSample.getSubSamples().get(0);
    Long subSampleId = createdSubSample.getId();

    ApiSubSample retrievedSubSample = subSampleApiMgr.getApiSubSampleById(subSampleId, testUser);
    assertEquals("5 g", retrievedSubSample.getQuantity().toQuantityInfo().toPlainString());

    // register usage
    ApiSubSample subSampleUsage = new ApiSubSample();
    subSampleUsage.setId(retrievedSubSample.getId());
    QuantityInfo quantity1dot5555mg = QuantityInfo.of(new BigDecimal("1.5555"), RSUnitDef.GRAM);
    subSampleApiMgr.registerApiSubSampleUsage(subSampleUsage, quantity1dot5555mg, testUser);

    retrievedSubSample = subSampleApiMgr.getApiSubSampleById(subSampleId, testUser);
    assertEquals("3.444 g", retrievedSubSample.getQuantity().toQuantityInfo().toPlainString());

    // register another usage
    QuantityInfo quantity45mg = QuantityInfo.of(new BigDecimal("45"), RSUnitDef.MILLI_GRAM);
    subSampleApiMgr.registerApiSubSampleUsage(subSampleUsage, quantity45mg, testUser);

    retrievedSubSample = subSampleApiMgr.getApiSubSampleById(subSampleId, testUser);
    assertEquals("3.399 g", retrievedSubSample.getQuantity().toQuantityInfo().toPlainString());

    // register usage greater than the remaining value - that should zero remaining quantity
    QuantityInfo quantity5g = QuantityInfo.of(new BigDecimal("5"), RSUnitDef.GRAM);
    subSampleApiMgr.registerApiSubSampleUsage(subSampleUsage, quantity5g, testUser);

    retrievedSubSample = subSampleApiMgr.getApiSubSampleById(subSampleId, testUser);
    assertEquals("0 g", retrievedSubSample.getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void moveSubSampleBetweenContainers() throws Exception {

    User testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);

    ApiContainer listContainer = createBasicContainerForUser(testUser);
    ApiContainer gridContainer = createBasicGridContainerForUser(testUser, 4, 4);
    ApiContainer imageContainer = createBasicImageContainerForUser(testUser);

    ApiContainer workbench = getWorkbenchForUser(testUser);
    int initialWorkbenchCount = workbench.getContentSummary().getTotalCount();
    assertEquals(3, initialWorkbenchCount);

    // subsample to move around
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(testUser);
    assertEquals(1, createdSample.getSubSamples().size());
    Mockito.verify(mockPublisher, Mockito.times(2))
        .publishEvent(Mockito.any(InventoryCreationEvent.class));

    ApiSubSampleInfo subSampleInfo = createdSample.getSubSamples().get(0);
    ApiSubSample subSample = subSampleApiMgr.getApiSubSampleById(subSampleInfo.getId(), testUser);
    // starts at workbech
    assertEquals(workbench.getId(), subSample.getParentContainer().getId());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryAccessEvent.class));

    // move to list container
    ApiSubSample updateRequest = new ApiSubSample();
    updateRequest.setId(subSampleInfo.getId());
    updateRequest.setParentContainer(listContainer);
    ApiSubSample updatedSubSample = subSampleApiMgr.updateApiSubSample(updateRequest, testUser);
    assertEquals(listContainer.getId(), updatedSubSample.getParentContainer().getId());
    Mockito.verify(mockPublisher).publishEvent(Mockito.any(InventoryMoveEvent.class));

    // verify target container updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(1, listContainer.getContentSummary().getTotalCount());
    workbench = getWorkbenchForUser(testUser);
    assertEquals(initialWorkbenchCount, workbench.getContentSummary().getTotalCount());

    // move to grid container
    updateRequest.setParentContainer(gridContainer);
    updateRequest.setParentLocation(new ApiContainerLocation(2, 3));
    updatedSubSample = subSampleApiMgr.updateApiSubSample(updateRequest, testUser);
    assertEquals(gridContainer.getId(), updatedSubSample.getParentContainer().getId());
    assertEquals(2, updatedSubSample.getParentLocation().getCoordX());
    assertEquals(3, updatedSubSample.getParentLocation().getCoordY());

    // verify source and target containers updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(0, listContainer.getContentSummary().getTotalCount());
    gridContainer = containerApiMgr.getApiContainerById(gridContainer.getId(), testUser);
    assertEquals(1, gridContainer.getContentSummary().getTotalCount());

    // move to image container
    updateRequest.setParentContainer(imageContainer);
    updateRequest.setParentLocation(imageContainer.getLocations().get(0));
    updatedSubSample = subSampleApiMgr.updateApiSubSample(updateRequest, testUser);
    assertEquals(imageContainer.getId(), updatedSubSample.getParentContainer().getId());

    // verify source and target containers updated
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(0, listContainer.getContentSummary().getTotalCount());
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), testUser);
    assertEquals(1, imageContainer.getContentSummary().getTotalCount());

    // move back to workbench
    updateRequest.setParentContainer(workbench);
    updatedSubSample = subSampleApiMgr.updateApiSubSample(updateRequest, testUser);
    assertEquals(workbench.getId(), subSample.getParentContainer().getId());

    // verify container now empty
    imageContainer = containerApiMgr.getApiContainerById(imageContainer.getId(), testUser);
    assertEquals(0, imageContainer.getContentSummary().getTotalCount());
    workbench = getWorkbenchForUser(testUser);
    assertEquals(initialWorkbenchCount + 1, workbench.getContentSummary().getTotalCount());

    // move back to list container
    updateRequest.setParentContainer(listContainer);
    updateRequest.setParentLocation(null);
    updatedSubSample = subSampleApiMgr.updateApiSubSample(updateRequest, testUser);
    assertEquals(listContainer.getId(), updatedSubSample.getParentContainer().getId());

    // verify list container now has item
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(1, listContainer.getContentSummary().getTotalCount());
    workbench = getWorkbenchForUser(testUser);
    assertEquals(initialWorkbenchCount, workbench.getContentSummary().getTotalCount());

    // delete subsample
    ApiSubSample deletedSubSample =
        subSampleApiMgr.markSubSampleAsDeleted(subSampleInfo.getId(), testUser, false);
    assertNull(deletedSubSample.getParentContainer());
    listContainer = containerApiMgr.getApiContainerById(listContainer.getId(), testUser);
    assertEquals(0, listContainer.getContentSummary().getTotalCount());

    // moves didn't cause 'edit' event, just move
    Mockito.verify(mockPublisher, Mockito.times(5))
        .publishEvent(Mockito.any(InventoryMoveEvent.class));
    Mockito.verify(mockPublisher, Mockito.never())
        .publishEvent(Mockito.any(InventoryEditingEvent.class));
    Mockito.verify(mockPublisher, Mockito.times(1))
        .publishEvent(Mockito.any(InventoryDeleteEvent.class));

    Mockito.verifyNoMoreInteractions(mockPublisher);
  }

  @Test
  public void deleteSubSample() {
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("mySample");
    ApiSubSample subSample1 = new ApiSubSample();
    subSample1.setName("mySubSample #1");
    ApiSubSample subSample2 = new ApiSubSample();
    subSample2.setName("mySubSample #2");
    ApiSubSample subSample3 = new ApiSubSample();
    subSample2.setName("mySubSample #3");
    newSample.setSubSamples(Arrays.asList(subSample1, subSample2, subSample3));

    ApiSampleWithFullSubSamples createdSample =
        sampleApiMgr.createNewApiSample(newSample, testUser);
    assertNotNull(createdSample);
    assertFalse(createdSample.isDeleted());
    assertNull(createdSample.getDeletedDate());
    assertEquals("3 ml", createdSample.getQuantity().toQuantityInfo().toPlainString());
    assertEquals(3, createdSample.getSubSamples().size());
    ApiSubSample createdSubSample1 = createdSample.getSubSamples().get(0);
    assertNotNull(createdSubSample1.getId());
    assertFalse(createdSubSample1.isDeleted());
    assertNull(createdSubSample1.getDeletedDate());
    ApiSubSample createdSubSample2 = createdSample.getSubSamples().get(1);
    assertNotNull(createdSubSample2.getId());
    assertFalse(createdSubSample2.isDeleted());
    assertNull(createdSubSample2.getDeletedDate());
    ApiSubSample createdSubSample3 = createdSample.getSubSamples().get(2);
    assertNotNull(createdSubSample3.getId());
    assertFalse(createdSubSample3.isDeleted());
    assertNull(createdSubSample3.getDeletedDate());

    // delete one of the subsamples explicitly
    subSampleApiMgr.markSubSampleAsDeleted(createdSubSample1.getId(), testUser, false);
    ApiSubSample reloadedSubSample1 =
        subSampleApiMgr.getApiSubSampleById(createdSubSample1.getId(), testUser);
    assertTrue(reloadedSubSample1.isDeleted());
    assertNotNull(reloadedSubSample1.getDeletedDate());

    ApiSample sampleWithDeletedSubSamples =
        sampleApiMgr.getApiSampleById(createdSample.getId(), testUser);
    assertFalse(sampleWithDeletedSubSamples.isDeleted());
    assertEquals(2, sampleWithDeletedSubSamples.getSubSamples().size());
    assertEquals(
        createdSubSample2.getId(), sampleWithDeletedSubSamples.getSubSamples().get(0).getId());
    assertEquals(
        "2 ml", sampleWithDeletedSubSamples.getQuantity().toQuantityInfo().toPlainString());

    // delete sample - active subsamples are marked deleted, but are still listed
    ApiSample deletedSample =
        sampleApiMgr.markSampleAsDeleted(createdSample.getId(), false, testUser);
    assertTrue(deletedSample.isDeleted());
    assertEquals(2, deletedSample.getSubSamples().size());
    assertTrue(deletedSample.getSubSamples().get(0).isDeleted());
    assertTrue(deletedSample.getSubSamples().get(0).isDeletedOnSampleDeletion());
    assertTrue(deletedSample.getSubSamples().get(1).isDeleted());
    assertTrue(deletedSample.getSubSamples().get(1).isDeletedOnSampleDeletion());

    // undelete one of the subsamples
    ApiSubSample restoredSubSample1 =
        subSampleApiMgr.restoreDeletedSubSample(createdSubSample1.getId(), testUser, false);
    assertFalse(restoredSubSample1.isDeleted());
    assertNull(restoredSubSample1.getDeletedDate());
    assertTrue(restoredSubSample1.getParentContainer().isWorkbench());
    // should also undelete parent sample
    sampleWithDeletedSubSamples = sampleApiMgr.getApiSampleById(createdSample.getId(), testUser);
    assertFalse(sampleWithDeletedSubSamples.isDeleted());
    // but just the subsample marked for undeletion, not other subsamples
    assertEquals(1, sampleWithDeletedSubSamples.getSubSamples().size());
    assertEquals(
        createdSubSample1.getId(), sampleWithDeletedSubSamples.getSubSamples().get(0).getId());
    assertEquals(
        "1 ml", sampleWithDeletedSubSamples.getQuantity().toQuantityInfo().toPlainString());

    // other subsamples stay deleted
    ApiSubSample reloadedSubSample2 =
        subSampleApiMgr.getApiSubSampleById(createdSubSample2.getId(), testUser);
    assertTrue(reloadedSubSample2.isDeleted());
    assertNotNull(reloadedSubSample2.getDeletedDate());
    // but 'deleted on sample deletion' flag is reset now when sample was restored
    assertFalse(reloadedSubSample2.isDeletedOnSampleDeletion());

    // delete the only undeleted subsample
    subSampleApiMgr.markSubSampleAsDeleted(createdSubSample1.getId(), testUser, false);
    sampleWithDeletedSubSamples = sampleApiMgr.getApiSampleById(createdSample.getId(), testUser);
    assertFalse(sampleWithDeletedSubSamples.isDeleted());
    assertEquals(0, sampleWithDeletedSubSamples.getSubSamples().size());
    assertNotNull(
        sampleWithDeletedSubSamples
            .getQuantity()); // prt-648, quantity should show 0 rather than null
    assertEquals(
        "0 ml", sampleWithDeletedSubSamples.getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void subSampleBehaviourAfterSampleDeletion() {

    Long initSubSampleCount =
        subSampleApiMgr.getSubSamplesForUser(null, null, null, testUser).getTotalHits();

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiSubSampleInfo basicSubSample = basicSample.getSubSamples().get(0);

    Long currentSubSampleCount =
        subSampleApiMgr.getSubSamplesForUser(null, null, null, testUser).getTotalHits();
    assertEquals(initSubSampleCount + 1, currentSubSampleCount);

    ApiSubSample retrievedSubSample =
        subSampleApiMgr.getApiSubSampleById(basicSubSample.getId(), testUser);
    assertFalse(retrievedSubSample.isDeleted());
    assertNull(retrievedSubSample.getDeletedDate());
    assertFalse(retrievedSubSample.isDeletedOnSampleDeletion());

    // mark sample deleted
    sampleApiMgr.markSampleAsDeleted(basicSample.getId(), false, testUser);

    // retrieve sample
    ApiSample deletedSample = sampleApiMgr.getApiSampleById(basicSample.getId(), testUser);
    assertTrue(deletedSample.isDeleted());

    // subsample still present on sample listing
    assertEquals(1, deletedSample.getSubSamples().size());
    ApiSubSampleInfo subSampleOfDeletedSample = deletedSample.getSubSamples().get(0);
    assertTrue(subSampleOfDeletedSample.isDeleted());
    assertNotNull(subSampleOfDeletedSample.getDeletedDate());
    assertTrue(subSampleOfDeletedSample.isDeletedOnSampleDeletion());

    // but subsample is not listed on general listing
    currentSubSampleCount =
        subSampleApiMgr.getSubSamplesForUser(null, null, null, testUser).getTotalHits();
    assertEquals(initSubSampleCount, currentSubSampleCount);

    // ... unless deleted are included
    currentSubSampleCount =
        subSampleApiMgr
            .getSubSamplesForUser(null, null, InventorySearchDeletedOption.INCLUDE, testUser)
            .getTotalHits();
    assertEquals(initSubSampleCount + 1, currentSubSampleCount);

    // can be queried directly though
    retrievedSubSample = subSampleApiMgr.getApiSubSampleById(basicSubSample.getId(), testUser);
    assertTrue(retrievedSubSample.isDeleted());
    assertNotNull(retrievedSubSample.getDeletedDate());
    assertTrue(retrievedSubSample.isDeletedOnSampleDeletion());

    // sysadmin can search subsamples too
    User sysadmin = logoutAndLoginAsSysAdmin();
    currentSubSampleCount =
        subSampleApiMgr
            .getSubSamplesForUser(null, null, InventorySearchDeletedOption.DELETED_ONLY, sysadmin)
            .getTotalHits();
    assertTrue(currentSubSampleCount > 0);
  }

  @Test
  public void duplicateSubsample() {
    User testUser = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(testUser);
    ApiSubSampleInfo createdSubSample = createdSample.getSubSamples().get(0);
    Long subSampleId = createdSubSample.getId();

    // add 2 notes and extra field
    subSampleApiMgr.addSubSampleNote(subSampleId, new ApiSubSampleNote("note #1"), testUser);
    subSampleApiMgr.addSubSampleNote(subSampleId, new ApiSubSampleNote("note #2"), testUser);
    addTestExtraField(createdSubSample, testUser);

    ApiSubSample copy = subSampleApiMgr.duplicate(subSampleId, testUser);
    assertEquals("mySubSample_COPY", copy.getName());
    assertEquals(2, copy.getNotes().size());
    assertEquals(1, copy.getExtraFields().size());
    assertFalse(copy.getId().equals(createdSubSample.getId()));
    assertEquals(
        10,
        sampleApiMgr
            .getSampleById(createdSample.getId(), testUser)
            .getQuantityInfo()
            .getNumericValue()
            .intValue());

    // sysadmin cannot duplicate user's subsample - requires edit permission
    User sysAdminUser = getSysAdminUser();
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> subSampleApiMgr.duplicate(subSampleId, sysAdminUser));
    assertTrue(iae.getMessage().endsWith("cannot be edited by current User"));
  }

  private void addTestExtraField(ApiSubSampleInfo apiSubSample, User user) {
    ApiExtraField extraField = new ApiExtraField(ExtraFieldTypeEnum.TEXT);
    extraField.setContent("fielddata");
    extraField.setParentGlobalId(apiSubSample.getGlobalId());
    extraField.setNewFieldRequest(true);
    ApiSubSample subSampleUpdate = new ApiSubSample();
    subSampleUpdate.setId(apiSubSample.getId());
    subSampleUpdate.setExtraFields(Arrays.asList(extraField));
    subSampleApiMgr.updateApiSubSample(subSampleUpdate, user);
  }

  @Test
  public void splitSubsample() throws InterruptedException {
    User testUser = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(testUser);
    assertEquals("5 g", createdSample.getQuantity().toQuantityInfo().toPlainString());

    ApiSubSampleInfo originalSubSample = createdSample.getSubSamples().get(0);
    assertEquals("5 g", originalSubSample.getQuantity().toQuantityInfo().toPlainString());

    // wait a moment, to verify modification date on a split subsample
    Thread.sleep(100);

    final int requiredTotal = 8;
    List<ApiSubSample> copies =
        subSampleApiMgr.split(
            SubSampleDuplicateConfig.split(originalSubSample.getId(), requiredTotal), testUser);
    // 7 copies are expected
    assertEquals(requiredTotal - 1, copies.size());

    ApiSubSampleInfoWithSampleInfo modifiedOriginal =
        subSampleApiMgr.getApiSubSampleById(originalSubSample.getId(), testUser);
    assertEquals("625 mg", modifiedOriginal.getQuantity().toQuantityInfo().toPlainString());
    assertNotEquals(modifiedOriginal.getCreatedMillis(), modifiedOriginal.getLastModifiedMillis());

    // copies all have same amount as modified original
    assertTrue(
        copies.stream().allMatch(ss -> ss.getQuantity().equals(modifiedOriginal.getQuantity())));

    // total quantity of the sample remains the same in this case time
    ApiSample retrievedSample = sampleApiMgr.getApiSampleById(createdSample.getId(), testUser);
    assertEquals("5 g", retrievedSample.getQuantity().toQuantityInfo().toPlainString());

    // assert naming rsinv-148
    Sample reloadedSample = sampleApiMgr.getSampleById(createdSample.getId(), testUser);
    IntStream.range(0, reloadedSample.getActiveSubSamplesCount())
        .forEach(
            i -> {
              String sname = reloadedSample.getActiveSubSamples().get(i).getName();
              assertTrue(
                  sname.endsWith("." + (i + 1)), String.format("failed at %d for %s : ", i, sname));
            });

    // assert that subsample quantity of 0 behaves gracefully RSINV-91
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    Long subSampleId = basicSample.getSubSamples().get(0).getId();
    SubSample saved = dao.get(subSampleId);
    saved.setQuantity(QuantityInfo.zero(RSUnitDef.getUnitById(saved.getUnitId())));
    dao.save(saved);

    List<ApiSubSample> copies2 =
        subSampleApiMgr.split(SubSampleDuplicateConfig.split(subSampleId, requiredTotal), testUser);
    assertEquals(7, copies2.size());
    assertTrue(
        copies2.stream()
            .allMatch(
                ss ->
                    ss.getQuantity() != null
                        && ss.getQuantity().getNumericValue().equals(BigDecimal.ZERO)));

    // sysadmin cannot split user's subsample - requires edit permission
    User sysAdminUser = getSysAdminUser();
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> subSampleApiMgr.duplicate(subSampleId, sysAdminUser));
    assertTrue(iae.getMessage().endsWith("cannot be edited by current User"));
  }

  @Test
  public void checkSplittingNonEmptySubSampleNeverCreatesZeroCopies() {
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    Long subSampleId = basicSample.getSubSamples().get(0).getId();
    SubSample saved = dao.get(subSampleId);
    saved.setQuantity(QuantityInfo.of(BigDecimal.valueOf(0.002), RSUnitDef.PICO_GRAM));
    dao.save(saved);

    // new quantity saved as sample's total quantity
    ApiSample retrievedSample = sampleApiMgr.getApiSampleById(basicSample.getId(), testUser);
    assertEquals("0.002 pg", retrievedSample.getQuantity().toQuantityInfo().toPlainString());

    // try split into 5 subsamples
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> subSampleApiMgr.split(SubSampleDuplicateConfig.split(subSampleId, 5), testUser));
    assertEquals(
        "Can't split 0.002 pg into 5 subsamples: resulting subsamples would have quantity equal to"
            + " 0.",
        iae.getMessage());

    // try split into 3 subsamples - rounding will create 0.001 copies which is acceptable
    List<ApiSubSample> copies =
        subSampleApiMgr.split(SubSampleDuplicateConfig.split(subSampleId, 3), testUser);
    assertEquals(2, copies.size());
    assertEquals("0.001 pg", copies.get(0).getQuantity().toQuantityInfo().toPlainString());

    // total quantity of sample will raise after split operation - that's rounding effect and is
    // fine
    retrievedSample = sampleApiMgr.getApiSampleById(basicSample.getId(), testUser);
    assertEquals("0.003 pg", retrievedSample.getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void subSampleEditedByTwoUsers() {

    // create a pi, with a group
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piUser);
    Group group = createGroup("group", piUser);
    addUsersToGroup(piUser, group, testUser);

    // create a subsample
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(testUser);
    ApiSubSample apiSubSample =
        subSampleApiMgr.getApiSubSampleById(createdSample.getSubSamples().get(0).getId(), testUser);

    // lock subsample by pi
    ApiInventoryEditLock apiLock =
        invLockTracker.attemptToLockForEdit(apiSubSample.getGlobalId(), piUser);
    assertEquals(ApiInventoryEditLockStatus.LOCKED_OK, apiLock.getStatus());

    // try edit by testUser
    apiSubSample.setName("updated name");
    IllegalArgumentException iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> subSampleApiMgr.updateApiSubSample(apiSubSample, testUser));
    assertTrue(iae.getMessage().startsWith("Item is currently edited by another user ("));

    // try delete by testUser
    iae =
        assertThrows(
            IllegalArgumentException.class,
            () -> subSampleApiMgr.markSubSampleAsDeleted(apiSubSample.getId(), testUser, false));
    assertTrue(iae.getMessage().startsWith("Item is currently edited by another user ("));

    // pi can edit fine
    ApiSubSample updatedSubSample = subSampleApiMgr.updateApiSubSample(apiSubSample, piUser);
    assertEquals("updated name", updatedSubSample.getName());

    // pi unlocks
    invLockTracker.attemptToUnlock(apiSubSample.getGlobalId(), piUser);

    // testUser can now edit fine
    apiSubSample.setName("updated name 2");
    updatedSubSample = subSampleApiMgr.updateApiSubSample(apiSubSample, testUser);
    assertEquals("updated name 2", updatedSubSample.getName());
  }

  @Test
  public void subSampleActionsWithinGroup() {

    // create pi with a sample, and a group with test user
    User pi = createAndSaveUserIfNotExists("pi" + getRandomName(8), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    ApiContainer piWorkbench = getWorkbenchForUser(pi);
    ApiSampleWithFullSubSamples piSample = createBasicSampleForUser(pi, "pi's sample");
    ApiSubSample piSubSample = piSample.getSubSamples().get(0);
    assertEquals(piWorkbench.getGlobalId(), piSubSample.getParentContainer().getGlobalId());
    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, testUser);

    // user can duplicate pi's subsample, copied subsample will be placed at user's workbench
    ApiContainer testUserWorkbench = getWorkbenchForUser(testUser);
    ApiSubSample copiedPiSubSample = subSampleApiMgr.duplicate(piSubSample.getId(), testUser);
    assertEquals(
        testUserWorkbench.getGlobalId(), copiedPiSubSample.getParentContainer().getGlobalId());
    assertEquals("mySubSample_COPY", copiedPiSubSample.getName());
    assertEquals(testUser.getUsername(), copiedPiSubSample.getCreatedBy());
    assertEquals(testUser.getUsername(), copiedPiSubSample.getModifiedBy());
    // pi stays as an owner of a subsample, because they keep owning a sample
    assertEquals(pi.getUsername(), copiedPiSubSample.getOwner().getUsername());

    // user can move the duplicated subsample back into pi's container
    ApiSubSample moveRequest = new ApiSubSample();
    moveRequest.setId(copiedPiSubSample.getId());
    moveRequest.setParentContainer(piWorkbench);
    ApiSubSample movedSubSample = subSampleApiMgr.updateApiSubSample(moveRequest, testUser);
    assertEquals(copiedPiSubSample.getId(), movedSubSample.getId());
    assertEquals(piWorkbench.getGlobalId(), movedSubSample.getParentContainer().getGlobalId());

    // cannot move into unrelated user's workbench though
    User otherUser = createAndSaveUserIfNotExists("pi" + getRandomName(8), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(otherUser);
    ApiContainer otherUserWorkbench = getWorkbenchForUser(otherUser);
    moveRequest.setParentContainer(otherUserWorkbench);
    ApiRuntimeException iae =
        assertThrows(
            ApiRuntimeException.class,
            () -> subSampleApiMgr.updateApiSubSample(moveRequest, testUser));
    assertEquals("move.failure.cannot.locate.target.container", iae.getMessage());
  }

  @Test
  public void addNewApiSubSampleToExistingSample() {
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    assertEquals(1, basicSample.getSubSamplesCount());
    assertEquals("5 g", basicSample.getQuantity().toQuantityInfo().toPlainString());

    ApiSubSample subSampleToSave = new ApiSubSample("addedSubSample");
    ApiSubSample savedSubSample =
        subSampleApiMgr.addNewApiSubSampleToSample(subSampleToSave, basicSample.getId(), testUser);
    assertEquals(subSampleToSave.getName(), savedSubSample.getName());
    assertEquals(basicSample.getGlobalId(), savedSubSample.getSampleInfo().getGlobalId());
    assertTrue(savedSubSample.getParentContainer().isWorkbench());
    assertEquals("1 g", savedSubSample.getQuantity().toQuantityInfo().toPlainString());

    // new subsample present in sample
    ApiSample reloadedSample = sampleApiMgr.getApiSampleById(basicSample.getId(), testUser);
    assertEquals(2, reloadedSample.getSubSamplesCount());
    assertEquals("6 g", reloadedSample.getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void createNewSubSamplesInExistingSample() {
    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    assertEquals(1, basicSample.getSubSamplesCount());
    assertEquals("5 g", basicSample.getQuantity().toQuantityInfo().toPlainString());

    // create 2 new subsamples, 3 mg quantity each
    List<ApiSubSample> newSubSamples =
        subSampleApiMgr.createNewSubSamplesForSample(
            basicSample.getId(),
            9,
            new ApiQuantityInfo(BigDecimal.valueOf(3), RSUnitDef.MILLI_GRAM),
            testUser);
    assertEquals(9, newSubSamples.size());
    // naming should start with 2, as the sample had one subsample before
    assertEquals("mySample.02", newSubSamples.get(0).getName());
    assertEquals("mySample.03", newSubSamples.get(1).getName());
    assertEquals("mySample.10", newSubSamples.get(8).getName());

    // new subsample present in sample
    ApiSample reloadedSample = sampleApiMgr.getApiSampleById(basicSample.getId(), testUser);
    assertEquals(10, reloadedSample.getSubSamplesCount());
    assertEquals("5.027 g", reloadedSample.getQuantity().toQuantityInfo().toPlainString());
  }

  @Test
  public void checkLimitedReadSubSampleActions() {

    // create a pi and other user
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User otherUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(piUser, otherUser);

    // groupA with pi and test user
    Group groupA = createGroup("groupA", piUser);
    addUsersToGroup(piUser, groupA, testUser);
    // groupB with pi and other user
    Group groupB = createGroup("groupB", piUser);
    addUsersToGroup(piUser, groupB, otherUser);

    // create a container shared with groupB, with a subcontainer
    ApiContainer apiContainer = createBasicContainerForUser(testUser, "c1", List.of(groupB));
    ApiSubSample apiSubSample = createComplexSampleForUser(testUser).getSubSamples().get(0);
    moveSubSampleIntoListContainer(apiSubSample.getId(), apiContainer.getId(), testUser);

    // as otherUser try retrieving the shared container
    ApiContainer containerAsSeenByOtherUser =
        containerApiMgr.getApiContainerById(apiContainer.getId(), otherUser);
    assertEquals(2, containerAsSeenByOtherUser.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.READ,
        containerAsSeenByOtherUser.getPermittedActions().get(0));
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.UPDATE,
        containerAsSeenByOtherUser.getPermittedActions().get(1));
    ApiSubSampleInfo subSampleInfoAsSeenByOtherUser =
        (ApiSubSampleInfo) containerAsSeenByOtherUser.getStoredContent().get(0);
    assertEquals(1, subSampleInfoAsSeenByOtherUser.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.LIMITED_READ,
        subSampleInfoAsSeenByOtherUser.getPermittedActions().get(0));
    assertNotNull(subSampleInfoAsSeenByOtherUser.getName());
    assertNotNull(subSampleInfoAsSeenByOtherUser.getBarcodes());
    assertNotNull(subSampleInfoAsSeenByOtherUser.getParentContainers());
    assertNull(subSampleInfoAsSeenByOtherUser.getModifiedBy()); // unavailable in limited view

    // as otherUser try retrieving the subSample
    ApiSubSample subSampleAsSeenByOtherUser =
        subSampleApiMgr.getApiSubSampleById(apiSubSample.getId(), otherUser);
    assertEquals(1, subSampleAsSeenByOtherUser.getPermittedActions().size());
    assertEquals(
        ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.LIMITED_READ,
        subSampleAsSeenByOtherUser.getPermittedActions().get(0));
    // assert only some fields populated
    assertNotNull(subSampleAsSeenByOtherUser.getName());
    assertNotNull(subSampleAsSeenByOtherUser.getBarcodes());
    assertNotNull(subSampleAsSeenByOtherUser.getParentContainers());
    assertNull(subSampleAsSeenByOtherUser.getModifiedBy());
    assertNull(subSampleAsSeenByOtherUser.getExtraFields());
    assertNull(subSampleAsSeenByOtherUser.getNotes());

    // compare with subcontainer as seen by the owner
    ApiSubSample subSampleAsSeenByTestUser =
        subSampleApiMgr.getApiSubSampleById(apiSubSample.getId(), testUser);
    assertEquals(3, subSampleAsSeenByTestUser.getPermittedActions().size());
    assertNotNull(subSampleAsSeenByTestUser.getModifiedBy());
    assertNotNull(subSampleAsSeenByTestUser.getExtraFields());
    assertNotNull(subSampleAsSeenByTestUser.getNotes());
  }
}
