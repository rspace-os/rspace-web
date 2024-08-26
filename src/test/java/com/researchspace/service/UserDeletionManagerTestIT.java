package com.researchspace.service;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.jdbc.JdbcTestUtils.countRowsInTable;

import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.Community;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.ShareRecordMessageOrRequestCreationConfiguration;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.events.AccountEventType;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.model.field.Field;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.UserDeletionPolicy.UserTypeRestriction;
import com.researchspace.service.cloud.CloudNotificationManager;
import com.researchspace.service.cloud.CommunityUserManager;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.TestGroup;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectRetrievalFailureException;

@RunWith(ConditionalTestRunner.class)
public class UserDeletionManagerTestIT extends RealTransactionSpringTestBase {

  private @Autowired UserDeletionManager userDeletionMgr;
  private @Autowired CommunityUserManager communityUserMgr;
  private @Autowired CloudNotificationManager cloudNotificationManager;
  private @Autowired MessageOrRequestCreatorManager requestCreatorManager;
  private @Autowired NfsManager nfsManager;
  private @Autowired UserAppConfigManager userAppConfigManager;
  private @Autowired RecordFavoritesManager favMgr;
  private @Autowired UserConnectionManager userConn;
  private @Autowired JdbcTemplate jdbcTemplate;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    RSpaceTestUtils.logout();
    super.tearDown();
  }

  @Test
  public void removeTempUser() throws Exception {
    // simple self-signup scenario
    final User tempuser =
        communityUserMgr.createInvitedUser(
            getRandomAlphabeticString("temp") + "@researchspace.com");

    UserDeletionPolicy policy = getDeleteTempUserPolicy();
    User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(tempuser.getId(), policy, sysadmin);
    assertTrue(result.isSucceeded());
    // user no longer exists
    assertUserNotExist(tempuser);

    // now invite via create group
  }

  @Test
  public void removeTempUserInvitedViaSharedRecord() throws Exception {
    User subject = createInitAndLoginAnyUser();
    StructuredDocument toShare = createBasicDocumentInRootFolderWithText(subject, "any");
    final User tempuser =
        communityUserMgr.createInvitedUser(
            getRandomAlphabeticString("temp") + "@researchspace.com");
    cloudNotificationManager.sendShareRecordRequest(subject, tempuser, toShare.getId(), "read");

    User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(tempuser.getId(), getDeleteTempUserPolicy(), sysadmin);
    assertTrue(result.isSucceeded());
    assertEquals(tempuser, result.getEntity());
    assertUserNotExist(tempuser);
  }

  @Test
  public void removeTempUserInvitedToBeInGroup() throws Exception {
    // setup group and invite new temp user
    TestGroup group = createTestGroup(0);
    final User tempuser =
        communityUserMgr.createInvitedUser(
            getRandomAlphabeticString("temp") + "@researchspace.com");
    logoutAndLoginAs(group.getPi());
    final int initialTargetCount = countCommunicationTargetRows();
    cloudNotificationManager.sendCreateGroupRequest(
        group.getPi(),
        tempuser,
        TransformerUtils.toList("a@a.com"),
        group.getGroup().getDisplayName());
    assertEquals(initialTargetCount + 1, countCommunicationTargetRows());

    // delete succeeds
    User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(tempuser.getId(), getDeleteTempUserPolicy(), sysadmin);
    assertTrue(result.isSucceeded());
    assertUserNotExist(tempuser);
    assertEquals(initialTargetCount, countCommunicationTargetRows());
  }

  private int countCommunicationTargetRows() {
    return countRowsInTable(jdbcTemplate, "CommunicationTarget");
  }

  private void assertUserNotExist(final User tempuser) throws Exception {
    assertExceptionThrown(() -> userMgr.get(tempuser.getId()), DataAccessException.class);
  }

  @Test
  public void removeTempUserFailsIfUserIsInitialized() throws Exception {
    final User tempuser =
        communityUserMgr.createInvitedUser(
            getRandomAlphabeticString("temp") + "@researchspace.com");
    initUser(tempuser, false); // folders, permissions etc added
    final User sysadmin = logoutAndLoginAsSysAdmin();
    final UserDeletionPolicy policy = getDeleteTempUserPolicy();

    assertExceptionThrown(
        () -> userDeletionMgr.removeUser(tempuser.getId(), policy, sysadmin),
        DataAccessException.class);
  }

  @Test
  @RunIfSystemPropertyDefined("nightly") // is slow to execute
  public void testRemoveInitialisedUser() throws Exception {
    // this user will create standard forms etc
    final User sysadmin = createAndSaveUser(getRandomAlphabeticString("toDelete"), "ROLE_SYSADMIN");
    initUser(sysadmin, true);

    final User tempuser = createAndSaveUser(getRandomAlphabeticString("toDelete"));
    initUser(tempuser, true); // folders, permissions etc added
    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    logoutAndLoginAs(sysadmin);
    final long filePropertyCount = getFilePropertyCount(tempuser.getUsername());
    assertTrue(filePropertyCount > 0);

    // now let's create an attachment in a field with complicated content
    StructuredDocument sdoc = createComplexDocument(tempuser);
    Field field = sdoc.getFields().get(0);
    EcatMediaFile added =
        addAttachmentDocumentToField(RSpaceTestUtils.getAnyAttachment(), field, tempuser);
    fieldMgr.addMediaFileLink(added.getId(), tempuser, field.getId(), true);

    // let's keep handle to attachment file
    File attachmentOnFilestore = new File(new URI(added.getFileUri()));
    assertTrue(attachmentOnFilestore.exists());

    // let's save the content as a snippet too
    recordMgr.createSnippet("testSnip", field.getFieldData(), tempuser);

    // add an app config
    createAppConfigForUser(tempuser);

    // now try delete the user
    logoutAndLoginAs(sysadmin);
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(tempuser.getId(), policy, sysadmin);
    assertTrue(report.isSucceeded());
    assertEquals(0, getFilePropertyCount(tempuser.getUsername()).intValue());
    assertUserNotExist(tempuser);

    // attachment file is still on filestore
    assertTrue(attachmentOnFilestore.exists());

    // call removal of user's filestore resources
    ServiceOperationResult<Integer> deleteResourcesResult =
        userDeletionMgr.deleteRemovedUserFilestoreResources(tempuser.getId(), true, sysadmin);
    assertTrue(deleteResourcesResult.isSucceeded());
    // assert attachment file is now deleted from filestore
    int deletedResourcesCount = deleteResourcesResult.getEntity();
    assertEquals(
        "unexpected number of deleted filestore resources after user removal",
        23,
        deletedResourcesCount);
    assertFalse(attachmentOnFilestore.exists());
  }

  private void createAppConfigForUser(User user) {
    // enable slack, add a slack channel
    userAppConfigManager.getByAppName("app.slack", user);

    Map<String, String> channelOptions = new HashMap<>();
    channelOptions.put("SLACK_TEAM_NAME", "testTeamName");
    channelOptions.put("SLACK_CHANNEL_NAME", "testChannelName");
    channelOptions.put("SLACK_CHANNEL_LABEL", "testLabel");
    channelOptions.put("SLACK_WEBHOOK_URL", "testWebhookUrl");
    channelOptions.put("SLACK_USER_ID", "U123");
    channelOptions.put("SLACK_TEAM_ID", "T456");
    channelOptions.put("SLACK_CHANNEL_ID", "C789");
    channelOptions.put("SLACK_USER_ACCESS_TOKEN", "xoxp-123456789");

    userAppConfigManager.saveAppConfigElementSet(channelOptions, null, false, user);
  }

  private Long getFilePropertyCount(String username) {
    openTransaction();
    Object rc =
        sessionFactory
            .getCurrentSession()
            .createCriteria(FileProperty.class)
            .add(Restrictions.eq("fileOwner", username))
            .setProjection(Projections.countDistinct("id"))
            .uniqueResult();
    commitTransaction();
    return (Long) rc;
  }

  @Test
  public void testValidationCantSelfDelete() {
    User sysadmin = logoutAndLoginAsSysAdmin();
    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(sysadmin.getId(), policy, sysadmin);
    assertFalse(report.isSucceeded());
    assertEquals(report.getMessage(), messages.getMessage("errors.deleteuser.nonself"));
  }

  @Test
  public void testOnlyAdminCanDelete() throws Exception {
    final User notAnAdmin = createAndSaveUser(getRandomAlphabeticString("toDelete"), "ROLE_USER");
    initUser(notAnAdmin, true);
    final User toDelete = createAndSaveUser(getRandomAlphabeticString("toDelete"));
    logoutAndLoginAs(notAnAdmin);
    assertAuthorisationExceptionThrown(
        () -> {
          final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
          userDeletionMgr.removeUser(toDelete.getId(), policy, notAnAdmin);
        });
  }

  @Test
  public void testRemoveUserWithRequests() {
    final User toDelete = createAndSaveUser(getRandomAlphabeticString("toDelete"));
    final User toDelete2 = createAndSaveUser(getRandomAlphabeticString("toDelete2"));
    initUsers(toDelete, toDelete2);
    logoutAndLoginAs(toDelete);
    BaseRecord doc = createBasicDocumentInRootFolderWithText(toDelete, "any");
    ShareRecordMessageOrRequestCreationConfiguration cgf =
        new ShareRecordMessageOrRequestCreationConfiguration();
    cgf.setTarget(toDelete2);
    cgf.setRecordId(doc.getId());
    cgf.setPermission("read");
    cgf.setMessageType(MessageType.REQUEST_SHARE_RECORD);
    requestCreatorManager.createRequest(
        cgf,
        toDelete.getUsername(),
        new HashSet<>(Collections.singletonList(toDelete2.getUsername())),
        null,
        null);

    User sysadmin = logoutAndLoginAsSysAdmin();
    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    // now we delete the person that toDelete sent invitation too
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(toDelete2.getId(), policy, sysadmin);
    assertTrue(report.isSucceeded());
    report = userDeletionMgr.removeUser(toDelete.getId(), policy, sysadmin);
    assertTrue(report.isSucceeded());
  }

  @Test
  public void deletePi() {
    // this user will create standard forms etc
    User sysadmin = logoutAndLoginAsSysAdmin();

    final User piToDelete = createAndSaveUser(getRandomAlphabeticString("toDelete"), "ROLE_PI");
    final User user = createAndSaveUser(getRandomAlphabeticString("toDelete"));
    initUsers(true, user, piToDelete);
    Group group = createGroupForUsers(sysadmin, piToDelete.getUsername(), "", piToDelete, user);

    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    // now we delete the person that toDelete shared the document with
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(piToDelete.getId(), policy, sysadmin);
    assertFalse(report.isSucceeded());
    assertThat(report.getMessage(), containsString("Sorry, cannot remove the only admin or PI"));
  }

  @Test
  public void testRemoveUserWithFavourites() {
    User anyUser = createInitAndLoginAnyUser();
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(anyUser, "any");
    favMgr.saveFavoriteRecord(sd.getId(), anyUser.getId()); // user's own favorites

    User sysadmin = logoutAndLoginAsSysAdmin();
    favMgr.saveFavoriteRecord(sd.getId(), sysadmin.getId()); // user's doc favorited by others

    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    // now we delete the person that toDelete shared the document with
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(anyUser.getId(), policy, sysadmin);
    assertTrue(report.isSucceeded());
  }

  @Test
  public void testRemoveUserWithUserConnection() {
    User anyUser = createInitAndLoginAnyUser();
    UserConnection aUserConnection = TestFactory.createUserConnection(anyUser.getUsername());
    userConn.save(aUserConnection);
    User sysadmin = logoutAndLoginAsSysAdmin();
    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    // now we delete the person that toDelete shared the document with
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(anyUser.getId(), policy, sysadmin);
    assertTrue(report.isSucceeded());
    assertFalse(userMgr.exists(anyUser.getId()));
  }

  @Test
  public void removeUserWithAccountEvents() {
    User toDelete = createInitAndLoginAnyUser();
    toDelete = userMgr.get(toDelete.getId());
    toDelete.setEnabled(false);
    userMgr.save(toDelete);
    UserAccountEvent accountEvent = new UserAccountEvent(toDelete, AccountEventType.DISABLED);
    userMgr.saveUserAccountEvent(accountEvent);
    User sysadmin = logoutAndLoginAsSysAdmin();
    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(toDelete.getId(), policy, sysadmin);
    assertTrue(report.isSucceeded());
  }

  @Test
  public void testRemoveUserWithSamplesLomAndBasket() throws IOException {
    // create user with standard forms, docs, inventory items
    final User toDelete = createAndSaveUser(getRandomAlphabeticString("toDelete"));
    initUser(toDelete, true);
    logoutAndLoginAs(toDelete);

    // create basic sample
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(toDelete);
    // create complex sample with attachments
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(toDelete);
    addFileAttachmentToInventoryItem(new GlobalIdentifier(complexSample.getGlobalId()), toDelete);
    addFileAttachmentToInventoryItem(
        new GlobalIdentifier(complexSample.getSubSamples().get(0).getGlobalId()), toDelete);

    StructuredDocument sd = createBasicDocumentInRootFolderWithText(toDelete, "any");
    Field field = sd.getFields().get(0);
    ApiMaterialUsage sampleUsage = new ApiMaterialUsage(sample, null);
    createBasicListOfMaterialsForUserAndDocField(toDelete, field, List.of(sampleUsage));

    // create basket with a sample in it
    basketApiMgr.createNewBasket("testBasket", List.of(sample.getGlobalId()), toDelete);

    // delete the user
    User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(toDelete.getId(), unrestrictedDeletionPolicy(), sysadmin);
    assertTrue(report.isSucceeded());
  }

  @Test
  public void testRemoveUserInGroup() {
    // this user will create standard forms etc
    final User admin = createAndSaveUser(getRandomAlphabeticString("toDelete"), "ROLE_ADMIN");
    initUser(admin, true);

    final User pi = createAndSaveUser(getRandomAlphabeticString("toDelete"), "ROLE_PI");
    final User toDelete = createAndSaveUser(getRandomAlphabeticString("toDelete"));
    final User toDelete2 = createAndSaveUser(getRandomAlphabeticString("toDelete2"));

    initUsers(true, pi, toDelete, toDelete2); // folders, permissions etc added
    logoutAndLoginAs(pi);
    Group group = createGroupForUsers(pi, pi.getUsername(), "", pi, toDelete, toDelete2);
    logoutAndLoginAs(toDelete);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(toDelete, "any");
    shareRecordWithGroup(toDelete, group, sd);
    shareRecordWithUser(toDelete, sd, toDelete2);
    User sysadmin = logoutAndLoginAsSysAdmin();

    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    // now we delete the person that toDelete shared the document with
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(toDelete2.getId(), policy, sysadmin);
    assertTrue(report.isSucceeded());
    // now login as toDelete and try to access shared records:
    logoutAndLoginAs(toDelete);
    sharingMgr.listSharedRecordsForUser(
        toDelete, PaginationCriteria.createDefaultForClass(RecordGroupSharing.class));
    // now login as sysadmin and delete user
    logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> report2 =
        userDeletionMgr.removeUser(toDelete.getId(), policy, sysadmin);
    assertTrue(report2.isSucceeded());
  }

  // RSPAC-1858
  @Test
  public void removeSysadmin() {
    final User sysadminToDelete =
        createAndSaveUser(
            getRandomAlphabeticString("sysadminToDelete"), Role.SYSTEM_ROLE.getName());
    initUser(sysadminToDelete, true);
    User sysadmin1 = logoutAndLoginAsSysAdmin();
    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(sysadminToDelete.getId(), policy, sysadmin1);
    assertTrue(report.isSucceeded());
  }

  // RSPAC-1929
  @Test
  public void removeCommunityAdminSuccess() {
    final User communityAdminToDelete =
        createAndSaveUser(
            getRandomAlphabeticString("communityAdminToDelete"), Role.ADMIN_ROLE.getName());
    initUser(communityAdminToDelete, true);
    User sysadmin1 = logoutAndLoginAsSysAdmin();
    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(communityAdminToDelete.getId(), policy, sysadmin1);
    assertThat(report.isSucceeded(), is(true));
  }

  // RSPAC-1929
  @Test
  public void removeCommunityAdminSuccessIfNotSoleAdmin() {
    final User communityAdminToDelete =
        createAndSaveUser(
            getRandomAlphabeticString("communityAdminToDelete"), Role.ADMIN_ROLE.getName());
    final User communityAdminToDelete2 =
        createAndSaveUser(
            getRandomAlphabeticString("communityAdminToDelete2"), Role.ADMIN_ROLE.getName());
    initUsers(communityAdminToDelete, communityAdminToDelete2);
    User sysadmin1 = logoutAndLoginAsSysAdmin();
    Community comm = createAndSaveCommunity(communityAdminToDelete, "community2");
    communityMgr.addAdminsToCommunity(new Long[] {communityAdminToDelete2.getId()}, comm.getId());
    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(communityAdminToDelete.getId(), policy, sysadmin1);
    assertThat(report.isSucceeded(), is(true));
  }

  private UserDeletionPolicy unrestrictedDeletionPolicy() {
    return new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION);
  }

  // RSPAC-1929
  @Test
  public void removeCommunityAdminFailsIfSoleAdmin() {
    final User communityAdminToDelete =
        createAndSaveUser(
            getRandomAlphabeticString("communityAdminToDelete"), Role.ADMIN_ROLE.getName());
    initUser(communityAdminToDelete, true);
    User sysadmin1 = logoutAndLoginAsSysAdmin();
    createAndSaveCommunity(communityAdminToDelete, "community1");

    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(communityAdminToDelete.getId(), policy, sysadmin1);
    assertThat(report.isSucceeded(), is(false));
  }

  @Test
  public void removeInstitutionalUser() {
    final User pi = createAndSaveUser(getRandomAlphabeticString("piToDelete"), "ROLE_PI");
    initUser(pi, true);

    NfsFileSystem fileSystem = new NfsFileSystem();
    fileSystem.setName("testUserDeletionFileSystem");
    nfsManager.saveNfsFileSystem(fileSystem);

    NfsFileStore fileStore = new NfsFileStore();
    fileStore.setFileSystem(fileSystem);
    fileStore.setUser(pi);
    nfsManager.saveNfsFileStore(fileStore);

    final User sysadmin = logoutAndLoginAsSysAdmin();
    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> report = userDeletionMgr.removeUser(pi.getId(), policy, sysadmin);
    assertTrue(report.isSucceeded());
  }

  @Test
  public void testDeleteUserTransfersFormsUsedByOthersToSysadmin(){
    User u1 = createInitAndLoginAnyUser();
    RSForm u1Form = createAnyForm(u1);
    u1Form.getAccessControl().setWorldPermissionType(PermissionType.READ);
    u1Form.publish();
    formMgr.save(u1Form);

    User u2 = createInitAndLoginAnyUser();
    Record u2Record = recordFactory.createStructuredDocument("doc", u2, u1Form);
    recordMgr.save(u2Record, u2);

    User sysadmin = logoutAndLoginAsSysAdmin();

    UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    // delete u1 and check deletion succeeds
    ServiceOperationResult<User> report = userDeletionMgr.removeUser(u1.getId(), policy, sysadmin);
    assertTrue(report.isSucceeded());

    // check u1 form ownership has been transferred to sysadmin performing the deletion
    RSForm formUsedToCreateDoc = formMgr.get(u1Form.getId());
    assertEquals(sysadmin, formUsedToCreateDoc.getOwner());

    // check u2 doc created from deleted user's form is unaffected and still accessible
    Record u2Doc = recordMgr.get(u2Record.getId());
    assertEquals(u2Record, u2Doc);
  }

  @Test
  public void testDeleteUserDeletesFormsOnlyUsedByDeletedUser(){
    User u1 = createInitAndLoginAnyUser();
    RSForm u1Form = createAnyForm(u1);
    u1Form.publish();
    formMgr.save(u1Form);

    Record u1Record = recordFactory.createStructuredDocument("doc", u1, u1Form);
    recordMgr.save(u1Record, u1);

    User sysadmin = logoutAndLoginAsSysAdmin();

    UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    // delete u1 and check deletion succeeds
    ServiceOperationResult<User> report = userDeletionMgr.removeUser(u1.getId(), policy, sysadmin);
    assertTrue(report.isSucceeded());

    // check u1 form has been deleted (and exception is thrown trying to retrieve) since the form
    // wasn't used by any other users
    assertThrows(
        ObjectRetrievalFailureException.class,
        () -> formMgr.get(u1Form.getId())
    );
  }

  private UserDeletionPolicy getDeleteTempUserPolicy() {
    return new UserDeletionPolicy(UserTypeRestriction.TEMP_USER);
  }
}
