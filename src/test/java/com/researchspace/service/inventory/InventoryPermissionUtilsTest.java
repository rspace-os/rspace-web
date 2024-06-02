package com.researchspace.service.inventory;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiGroupInfoWithSharedFlag;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventorySharingMode;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.Group;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.InventoryRecord.InventorySharingMode;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class InventoryPermissionUtilsTest extends SpringTransactionalTest {

  private User testUser;

  @Before
  public void setUp() {
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(testUser);
    assertTrue(testUser.isContentInitialized());
  }

  @Test
  public void checkOwnerGroupMembersRetrievalForGroupOwnersSharingMode() {

    User pi1 = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User pi2 = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User userInGroup2 = createAndSaveUserIfNotExists("userInGroup2");
    User userOutsideGroups = createAndSaveUserIfNotExists("userOutsideGroups");

    initialiseContentWithEmptyContent(pi1, pi2, userInGroup2, userOutsideGroups);
    Group group1 = createGroup("group1", pi1);
    addUsersToGroup(pi1, group1, testUser);
    Group group2 = createGroup("group2", pi1);
    addUsersToGroup(pi2, group2, testUser, userInGroup2);

    // testuser can see items of members of both groups
    List<String> usernames =
        invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(testUser);
    assertEquals(4, usernames.size());
    assertTrue(usernames.contains(pi1.getUsername()));
    assertTrue(usernames.contains(pi2.getUsername()));
    assertTrue(usernames.contains(testUser.getUsername()));
    assertTrue(usernames.contains(userInGroup2.getUsername()));
    assertFalse(usernames.contains(userOutsideGroups.getUsername()));

    // pi of one group can see only that group's items
    usernames = invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(pi1);
    assertEquals(2, usernames.size());
    assertTrue(usernames.contains(pi1.getUsername()));
    assertFalse(usernames.contains(pi2.getUsername()));
    assertTrue(usernames.contains(testUser.getUsername()));
    assertFalse(usernames.contains(userInGroup2.getUsername()));
    assertFalse(usernames.contains(userOutsideGroups.getUsername()));

    // another user can see just their stuff
    usernames = invPermissionUtils.getUsernameOfUserAndAllMembersOfTheirGroups(userOutsideGroups);
    assertEquals(1, usernames.size());
    assertEquals(userOutsideGroups.getUsername(), usernames.get(0));
  }

  @Test
  public void checkPermissionsForOwnerGroupsSharingMode() {

    User pi1 = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User pi2 = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User userInGroup2 = createAndSaveUserIfNotExists("userInGroup2");
    User userOutsideGroups = createAndSaveUserIfNotExists("userOutsideGroups");

    initialiseContentWithEmptyContent(pi1, pi2, userInGroup2, userOutsideGroups);
    Group group1 = createGroup("group1", pi1);
    addUsersToGroup(pi1, group1, testUser);
    Group group2 = createGroup("group2", pi1);
    addUsersToGroup(pi2, group2, testUser, userInGroup2);

    // standard group-sharing mode
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(testUser, "basicSample");
    assertEquals(ApiInventorySharingMode.OWNER_GROUPS, createdSample.getSharingMode());
    Sample sample = sampleApiMgr.getSampleById(createdSample.getId(), testUser);

    // all within group have full permission, outside group none
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, testUser));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, pi1));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, pi2));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, userInGroup2));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, userOutsideGroups));

    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, testUser));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, pi1));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, pi2));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, userInGroup2));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(sample, userOutsideGroups));
  }

  @Test
  public void checkPermissionsForWhitelistSharingMode() {

    User pi1 = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User pi2 = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User userInGroup2 = createAndSaveUserIfNotExists("userInGroup2");
    User userOutsideGroups = createAndSaveUserIfNotExists("userOutsideGroups");

    initialiseContentWithEmptyContent(pi1, pi2, userInGroup2, userOutsideGroups);
    Group group1 = createGroup("group1", pi1);
    addUsersToGroup(pi1, group1, testUser);
    Group group2 = createGroup("group2", pi2);
    addUsersToGroup(pi2, group2, testUser, userInGroup2);
    Group group3 = createGroup("group3", pi2);
    addUsersToGroup(pi2, group3);

    // add lab admins with and without view all permissions to group1
    User labAdmin = createAndSaveUserIfNotExists("labAdmin");
    User labAdminWithViewAll = createAndSaveUserIfNotExists("labAdminWithViewAll");
    initialiseContentWithEmptyContent(labAdmin, labAdminWithViewAll);
    logoutAndLoginAs(pi1);
    grpMgr.addMembersToGroup(
        group1.getId(), Arrays.asList(new User[] {labAdmin}), "", labAdmin.getUsername(), pi1);
    grpMgr.addMembersToGroup(
        group1.getId(),
        Arrays.asList(new User[] {labAdminWithViewAll}),
        "",
        labAdminWithViewAll.getUsername(),
        pi1);
    labAdminWithViewAll =
        grpMgr.authorizeLabAdminToViewAll(labAdminWithViewAll.getId(), pi1, group1.getId(), true);

    // assert the lab admins are setup as expected, i.e. one with view all can read docs of testUser
    group1 = grpMgr.getGroup(group1.getId());
    assertEquals(2, group1.getUsersByRole(RoleInGroup.RS_LAB_ADMIN).size());
    assertEquals(1, group1.getLabAdminsWithViewAllPermission().size());
    StructuredDocument testUserDoc = createBasicDocumentInRootFolderWithText(testUser, "any");
    logoutAndLoginAs(labAdmin);
    assertFalse(permissionUtils.isPermitted(testUserDoc, PermissionType.READ, labAdmin));
    logoutAndLoginAs(labAdminWithViewAll);
    assertTrue(permissionUtils.isPermitted(testUserDoc, PermissionType.READ, labAdminWithViewAll));

    // create a sample that is shared with group1 only
    ApiSampleWithFullSubSamples createdSample =
        createBasicSampleForUserAndGroups(testUser, "basicSample", List.of(group1));
    Sample sample = sampleApiMgr.getSampleById(createdSample.getId(), testUser);
    assertEquals(InventorySharingMode.WHITELIST, sample.getSharingMode());
    assertEquals(List.of(group1.getUniqueName()), sample.getSharedWithUniqueNames());

    // within group1 full permission, outside group1 none
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, testUser));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, labAdmin));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, labAdminWithViewAll));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, pi1));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, pi2));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, userInGroup2));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, userOutsideGroups));

    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, testUser));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, labAdmin));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, labAdminWithViewAll));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, pi1));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            sample, pi2)); // pi2 is a PI in one of testUser's group
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(sample, userInGroup2));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(sample, userOutsideGroups));

    // update a sample to be shared with group2 only
    ApiSample sampleUpdate = new ApiSample();
    sampleUpdate.setId(createdSample.getId());
    sampleUpdate.setSharedWith(
        List.of(ApiGroupInfoWithSharedFlag.forSharingWithGroup(group2, testUser)));
    sampleApiMgr.updateApiSample(sampleUpdate, testUser);

    sample = sampleApiMgr.getSampleById(createdSample.getId(), testUser);
    assertEquals(InventorySharingMode.WHITELIST, sample.getSharingMode());
    assertEquals(List.of(group2.getUniqueName()), sample.getSharedWithUniqueNames());

    // within group1 no permission (apart from pi, and la with va), within group2 full permission
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, testUser));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, labAdmin));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, labAdminWithViewAll));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, pi1));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, pi2));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, userInGroup2));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, userOutsideGroups));

    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, testUser));
    assertFalse(
        invPermissionUtils.canUserReadInventoryRecord(
            sample, labAdmin)); // LA without view all cannot see
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, labAdminWithViewAll));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, pi1));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, pi2));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, userInGroup2));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(sample, userOutsideGroups));

    // shared sample with group3 only, where owner is not a member of
    sampleUpdate.setSharedWith(
        List.of(ApiGroupInfoWithSharedFlag.forSharingWithGroup(group3, testUser)));
    sampleApiMgr.updateApiSample(sampleUpdate, testUser);

    // full permission only to item owner and group3
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, testUser));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, labAdmin));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, labAdminWithViewAll));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, pi1));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, pi2));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, userInGroup2));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, userOutsideGroups));

    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, testUser));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(sample, labAdmin));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, labAdminWithViewAll));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, pi1));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, pi2));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(sample, userInGroup2));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(sample, userOutsideGroups));
  }

  @Test
  public void checkPermissionsForOwnerOnlySharingMode() {

    User pi1 = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User user2 = createAndSaveUserIfNotExists("userInGroup2");
    User userOutsideGroups = createAndSaveUserIfNotExists("userOutsideGroups");

    initialiseContentWithEmptyContent(pi1, user2, userOutsideGroups);
    Group group1 = createGroup("group1", pi1);
    addUsersToGroup(pi1, group1, testUser, user2);

    // create a sample that is group-shared
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(testUser);
    Sample sample = sampleApiMgr.getSampleById(createdSample.getId(), testUser);
    assertEquals(InventorySharingMode.OWNER_GROUPS, sample.getSharingMode());

    // within group1 full permission, outside group1 none
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, testUser));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, user2));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, pi1));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, userOutsideGroups));

    // update a sample to be owner_only
    ApiSample sampleUpdate = new ApiSample();
    sampleUpdate.setId(createdSample.getId());
    sampleUpdate.setSharingMode(ApiInventorySharingMode.OWNER_ONLY);
    sampleApiMgr.updateApiSample(sampleUpdate, testUser);

    sample = sampleApiMgr.getSampleById(createdSample.getId(), testUser);
    assertEquals(InventorySharingMode.OWNER_ONLY, sample.getSharingMode());

    // within group1 no permission - apart from pi who can always read
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(sample, testUser));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(sample, user2));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, pi1));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sample, pi1));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(sample, userOutsideGroups));
  }

  @Test
  public void directSharingPermissionsJiraTestCases() {
    /* the test covers cases from https://researchspace.atlassian.net/browse/RSINV-235?focusedCommentId=53452 */

    // create a pi and other user
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User userA = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiA"));
    User userB = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiB"));
    User userC = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiC"));
    initialiseContentWithEmptyContent(piUser, userA, userB, userC);

    // Let GroupA be a lab group with members UserA and UserB.
    Group groupA = createGroup("GroupA", piUser);
    addUsersToGroup(piUser, groupA, userA, userB);
    // Let GroupB be a lab group with members UserB and UserC.
    Group groupB = createGroup("GroupB", piUser);
    addUsersToGroup(piUser, groupB, userB, userC);
    // Let GroupC be a lab group with just UserC.
    Group groupC = createGroup("GroupC", piUser);
    addUsersToGroup(piUser, groupC, userC);

    // UserA owns a container, ContainerA.
    ApiContainer apiContainerA = createBasicContainerForUser(userA, "ContainerA");
    Container containerA = containerApiMgr.getContainerById(apiContainerA.getId(), userA);
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userA));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userB));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(containerA, userC));

    // UserA sets the sharing of ContainerA to a whitelist [with own groups selected] and saves the
    // changes.
    ApiContainer containerUpdate = new ApiContainer();
    containerUpdate.setId(containerA.getId());
    containerUpdate.setSharingMode(ApiInventorySharingMode.WHITELIST);
    containerUpdate.setSharedWith(
        List.of(ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupA, userA)));
    containerApiMgr.updateApiContainer(containerUpdate, userA);
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userA));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userB));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(containerA, userC));

    // UserA adds GroupB to ContainerA's sharing whitelist.
    containerUpdate.setSharedWith(
        List.of(
            ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupA, userA),
            ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupB, userA)));
    containerApiMgr.updateApiContainer(containerUpdate, userA);
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userA));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userB));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userC));

    // UserA removes GroupA from ContainerA's sharing whitelist.
    containerUpdate.setSharedWith(
        List.of(ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupB, userA)));
    containerApiMgr.updateApiContainer(containerUpdate, userA);
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userA));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userB));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userC));

    // UserA replaces GroupB with GroupC in ContainerA's sharing whitelist.
    containerUpdate.setSharedWith(
        List.of(ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupC, userA)));
    containerApiMgr.updateApiContainer(containerUpdate, userA);
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userA));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(containerA, userB));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userC));

    // UserA leaves GroupA and joins GroupC
    logoutAndLoginAs(piUser);
    grpMgr.removeUserFromGroup(userA.getUsername(), groupA.getId(), piUser);
    grpMgr.addUserToGroup(userA.getUsername(), groupC.getId(), RoleInGroup.DEFAULT);
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userA));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(containerA, userB));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userC));

    // UserA sets the sharing of ContainerA to be based on the owner's (UserA's) groups.
    containerUpdate.setSharingMode(ApiInventorySharingMode.OWNER_GROUPS);
    containerApiMgr.updateApiContainer(containerUpdate, userA);
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userA));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(containerA, userB));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userC));

    // UserA leaves GroupC and rejoins GroupA
    grpMgr.removeUserFromGroup(userA.getUsername(), groupC.getId(), piUser);
    grpMgr.addUserToGroup(userA.getUsername(), groupA.getId(), RoleInGroup.DEFAULT);
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userA));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userB));
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(containerA, userC));

    // UserA transfers ContainerA to UserC.
    containerUpdate.setOwner(new ApiUser(userC));
    containerApiMgr.changeApiContainerOwner(containerUpdate, userA);
    assertFalse(invPermissionUtils.canUserEditInventoryRecord(containerA, userA));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userB));
    assertTrue(invPermissionUtils.canUserEditInventoryRecord(containerA, userC));
  }

  @Test
  public void containerAndContentSharingPermissionsJiraTestCases() {
    /* the test covers cases from https://researchspace.atlassian.net/browse/RSINV-235?focusedCommentId=53489 */

    // create a pi and users
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User userD = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiD"));
    User userE = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiE"));
    User userF = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiF"));
    initialiseContentWithEmptyContent(piUser, userD, userE, userF);

    // Let GroupD be a lab group with members: UserD
    Group groupD = createGroup("GroupD", piUser);
    addUsersToGroup(piUser, groupD, userD);
    // Let GroupE be a lab group with members: UserE, UserF
    Group groupE = createGroup("GroupE", piUser);
    addUsersToGroup(piUser, groupE, userE, userF);

    // UserD owns a container C1, with access permissions set to the default of UserD's groups
    ApiContainer apiContainerC1 = createBasicContainerForUser(userD, "C1");
    Container containerC1 = containerApiMgr.getContainerById(apiContainerC1.getId(), userD);
    assertEquals(InventorySharingMode.OWNER_GROUPS, containerC1.getSharingMode());

    // UserE owns a sample SA1 which has two subsamples, SS1 and SS2
    // SA1 has access permissions set to the default of UserE's groups
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("SA1");
    ApiSubSample newSubSample1 = new ApiSubSample("SS1");
    ApiSubSample newSubSample2 = new ApiSubSample("SS2");
    newSample.setSubSamples(List.of(newSubSample1, newSubSample2));
    ApiSampleWithFullSubSamples apiSampleSA1 = sampleApiMgr.createNewApiSample(newSample, userE);
    Sample sampleSA1 = sampleApiMgr.getSampleById(apiSampleSA1.getId(), userE);
    assertEquals("SA1", sampleSA1.getName());
    assertEquals(InventorySharingMode.OWNER_GROUPS, sampleSA1.getSharingMode());
    assertEquals(2, sampleSA1.getSubSamples().size());
    assertEquals("SS1", sampleSA1.getSubSamples().get(0).getName());
    assertEquals("SS2", sampleSA1.getSubSamples().get(1).getName());
    SubSample subSampleSS1 = sampleSA1.getSubSamples().get(0);
    SubSample subSampleSS2 = sampleSA1.getSubSamples().get(1);

    // C1 contains SS1, only.
    moveSubSampleIntoListContainer(subSampleSS1.getId(), containerC1.getId(), piUser);

    /*
    A: At start,
    	- UserD can see the *full details* of C1, as they are owner.
    	- UserD can see *some details* of SS1 as it is contained in C1.
    	- UserD can see only *basic details* of SA1, and SS2.
    	- UserE and UserF can see *some details* C1, as their sample has a subsample that lives in it.
    	- UserE and UserF can see the *full details* of SA1, SS1, and SS2.
     */
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC1, userD));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userD));
    assertFalse(
        invPermissionUtils.canUserReadInventoryRecord(
            subSampleSS1, userD)); // 'some details' access to SS1, not full
    assertTrue(invPermissionUtils.canUserLimitedReadInventoryRecord(subSampleSS1, userD));
    assertFalse(
        invPermissionUtils.canUserReadInventoryRecord(
            subSampleSS2, userD)); // only basic details to SS2
    assertFalse(invPermissionUtils.canUserLimitedReadInventoryRecord(subSampleSS2, userD));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC1, userE)); // TODO should give 'some details' access, not full
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS2, userE));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC1, userF)); // TODO should give 'some details' access, not full
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userF));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userF));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS2, userF));

    /* B: UserE sets the access permission of SA1 to an access list including GroupD and GroupE
    - No change to UserD's access to C1
    - UserD can now see the *full details* of SA1, SS1, and SS2 as they are a member of GroupD
    - No change to UserE's access to C1, SA1, SS1, nor SS2.
    - No change to UserF's access to C1, SA1, SS1, nor SS2.
    */
    ApiSample sampleUpdate = new ApiSample();
    sampleUpdate.setId(sampleSA1.getId());
    sampleUpdate.setSharingMode(ApiInventorySharingMode.WHITELIST);
    sampleUpdate.setSharedWith(
        List.of(
            ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupD, userE),
            ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupE, userE)));
    sampleApiMgr.updateApiSample(sampleUpdate, userE);
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS2, userD));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC1, userE)); // TODO should give 'some details' access, not full
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS2, userE));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC1, userF)); // TODO should give 'some details' access, not full
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userF));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userF));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS2, userF));

    /* C: UserE creates a container C2 with default permissions, placing C1 in it.
    	- UserD can see *some details* of C2 as their C1 is in it.
    	- No change to UserD's access to C1, SA1, SS1, nor SS2.
    	- UserE can see the *full details* of C2, as its owner.
    	- No change to UserE's access to C1, SA1, SS1, nor SS2.
    	- UserF can see the *full details* of C2, as a member of GroupE with UserE.
    	- No change to UserF's access to C1, SA1, SS1, nor SS2.
    */
    ApiContainer apiContainerC2 = createBasicContainerForUser(userE, "C2");
    moveContainerIntoListContainer(apiContainerC1.getId(), apiContainerC2.getId(), piUser);
    Container containerC2 = containerApiMgr.getContainerById(apiContainerC2.getId(), userE);
    assertEquals(InventorySharingMode.OWNER_GROUPS, containerC2.getSharingMode());
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC2, userD)); // TODO should give 'some details' access, not full
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS2, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userE));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC1, userE)); // TODO should give 'some details' access, not full
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userF));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC1, userF)); // TODO should give 'some details' access, not full

    /* D: UserD transfers C1 to UserE
    - UserD can only see *some details* of C1 and C2.
    - No change to UserD's access to SA1, SS1, nor SS2.
    - UserE can see the *full details* of C1, as its owner.
    - No change to UserE's access to C2, SA1, SS1, nor SS2.
    - UserF can see the *full details* of C1, as a member of GroupE with UserE.
    - No change to UserF's access to C2, SA1, SS1, nor SS2.
    */
    ApiContainer containerC1Update = new ApiContainer();
    containerC1Update.setId(containerC1.getId());
    containerC1Update.setOwner(new ApiUser(userE));
    containerApiMgr.changeApiContainerOwner(containerC1Update, userD);
    assertFalse(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC2, userD)); // 'some details' access, not full
    assertTrue(invPermissionUtils.canUserLimitedReadInventoryRecord(containerC2, userD));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC1, userD)); // TODO should give 'some details' access, not full
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS2, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC1, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userF));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC1, userF));

    /* E: UserE sets the access permission C1 to an access list including GroupD only.
    They are warned that they only retain access because they are the owner.
    - UserD can see full details of C1.
    - No change to UserD's access to C2, SA1, SS1, nor SS2.
    - No change to UserE's access to C1, C2, SA1, SS1, nor SS2.
    - UserF can see *some details* of C1, as they are in GroupE who can see the *full details* of SS1.
    - No change to UserF's access to C2, SA1, SS1, nor SS2.
    */
    containerC1Update.setSharingMode(ApiInventorySharingMode.WHITELIST);
    containerC1Update.setSharedWith(
        List.of(ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupD, userE)));
    containerApiMgr.updateApiContainer(containerC1Update, userE);
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC2, userD)); // TODO should give 'some details' access, not full
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS2, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC1, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userF));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC1, userF)); // TODO should give 'some details' access, not full

    /* F: UserD removes GroupD from C1's access list.
    They are warned that they are removing their own access; an operation that they cannot undo.
    - The only regular user who can view the *full details* of C1 is UserE.
    - UserD can only see *basic details* of C2, and *some details* of C1 (which stores SS1)
    - No change to UserE's access to C2, SA1, SS1, nor SS2.
    - No change to UserF's access to C1, C2, SA1, SS1, nor SS2.
    */
    containerC1Update.setSharedWith(new ArrayList<>());
    containerApiMgr.updateApiContainer(containerC1Update, userD);
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(containerC2, userD));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC1, userD)); // TODO should give 'some details' access, not full
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS2, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC1, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userF));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC1, userF)); // TODO should give 'some details' access, not full

    /* G: UserE moves SS1 from C1 to C2
    - UserD can only see *basic details* of C1, and *some details* of C2 (which now stores SS1).
    - No other changes for UserD.
    - No changes for UserE.
    - No changes for UserF.
    */
    moveSubSampleIntoListContainer(subSampleSS1.getId(), containerC2.getId(), piUser);
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC2, userD)); // TODO should give 'some details' access, not full
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(containerC1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS2, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC1, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userF));
    assertFalse(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC1, userF)); // 'some details' access, not full
    assertTrue(invPermissionUtils.canUserLimitedReadInventoryRecord(containerC1, userF));

    /* H: UserE leaves GroupE
    	- No changes for UserD
    	- No changes for UserE
    	- UserF can only see *some details* of C2, as they still have full access to SS1
    	- UserF can only see * basic details* of C1
    	- No other changes for UserF.
    */
    logoutAndLoginAs(piUser);
    grpMgr.removeUserFromGroup(userE.getUsername(), groupE.getId(), piUser);
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userD));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(containerC1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(subSampleSS2, userD));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC2, userE));
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC1, userE));
    assertTrue(
        invPermissionUtils.canUserReadInventoryRecord(
            containerC2, userF)); // TODO should give 'some details' access, not full
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(containerC1, userF));
  }

  @Test
  public void limitedReadContainerTestCases() {
    // create a pi and users
    User piUser = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    User userA = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiA"));
    User userB = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiB"));
    User userC = createAndSaveUserIfNotExists(getRandomAlphabeticString("apiC"));
    initialiseContentWithEmptyContent(piUser, userA, userB, userC);

    // created two groups
    Group groupA = createGroup("GroupA", piUser);
    addUsersToGroup(piUser, groupA, userA);
    Group groupB = createGroup("GroupB", piUser);
    addUsersToGroup(piUser, groupB, userB, userC);

    // UserA owns a container C1, subcontainer C2 and sample SA1
    ApiContainer apiContainerC1 = createBasicContainerForUser(userA, "C1");
    Container containerC1 = containerApiMgr.getContainerById(apiContainerC1.getId(), userA);
    assertEquals(InventorySharingMode.OWNER_GROUPS, containerC1.getSharingMode());

    ApiContainer apiContainerC2 = createBasicContainerForUser(userA, "C2");
    moveContainerIntoListContainer(apiContainerC2.getId(), apiContainerC1.getId(), userA);
    Container containerC2 = containerApiMgr.getContainerById(apiContainerC2.getId(), userA);
    assertEquals(InventorySharingMode.OWNER_GROUPS, containerC2.getSharingMode());
    assertEquals(containerC1.getId(), containerC2.getParentContainer().getId());

    ApiSampleWithFullSubSamples apiSampleSA1 = createBasicSampleForUser(userA, "SA1");
    moveSubSampleIntoListContainer(
        apiSampleSA1.getSubSamples().get(0).getId(), containerC1.getId(), userA);
    Sample sampleSA1 = sampleApiMgr.getSampleById(apiSampleSA1.getId(), userA);
    assertEquals("SA1", sampleSA1.getName());
    assertEquals(InventorySharingMode.OWNER_GROUPS, sampleSA1.getSharingMode());
    assertEquals(1, sampleSA1.getSubSamples().size());
    SubSample subSampleSS1 = sampleSA1.getSubSamples().get(0);
    assertEquals(containerC1.getId(), subSampleSS1.getParentContainer().getId());
    assertEquals(InventorySharingMode.OWNER_GROUPS, subSampleSS1.getSharingMode());

    // verify that userC (from groupB) has no limited read to any of items
    assertFalse(invPermissionUtils.canUserLimitedReadInventoryRecord(containerC1, userC));
    assertFalse(invPermissionUtils.canUserLimitedReadInventoryRecord(containerC2, userC));
    assertFalse(invPermissionUtils.canUserLimitedReadInventoryRecord(subSampleSS1, userC));
    assertFalse(invPermissionUtils.canUserLimitedReadInventoryRecord(sampleSA1, userC));

    // share container with groupB
    ApiContainer containerC1Update = new ApiContainer();
    containerC1Update.setId(containerC1.getId());
    containerC1Update.setSharingMode(ApiInventorySharingMode.WHITELIST);
    containerC1Update.setSharedWith(
        List.of(ApiGroupInfoWithSharedFlag.forSharingWithGroup(groupB, userA)));
    containerApiMgr.updateApiContainer(containerC1Update, userA);

    // verify that userC can read shared container, but has only limited read to subcontainer,
    // subSample and its sample
    assertTrue(invPermissionUtils.canUserReadInventoryRecord(containerC1, userC));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(containerC2, userC));
    assertTrue(invPermissionUtils.canUserLimitedReadInventoryRecord(containerC2, userC));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(subSampleSS1, userC));
    assertTrue(invPermissionUtils.canUserLimitedReadInventoryRecord(subSampleSS1, userC));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(sampleSA1, userC));
    assertTrue(invPermissionUtils.canUserLimitedReadInventoryRecord(sampleSA1, userC));
  }
}
