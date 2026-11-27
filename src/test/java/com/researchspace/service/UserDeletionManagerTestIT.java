package com.researchspace.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.jdbc.JdbcTestUtils.countRowsInTable;
import static org.springframework.test.jdbc.JdbcTestUtils.countRowsInTableWhere;

import com.researchspace.Constants;
import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestInfo;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestStatusChange;
import com.researchspace.api.v1.model.ApiSampleRequestStatusPut;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.InventoryLinkDao;
import com.researchspace.dao.NfsDao;
import com.researchspace.dao.SampleDao;
import com.researchspace.dao.StoichiometryInventoryLinkDao;
import com.researchspace.model.Community;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.apps.App;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.ShareRecordMessageOrRequestCreationConfiguration;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.events.AccountEventType;
import com.researchspace.model.events.UserAccountEvent;
import com.researchspace.model.field.Field;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestRole;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.netfiles.NfsFieldLink;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.stoichiometry.MoleculeRole;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryInventoryLink;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.UserDeletionPolicy.UserTypeRestriction;
import com.researchspace.service.cloud.CloudNotificationManager;
import com.researchspace.service.cloud.CommunityUserManager;
import com.researchspace.service.impl.TemplateTransferService;
import com.researchspace.service.inventory.SampleRequestApiManager;
import com.researchspace.testutils.GalleryFilestoreTestUtils;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import com.researchspace.testutils.TestFactory;
import com.researchspace.testutils.TestGroup;
import jakarta.ws.rs.NotFoundException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectRetrievalFailureException;

public class UserDeletionManagerTestIT extends RealTransactionSpringTestBase {

  private @Autowired SampleRequestApiManager sampleRequestApiMgr;
  private @Autowired SystemPropertyManager systemPropertyMgr;
  private String originalSampleRequestsAvailable;

  private @Autowired UserDeletionManager userDeletionMgr;
  private @Autowired CommunityUserManager communityUserMgr;
  private @Autowired CloudNotificationManager cloudNotificationManager;
  private @Autowired MessageOrRequestCreatorManager requestCreatorManager;
  private @Autowired NfsManager nfsManager;
  private @Autowired NfsDao nfsDao;
  private @Autowired UserAppConfigManager userAppConfigManager;
  private @Autowired RecordFavoritesManager favMgr;
  private @Autowired UserConnectionManager userConn;
  private @Autowired JdbcTemplate jdbcTemplate;
  private @Autowired StoichiometryManager stoichiometryMgr;
  private @Autowired StoichiometryMoleculeManager stoichiometryMoleculeMgr;
  private @Autowired RSChemElementManager rsChemElementMgr;
  private @Autowired StoichiometryInventoryLinkDao stoichiometryInventoryLinkDao;
  private @Autowired InstrumentDao instrumentDao;
  private @Autowired ContainerDao containerDao;
  private @Autowired SampleDao sampleDao;
  private @Autowired InventoryLinkDao inventoryLinkDao;
  private @Autowired AuditTrailService auditService;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @AfterEach
  public void tearDown() throws Exception {
    restoreSampleRequestsAvailable();
    RSpaceTestUtils.logout();
    super.tearDown();
  }

  /** This class commits, so a changed system property would otherwise leak into later tests. */
  private void restoreSampleRequestsAvailable() {
    if (originalSampleRequestsAvailable != null) {
      logoutAndLoginAsSysAdmin();
      systemPropertyMgr.save(
          SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
          originalSampleRequestsAvailable,
          getSysAdminUser());
      originalSampleRequestsAvailable = null;
    }
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

  // RSDEV-1636: temporary accounts get the same final sweep as other deletions
  @Test
  public void removeTempUserRemovesResidualAccessEntries() throws Exception {
    User owner = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(owner, "doc");
    final User tempuser =
        communityUserMgr.createInvitedUser(
            getRandomAlphabeticString("temp") + "@researchspace.com");
    Folder pairFolder =
        doInTransaction(
            () -> {
              Folder folder = recordFactory.createIndividualSharedFolder(owner, tempuser);
              perFactory.setUpACLForIndividSharedFolder(owner, tempuser, folder);
              folderMgr.addChild(
                  folderDao.getIndividualSharedItemsFolderForUser(owner).getId(),
                  folder,
                  owner,
                  ACLPropagationPolicy.NULL_POLICY);
              return folder;
            });
    jdbcTemplate.update(
        "update BaseRecord set acl = concat(acl, '&', ?, '=RECORD:READ:') where id = ?",
        tempuser.getUsername(),
        doc.getId());
    assertThat(aclNames(pairFolder.getId())).contains(tempuser.getUsername());

    User sysadmin = logoutAndLoginAsSysAdmin();
    assertTrue(
        userDeletionMgr
            .removeUser(tempuser.getId(), getDeleteTempUserPolicy(), sysadmin)
            .isSucceeded());

    assertUserNotExist(tempuser);
    for (Long id : List.of(doc.getId(), pairFolder.getId())) {
      assertThat(aclNames(id)).contains(owner.getUsername()).doesNotContain(tempuser.getUsername());
    }
  }

  private int countCommunicationTargetRows() {
    return countRowsInTable(jdbcTemplate, "CommunicationTarget");
  }

  private void assertUserNotExist(final User tempuser) throws Exception {
    Long userId = tempuser.getId();
    assertThrows(DataAccessException.class, () -> userMgr.get(userId));
  }

  @Test
  public void removeTempUserFailsIfUserIsInitialized() throws Exception {
    final User tempuser =
        communityUserMgr.createInvitedUser(
            getRandomAlphabeticString("temp") + "@researchspace.com");
    initUser(tempuser, false); // folders, permissions etc added
    final User sysadmin = logoutAndLoginAsSysAdmin();
    final UserDeletionPolicy policy = getDeleteTempUserPolicy();

    Long userId = tempuser.getId();
    assertThrows(
        DataAccessException.class, () -> userDeletionMgr.removeUser(userId, policy, sysadmin));
  }

  @Test
  @EnabledIfSystemProperty(named = "nightly", matches = "(|true)") // is slow to execute
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
    assertThat(attachmentOnFilestore).exists();

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
    assertThat(attachmentOnFilestore).exists();

    // call removal of user's filestore resources
    ServiceOperationResult<Integer> deleteResourcesResult =
        userDeletionMgr.deleteRemovedUserFilestoreResources(tempuser.getId(), true, sysadmin);
    assertTrue(deleteResourcesResult.isSucceeded());
    // assert attachment file is now deleted from filestore
    int deletedResourcesCount = deleteResourcesResult.getEntity();
    assertEquals(
        23,
        deletedResourcesCount,
        "unexpected number of deleted filestore resources after user removal");
    assertFalse(attachmentOnFilestore.exists());
  }

  private void createAppConfigForUser(User user) {
    // enable slack, add a slack channel
    userAppConfigManager.getByAppName("app.slack", user);

    Map<String, String> channelOptions = new HashMap<>();
    channelOptions.put("SLACK_TEAM_NAME", "testTeamName");
    channelOptions.put("SLACK_CHANNEL_NAME", "testChannelName");
    channelOptions.put("SLACK_CHANNEL_LABEL", "testLabel");
    channelOptions.put("SLACK_USER_ID", "U123");
    channelOptions.put("SLACK_TEAM_ID", "T456");
    channelOptions.put("SLACK_CHANNEL_ID", "C789");

    userAppConfigManager.saveAppConfigElementSet(channelOptions, null, true, user, App.APP_SLACK);
  }

  private Long getFilePropertyCount(String username) {
    openTransaction();
    Object rc =
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(distinct fp.id) from FileProperty fp where fp.fileOwner = :owner",
                Long.class)
            .setParameter("owner", username)
            .getSingleResult();
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
    assertEquals(report.getMessage(), messages.getMessage("errors.deleteUser.nonSelf"));
  }

  @Test
  public void testOnlyAdminCanDelete() throws Exception {
    final User notAnAdmin = createAndSaveUser(getRandomAlphabeticString("toDelete"), "ROLE_USER");
    initUser(notAnAdmin, true);
    final User toDelete = createAndSaveUser(getRandomAlphabeticString("toDelete"));
    logoutAndLoginAs(notAnAdmin);
    final UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    Long userId = toDelete.getId();
    assertThrows(
        AuthorizationException.class, () -> userDeletionMgr.removeUser(userId, policy, notAnAdmin));
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
    assertThat(report.getMessage())
        .as(report.getMessage())
        .contains("Sorry, cannot remove the only admin or PI");
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
    addGalleryFileToInventoryItem(new GlobalIdentifier(complexSample.getGlobalId()), toDelete);
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

  // RSDEV-1636
  @Test
  public void deletionRevokesIndividualSharesWithTheDeletedUser() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), "ROLE_PI");
    User owner = createAndSaveUser(getRandomAlphabeticString("owner"));
    User sharee = createAndSaveUser(getRandomAlphabeticString("sharee"));
    User otherSharee = createAndSaveUser(getRandomAlphabeticString("other"));
    User prefixedSharee = createAndSaveUser(sharee.getUsername() + "2");
    initUsers(pi, owner, sharee, otherSharee, prefixedSharee);
    logoutAndLoginAs(pi);
    createGroupForUsers(pi, pi.getUsername(), "", pi, owner, sharee, otherSharee, prefixedSharee);

    logoutAndLoginAs(owner);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(owner, "doc");
    StructuredDocument prefixedDoc = createBasicDocumentInRootFolderWithText(owner, "prefixed");
    StructuredDocument residueDoc = createBasicDocumentInRootFolderWithText(owner, "residue");
    Notebook notebook = createNotebookWithNEntries(owner.getRootFolder().getId(), "nb", 1, owner);
    BaseRecord entry = doInTransaction(() -> folderDao.getChildren(notebook).get(0));
    share(owner, doc, sharee, "write");
    share(owner, doc, otherSharee, "write");
    share(owner, notebook, sharee, "read");
    share(owner, entry, sharee, "write");
    share(owner, prefixedDoc, prefixedSharee, "read");
    List<Long> incomingShares =
        List.of(shareId(doc, sharee), shareId(notebook, sharee), shareId(entry, sharee));
    logoutAndLoginAs(sharee);
    StructuredDocument shareesDoc = createBasicDocumentInRootFolderWithText(sharee, "own");
    share(sharee, shareesDoc, owner, "read");
    // residue of an older version: an entry without a share
    jdbcTemplate.update(
        "update BaseRecord set acl = concat(acl, '&', ?, '=RECORD:READ:') where id = ?",
        sharee.getUsername(),
        residueDoc.getId());
    Folder pairFolder =
        doInTransaction(() -> folderDao.getIndividualSharedFolderForUsers(owner, sharee, null));

    User sysadmin = logoutAndLoginAsSysAdmin();
    Mockito.clearInvocations(auditService);
    assertTrue(
        userDeletionMgr
            .removeUser(sharee.getId(), unrestrictedDeletionPolicy(), sysadmin)
            .isSucceeded());

    assertEquals(
        0,
        countRowsInTableWhere(jdbcTemplate, "RecordGroupSharing", "sharee_id=" + sharee.getId()));
    for (Long id :
        List.of(
            doc.getId(), notebook.getId(), entry.getId(), residueDoc.getId(), pairFolder.getId())) {
      assertThat(aclNames(id)).doesNotContain(sharee.getUsername());
    }
    assertThat(aclNames(doc.getId())).contains(otherSharee.getUsername());
    assertThat(aclNames(prefixedDoc.getId())).contains(prefixedSharee.getUsername());
    assertEquals(
        0,
        countRowsInTableWhere(jdbcTemplate, "RecordToFolder", "record_id=" + pairFolder.getId()));
    assertEquals(
        1,
        countRowsInTableWhere(jdbcTemplate, "BaseRecord", "name='" + pairFolder.getName() + "'"));
    // one event per removed row, the notebook's separately shared entry included
    assertThat(unshareEvents())
        .containsExactlyInAnyOrderElementsOf(events(incomingShares, sysadmin));

    User reused = createAndSaveUser(sharee.getUsername());
    initUsers(reused);
    logoutAndLoginAs(reused);
    doInTransaction(
        () -> {
          for (BaseRecord item :
              List.of(
                  recordMgr.get(doc.getId()),
                  folderMgr.getNotebook(notebook.getId()),
                  recordMgr.get(entry.getId()))) {
            assertFalse(permissionUtils.isPermitted(item, PermissionType.READ, reused));
            assertFalse(permissionUtils.isPermitted(item, PermissionType.WRITE, reused));
          }
        });
  }

  // RSDEV-1636: a legacy share whose recorded sharer did not create the folder it is linked in
  @Test
  public void deletionUnlinksLegacyShareRecordedWithAnotherSharer() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), "ROLE_PI");
    User owner = createAndSaveUser(getRandomAlphabeticString("owner"));
    User sharee = createAndSaveUser(getRandomAlphabeticString("sharee"));
    initUsers(pi, owner, sharee);
    logoutAndLoginAs(pi);
    createGroupForUsers(pi, pi.getUsername(), "", pi, owner, sharee);
    logoutAndLoginAs(owner);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(owner, "doc");
    share(owner, doc, sharee, "read");
    Long shareId = shareId(doc, sharee);
    jdbcTemplate.update(
        "update RecordGroupSharing set sharedBy_id = ? where shared_id = ? and sharee_id = ?",
        pi.getId(),
        doc.getId(),
        sharee.getId());
    Folder pairFolder =
        doInTransaction(() -> folderDao.getIndividualSharedFolderForUsers(owner, sharee, null));

    User sysadmin = logoutAndLoginAsSysAdmin();
    Mockito.clearInvocations(auditService);
    assertTrue(
        userDeletionMgr
            .removeUser(sharee.getId(), unrestrictedDeletionPolicy(), sysadmin)
            .isSucceeded());

    assertThat(childIds(pairFolder)).isEmpty();
    assertEquals(
        0,
        countRowsInTableWhere(jdbcTemplate, "RecordToFolder", "record_id=" + pairFolder.getId()));
    assertEquals(
        0,
        countRowsInTableWhere(
            jdbcTemplate,
            "BaseRecord",
            "name like '%"
                + pi.getUsername()
                + "%' and name like '%"
                + sharee.getUsername()
                + "%'"));
    assertThat(aclNames(doc.getId())).doesNotContain(sharee.getUsername());
    assertThat(unshareEvents()).containsExactly(Map.entry(shareId, sysadmin.getUsername()));
  }

  private void share(User sharer, BaseRecord item, User sharee, String permission) {
    ShareConfigElement cfg = new ShareConfigElement(sharee.getId(), permission);
    cfg.setUserId(sharee.getId());
    assertTrue(
        sharingMgr.shareRecord(sharer, item.getId(), new ShareConfigElement[] {cfg}).isSucceeded());
  }

  private List<String> aclNames(Long recordId) {
    String acl =
        jdbcTemplate.queryForObject(
            "select acl from BaseRecord where id = ?", String.class, recordId);
    return Stream.of(acl.split("&")).map(el -> el.split("=")[0]).collect(Collectors.toList());
  }

  private List<Long> childIds(Folder folder) {
    return jdbcTemplate.queryForList(
        "select record_id from RecordToFolder where folder_id = ?", Long.class, folder.getId());
  }

  private Long shareId(BaseRecord item, User sharee) {
    return jdbcTemplate.queryForObject(
        "select id from RecordGroupSharing where shared_id = ? and sharee_id = ?",
        Long.class,
        item.getId(),
        sharee.getId());
  }

  // the UNSHARE events as (share row id, subject)
  private List<Map.Entry<Long, String>> unshareEvents() {
    ArgumentCaptor<HistoricalEvent> events = ArgumentCaptor.forClass(HistoricalEvent.class);
    Mockito.verify(auditService, Mockito.atLeast(0)).notify(events.capture());
    return events.getAllValues().stream()
        .filter(e -> e instanceof GenericEvent && e.getAuditAction() == AuditAction.UNSHARE)
        .map(
            e ->
                Map.entry(
                    ((RecordGroupSharing) e.getAuditedObject()).getId(),
                    e.getSubject().getUniqueName()))
        .collect(Collectors.toList());
  }

  private static List<Map.Entry<Long, String>> events(List<Long> shareIds, User subject) {
    return shareIds.stream()
        .map(id -> Map.entry(id, subject.getUsername()))
        .collect(Collectors.toList());
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
    assertEquals(true, report.isSucceeded());
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
    assertEquals(true, report.isSucceeded());
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
    assertEquals(false, report.isSucceeded());
  }

  @Test
  public void removeUserWhoseFilestoreIsLinkedFromDocuments() throws Exception {
    final User toDelete = createAndSaveUser(getRandomAlphabeticString("fsOwner"));
    final User other = createAndSaveUser(getRandomAlphabeticString("fsOther"));
    initUsers(toDelete, other);
    NfsFileSystem fileSystem = GalleryFilestoreTestUtils.createS3FileSystem(null);
    nfsManager.saveNfsFileSystem(fileSystem);
    NfsFileStore storeOfDeleted =
        GalleryFilestoreTestUtils.createFileStore("mine", toDelete, fileSystem);
    nfsManager.saveNfsFileStore(storeOfDeleted);
    NfsFileStore storeOfOther =
        GalleryFilestoreTestUtils.createFileStore("theirs", other, fileSystem);
    nfsManager.saveNfsFileStore(storeOfOther);
    logoutAndLoginAs(toDelete);
    StructuredDocument ownDoc = createBasicDocumentInRootFolderWithText(toDelete, "any");
    logoutAndLoginAs(other);
    StructuredDocument othersDoc = createBasicDocumentInRootFolderWithText(other, "any");
    Field ownField = ownDoc.getFields().get(0);
    Field othersField = othersDoc.getFields().get(0);
    // own document via own filestore, own document via other's filestore, other's document via own
    // filestore: each is removed by a different user-deletion statement
    openTransaction();
    nfsDao.saveFieldLink(new NfsFieldLink(ownField, storeOfDeleted, "/run.dat"));
    nfsDao.saveFieldLink(new NfsFieldLink(ownField, storeOfOther, "/run.dat"));
    nfsDao.saveFieldLink(new NfsFieldLink(othersField, storeOfDeleted, "/run.dat"));
    commitTransaction();

    final User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(toDelete.getId(), unrestrictedDeletionPolicy(), sysadmin);

    assertTrue(report.isSucceeded());
    openTransaction();
    assertThat(nfsDao.getFieldLinks(othersField.getId())).isEmpty();
    commitTransaction();
  }

  @Test
  public void removeUserWhoseFilestoreAnInventoryLinkTargets() throws Exception {
    // the link row must not hold the filestore row back
    final User toDelete = createAndSaveUser(getRandomAlphabeticString("fsOwner"));
    initUser(toDelete, true);
    NfsFileSystem fileSystem = GalleryFilestoreTestUtils.createS3FileSystem(null);
    nfsManager.saveNfsFileSystem(fileSystem);
    NfsFileStore fileStore =
        GalleryFilestoreTestUtils.createFileStore("mine", toDelete, fileSystem);
    nfsManager.saveNfsFileStore(fileStore);
    InventoryLink link = new InventoryLink();
    link.setRelationType("IsSourceOf");
    link.setFilestoreTarget(fileStore, "/XRD/run.dat");
    openTransaction();
    inventoryLinkDao.save(link);
    commitTransaction();

    final User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(toDelete.getId(), unrestrictedDeletionPolicy(), sysadmin);

    assertTrue(report.isSucceeded());
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
  public void testDeleteUserTransfersFormsUsedByOthersToSysadmin() {
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
  public void testDeleteUserDeletesFormsOnlyUsedByDeletedUser() {
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
    Long formId = u1Form.getId();
    assertThrows(ObjectRetrievalFailureException.class, () -> formMgr.get(formId));
  }

  @Test
  public void testDeleteUserWithRSChemElementAndStoichiometry() throws Exception {
    User userToDelete = createInitAndLoginAnyUser();

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(userToDelete, "Chemistry doc");

    RSChemElement chemElement = RSChemElement.builder().chemElements("CCO").record(doc).build();
    chemElement = rsChemElementMgr.save(chemElement, userToDelete);

    Stoichiometry stoichiometry =
        Stoichiometry.builder().parentReaction(chemElement).record(doc).build();
    stoichiometry = stoichiometryMgr.save(stoichiometry);

    RSChemElement moleculeChemElement =
        RSChemElement.builder().chemElements("C2H6O").record(doc).build();
    moleculeChemElement = rsChemElementMgr.save(moleculeChemElement, userToDelete);

    StoichiometryMolecule molecule =
        StoichiometryMolecule.builder()
            .stoichiometry(stoichiometry)
            .rsChemElement(moleculeChemElement)
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .molecularWeight(46.07)
            .build();
    stoichiometry.addMolecule(molecule);
    stoichiometryMgr.save(stoichiometry);

    User sysadmin = logoutAndLoginAsSysAdmin();
    UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(userToDelete.getId(), policy, sysadmin);

    assertTrue(result.isSucceeded());
    assertUserNotExist(userToDelete);
  }

  @Test
  public void testDeleteUserWithReactionlessStoichiometry() throws Exception {
    User userToDelete = createInitAndLoginAnyUser();

    StructuredDocument doc = createBasicDocumentInRootFolderWithText(userToDelete, "Chemistry doc");

    Stoichiometry stoichiometry = Stoichiometry.builder().record(doc).build();
    stoichiometry = stoichiometryMgr.save(stoichiometry);

    RSChemElement moleculeChemElement =
        RSChemElement.builder().chemElements("C2H6O").record(doc).build();
    moleculeChemElement = rsChemElementMgr.save(moleculeChemElement, userToDelete);

    StoichiometryMolecule molecule =
        StoichiometryMolecule.builder()
            .stoichiometry(stoichiometry)
            .rsChemElement(moleculeChemElement)
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .molecularWeight(46.07)
            .build();
    stoichiometry.addMolecule(molecule);
    stoichiometryMgr.save(stoichiometry);

    User sysadmin = logoutAndLoginAsSysAdmin();
    UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(userToDelete.getId(), policy, sysadmin);

    assertTrue(result.isSucceeded());
    assertUserNotExist(userToDelete);
  }

  @Test
  public void testDeleteUserWithStoichiometryAndInventoryLink() throws Exception {
    User userToDelete = createInitAndLoginAnyUser();
    StoichiometryMolecule savedMolecule = createStoichiometryWithMoleculeForUser(userToDelete);

    ApiSampleWithFullSubSamples apiSample = createBasicSampleForUser(userToDelete);
    openTransaction();
    Sample sample = sampleDao.get(apiSample.getId());
    SubSample subSample = sample.getSubSamples().get(0);
    Long linkId = saveStoichiometryInventoryLink(savedMolecule, subSample);
    commitTransaction();

    User sysadmin = logoutAndLoginAsSysAdmin();
    UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(userToDelete.getId(), policy, sysadmin);

    assertTrue(result.isSucceeded());
    assertUserNotExist(userToDelete);
    assertStoichiometryInventoryLinkAndAuditDeleted(linkId);
  }

  @Test
  public void testDeleteUserOwningStoichiometryLinkedToOtherUsersInventory() throws Exception {
    // Inventory belongs to a different user from the one being deleted, so
    // deleteInventoryItems cannot clean up the link row — the SQL in
    // deleteRecords (joined via BaseRecord.owner_id) is the only thing that
    // can remove it before the StoichiometryMolecule delete runs.
    User inventoryOwner = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples apiSample = createBasicSampleForUser(inventoryOwner);

    RSpaceTestUtils.logout();
    User userToDelete = createInitAndLoginAnyUser();
    StoichiometryMolecule savedMolecule = createStoichiometryWithMoleculeForUser(userToDelete);

    openTransaction();
    Sample sample = sampleDao.get(apiSample.getId());
    Long linkId = saveStoichiometryInventoryLink(savedMolecule, sample);
    commitTransaction();

    User sysadmin = logoutAndLoginAsSysAdmin();
    UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(userToDelete.getId(), policy, sysadmin);

    assertTrue(result.isSucceeded());
    assertUserNotExist(userToDelete);
    assertStoichiometryInventoryLinkAndAuditDeleted(linkId);
  }

  @Test
  public void testDeleteUserOwningInventoryLinkedFromOtherUsersStoichiometry() throws Exception {
    User stoichOwner = createInitAndLoginAnyUser();
    StoichiometryMolecule savedMolecule = createStoichiometryWithMoleculeForUser(stoichOwner);

    RSpaceTestUtils.logout();
    User inventoryOwner = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples apiSample = createBasicSampleForUser(inventoryOwner);

    openTransaction();
    Sample sample = sampleDao.get(apiSample.getId());
    Long linkId = saveStoichiometryInventoryLink(savedMolecule, sample);
    commitTransaction();

    User sysadmin = logoutAndLoginAsSysAdmin();
    UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(inventoryOwner.getId(), policy, sysadmin);

    assertTrue(result.isSucceeded());
    assertUserNotExist(inventoryOwner);
    assertStoichiometryInventoryLinkAndAuditDeleted(linkId);
  }

  @Test
  public void testDeleteUserOwningContainerLinkedFromOtherUsersStoichiometry() throws Exception {
    // Exercises the container_id join in deleteInventoryItems.
    User stoichOwner = createInitAndLoginAnyUser();
    StoichiometryMolecule savedMolecule = createStoichiometryWithMoleculeForUser(stoichOwner);

    RSpaceTestUtils.logout();
    User inventoryOwner = createInitAndLoginAnyUser();
    ApiContainer apiContainer = createBasicContainerForUser(inventoryOwner);

    openTransaction();
    Container container = containerDao.get(apiContainer.getId());
    Long linkId = saveStoichiometryInventoryLink(savedMolecule, container);
    commitTransaction();

    User sysadmin = logoutAndLoginAsSysAdmin();
    UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(inventoryOwner.getId(), policy, sysadmin);

    assertTrue(result.isSucceeded());
    assertUserNotExist(inventoryOwner);
    assertStoichiometryInventoryLinkAndAuditDeleted(linkId);
  }

  @Test
  public void testDeleteUserOwningInstrumentLinkedFromOtherUsersStoichiometry() throws Exception {
    // Exercises the instrumentEntity_id join in deleteInventoryItems.
    User stoichOwner = createInitAndLoginAnyUser();
    StoichiometryMolecule savedMolecule = createStoichiometryWithMoleculeForUser(stoichOwner);

    RSpaceTestUtils.logout();
    User inventoryOwner = createInitAndLoginAnyUser();
    ApiInstrument apiInstrument = createBasicInstrumentForUser(inventoryOwner);

    openTransaction();
    Instrument instrument = instrumentDao.get(apiInstrument.getId());
    Long linkId = saveStoichiometryInventoryLink(savedMolecule, instrument);
    commitTransaction();

    User sysadmin = logoutAndLoginAsSysAdmin();
    UserDeletionPolicy policy = unrestrictedDeletionPolicy();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(inventoryOwner.getId(), policy, sysadmin);

    assertTrue(result.isSucceeded());
    assertUserNotExist(inventoryOwner);
    assertStoichiometryInventoryLinkAndAuditDeleted(linkId);
  }

  private StoichiometryMolecule createStoichiometryWithMoleculeForUser(User user) throws Exception {
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "Chemistry doc");

    RSChemElement chemElement = RSChemElement.builder().chemElements("CCO").record(doc).build();
    chemElement = rsChemElementMgr.save(chemElement, user);

    Stoichiometry stoichiometry =
        Stoichiometry.builder().parentReaction(chemElement).record(doc).build();
    stoichiometry = stoichiometryMgr.save(stoichiometry);

    RSChemElement moleculeChemElement =
        RSChemElement.builder().chemElements("C2H6O").record(doc).build();
    moleculeChemElement = rsChemElementMgr.save(moleculeChemElement, user);

    StoichiometryMolecule molecule =
        StoichiometryMolecule.builder()
            .stoichiometry(stoichiometry)
            .rsChemElement(moleculeChemElement)
            .role(MoleculeRole.REACTANT)
            .formula("C2H6O")
            .name("Ethanol")
            .smiles("CCO")
            .molecularWeight(46.07)
            .build();
    stoichiometry.addMolecule(molecule);
    stoichiometry = stoichiometryMgr.save(stoichiometry);
    return stoichiometry.getMolecules().get(0);
  }

  private Long saveStoichiometryInventoryLink(
      StoichiometryMolecule molecule, InventoryRecord inventoryRecord) {
    StoichiometryInventoryLink link = new StoichiometryInventoryLink();
    // Reload the molecule within the current transaction so it is a managed (not detached)
    // entity, mirroring StoichiometryInventoryLinkManagerImpl.createLink. Hibernate 6's persist()
    // cannot cascade to a detached association, unlike Hibernate 5's saveOrUpdate().
    link.setStoichiometryMolecule(stoichiometryMoleculeMgr.getById(molecule.getId()));
    link.setInventoryRecord(inventoryRecord);
    return stoichiometryInventoryLinkDao.save(link).getId();
  }

  private void assertStoichiometryInventoryLinkAndAuditDeleted(Long linkId) {
    String where = "id = " + linkId;
    assertEquals(
        0,
        countRowsInTableWhere(jdbcTemplate, "StoichiometryInventoryLink", where),
        "StoichiometryInventoryLink row should have been deleted");
    assertEquals(
        0,
        countRowsInTableWhere(jdbcTemplate, "StoichiometryInventoryLink_AUD", where),
        "StoichiometryInventoryLink_AUD rows should have been deleted");
  }

  @Test
  public void deleteUserFormThatReferencesPreviousVersion() throws Exception {
    User formCreator = createInitAndLoginAnyUser();

    RSForm originalForm = createAnyForm(formCreator);
    originalForm.publish();
    formMgr.save(originalForm);

    RSForm newVersion = createAnyForm(formCreator);
    newVersion.publish();
    formMgr.save(newVersion);

    // Set originalForm as previousVersion for newVersion
    jdbcTemplate.update(
        "UPDATE RSForm SET previousVersion_id = ? WHERE id = ?",
        originalForm.getId(),
        newVersion.getId());

    User sysadmin = logoutAndLoginAsSysAdmin();
    UserDeletionPolicy policy = unrestrictedDeletionPolicy();

    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(formCreator.getId(), policy, sysadmin);

    assertTrue(result.isSucceeded());
    assertUserNotExist(formCreator);
  }

  @Test
  public void deleteUserFormWithTempVersion() throws Exception {
    User formCreator = createInitAndLoginAnyUser();

    RSForm form1 = createAnyForm(formCreator);
    form1.publish();
    formMgr.save(form1);

    RSForm form2 = createAnyForm(formCreator);
    form2.publish();
    formMgr.save(form2);

    List<Long> fieldIds =
        jdbcTemplate.queryForList(
            "SELECT id FROM FieldForm WHERE form_id IN (?, ?)",
            Long.class,
            form1.getId(),
            form2.getId());

    // set up the temp field reference between the two forms
    jdbcTemplate.update(
        "UPDATE FieldForm SET tempFieldForm_id = ? WHERE id = ?", fieldIds.get(0), fieldIds.get(1));

    User sysadmin = logoutAndLoginAsSysAdmin();
    UserDeletionPolicy policy = unrestrictedDeletionPolicy();

    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(formCreator.getId(), policy, sysadmin);

    assertTrue(result.isSucceeded());
    assertUserNotExist(formCreator);
  }

  @Test
  public void whenFormUsedByOtherUserThenFormVersionChainTransferredToSysadmin() throws Exception {
    // Create userA and create initial form (version 0)
    User userA = createInitAndLoginAnyUser();
    RSForm formVersion0 = createAnyForm(userA);
    formVersion0.getAccessControl().setWorldPermissionType(PermissionType.READ);
    formVersion0.publish();
    formMgr.save(formVersion0);

    // As userA, create version 1 and link to version 0 via previousVersion
    RSForm formVersion1 = createAnyForm(userA);
    formVersion1.publish();
    formMgr.save(formVersion1);

    jdbcTemplate.update(
        "UPDATE RSForm SET previousVersion_id = ? WHERE id = ?",
        formVersion0.getId(),
        formVersion1.getId());

    // As userB create a document from version 1
    User userB = createInitAndLoginAnyUser();
    Record userBDoc =
        recordFactory.createStructuredDocument("doc from formV1", userB, formVersion1);
    recordMgr.save(userBDoc, userB);

    //    // As userA, create version 2
    logoutAndLoginAs(userA);
    RSForm formVersion2 = createAnyForm(userA);
    formVersion2.publish();
    formMgr.save(formVersion2);

    jdbcTemplate.update(
        "UPDATE RSForm SET previousVersion_id = ? WHERE id = ?",
        formVersion1.getId(),
        formVersion2.getId());

    // As sysadmin, delete userA
    User sysadmin = logoutAndLoginAsSysAdmin();
    UserDeletionPolicy policy = unrestrictedDeletionPolicy();

    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(userA.getId(), policy, sysadmin);

    assertTrue(result.isSucceeded());
    assertUserNotExist(userA);

    // assert form version chain forward and backwards from version used by userB are transferred to
    // sysadmin
    assertFormExistsOwnedBySysadmin(formVersion0.getId(), sysadmin);
    assertFormExistsOwnedBySysadmin(formVersion1.getId(), sysadmin);
    assertFormExistsOwnedBySysadmin(formVersion2.getId(), sysadmin);
  }

  @Test
  public void testDeleteUserTransfersTemplateSharedWithGroupToSysadmin() throws Exception {
    TestGroup testGroup = createTestGroup(1);
    User userToDelete = testGroup.u1();
    User pi = testGroup.getPi();

    logoutAndLoginAs(userToDelete);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(userToDelete, "doc");
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(doc.getId(), userToDelete);
    shareRecordWithGroup(userToDelete, testGroup.getGroup(), template);

    logoutAndLoginAs(pi);
    StructuredDocument piDoc =
        recordMgr
            .createFromTemplate(
                template.getId(), "piDoc", pi, folderMgr.getRootFolderForUser(pi).getId())
            .getUniqueCopy()
            .asStrucDoc();

    User sysadmin =
        createAndSaveUser(getRandomAlphabeticString("sysadmin"), Role.SYSTEM_ROLE.getName());
    initUser(sysadmin);
    logoutAndLoginAs(sysadmin);
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(userToDelete.getId(), unrestrictedDeletionPolicy(), sysadmin);

    assertTrue(report.isSucceeded());

    StructuredDocument transferredTemplate = (StructuredDocument) recordMgr.get(template.getId());
    assertEquals(sysadmin, transferredTemplate.getOwner());

    Folder userFolder = findDeletedUsersTemplateFolder(userToDelete, sysadmin);
    assertThat(folderMgr.getFolderChildrenIds(userFolder)).contains(template.getId());

    assertNotNull(recordMgr.get(piDoc.getId()));
  }

  @Test
  public void testDeleteUserTransfersTemplateSharedDirectlyWithUserToSysadmin() throws Exception {
    // user-level sharing requires both users to be in the same group
    TestGroup testGroup = createTestGroup(2);
    User userToDelete = testGroup.u1();
    User userB = testGroup.u2();

    logoutAndLoginAs(userToDelete);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(userToDelete, "doc");
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(doc.getId(), userToDelete);
    shareRecordWithUser(userToDelete, template, userB);

    logoutAndLoginAs(userB);
    StructuredDocument userBDoc =
        recordMgr
            .createFromTemplate(
                template.getId(), "userBDoc", userB, folderMgr.getRootFolderForUser(userB).getId())
            .getUniqueCopy()
            .asStrucDoc();

    User sysadmin =
        createAndSaveUser(getRandomAlphabeticString("sysadmin"), Role.SYSTEM_ROLE.getName());
    initUser(sysadmin);
    logoutAndLoginAs(sysadmin);
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(userToDelete.getId(), unrestrictedDeletionPolicy(), sysadmin);

    assertTrue(report.isSucceeded());

    StructuredDocument transferredTemplate = (StructuredDocument) recordMgr.get(template.getId());
    assertEquals(sysadmin, transferredTemplate.getOwner());

    Folder userFolder = findDeletedUsersTemplateFolder(userToDelete, sysadmin);
    assertThat(folderMgr.getFolderChildrenIds(userFolder)).contains(template.getId());

    assertNotNull(recordMgr.get(userBDoc.getId()));
  }

  @Test
  public void testDeleteUserDeletesTemplateOnlyUsedByDeletedUser() throws Exception {
    User userToDelete = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(userToDelete, "doc");
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(doc.getId(), userToDelete);
    createFromTemplate(userToDelete, template, "docFromOwnTemplate");

    User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(userToDelete.getId(), unrestrictedDeletionPolicy(), sysadmin);

    assertTrue(report.isSucceeded());
    Long templateId = template.getId();
    assertThrows(ObjectRetrievalFailureException.class, () -> recordMgr.get(templateId));
  }

  @Test
  public void testDeleteUserTransfersTemplateWithGalleryAttachmentToSysadmin() throws Exception {
    TestGroup testGroup = createTestGroup(1);
    User userToDelete = testGroup.u1();
    User pi = testGroup.getPi();

    logoutAndLoginAs(userToDelete);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(userToDelete, "doc");
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(doc.getId(), userToDelete);
    EcatImage image = addImageToField(template.getFields().get(0), userToDelete);
    shareRecordWithGroup(userToDelete, testGroup.getGroup(), template);

    logoutAndLoginAs(pi);
    recordMgr
        .createFromTemplate(
            template.getId(), "piDoc", pi, folderMgr.getRootFolderForUser(pi).getId())
        .getUniqueCopy()
        .asStrucDoc();

    User sysadmin =
        createAndSaveUser(getRandomAlphabeticString("sysadmin"), Role.SYSTEM_ROLE.getName());
    initUser(sysadmin);
    logoutAndLoginAs(sysadmin);
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(userToDelete.getId(), unrestrictedDeletionPolicy(), sysadmin);

    assertTrue(report.isSucceeded());

    StructuredDocument transferredTemplate = (StructuredDocument) recordMgr.get(template.getId());
    assertEquals(sysadmin, transferredTemplate.getOwner());

    EcatImage transferredImage = (EcatImage) recordMgr.get(image.getId());
    assertEquals(sysadmin, transferredImage.getOwner());

    Folder galleryImagesFolder =
        recordMgr.getGalleryMediaFolderForUser(MediaUtils.IMAGES_MEDIA_FLDER_NAME, sysadmin);
    Folder galleryUserFolder =
        findDeletedUsersSubfolder(galleryImagesFolder, userToDelete, sysadmin);
    assertThat(folderMgr.getFolderChildrenIds(galleryUserFolder)).contains(image.getId());
  }

  @Test
  public void testDeleteUserWithBothSharedFormAndTemplateTransfersBothToSysadmin()
      throws Exception {
    // user-level sharing requires both users to be in the same group
    TestGroup testGroup = createTestGroup(2);
    User userToDelete = testGroup.u1();
    User userB = testGroup.u2();

    logoutAndLoginAs(userToDelete);
    RSForm form = createAnyForm(userToDelete);
    form.getAccessControl().setWorldPermissionType(PermissionType.READ);
    form.publish();
    formMgr.save(form);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(userToDelete, "doc");
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(doc.getId(), userToDelete);
    shareRecordWithUser(userToDelete, template, userB);

    logoutAndLoginAs(userB);
    Record userBDocFromForm = recordFactory.createStructuredDocument("docFromForm", userB, form);
    recordMgr.save(userBDocFromForm, userB);
    StructuredDocument userBDocFromTemplate =
        recordMgr
            .createFromTemplate(
                template.getId(),
                "docFromTemplate",
                userB,
                folderMgr.getRootFolderForUser(userB).getId())
            .getUniqueCopy()
            .asStrucDoc();

    User sysadmin =
        createAndSaveUser(getRandomAlphabeticString("sysadmin"), Role.SYSTEM_ROLE.getName());
    initUser(sysadmin);
    logoutAndLoginAs(sysadmin);
    ServiceOperationResult<User> report =
        userDeletionMgr.removeUser(userToDelete.getId(), unrestrictedDeletionPolicy(), sysadmin);

    assertTrue(report.isSucceeded());
    assertEquals(sysadmin, formMgr.get(form.getId()).getOwner());
    assertEquals(sysadmin, ((StructuredDocument) recordMgr.get(template.getId())).getOwner());

    Folder userFolder = findDeletedUsersTemplateFolder(userToDelete, sysadmin);
    assertThat(folderMgr.getFolderChildrenIds(userFolder)).contains(template.getId());

    assertNotNull(recordMgr.get(userBDocFromForm.getId()));
    assertNotNull(recordMgr.get(userBDocFromTemplate.getId()));
  }

  private Folder findDeletedUsersTemplateFolder(User deletedUser, User sysadmin) {
    String deletedFolderName =
        messages.getMessage(TemplateTransferService.DELETED_USER_TEMPLATES_FOLDER);
    Folder templateRoot = folderMgr.getTemplateFolderForUser(sysadmin);
    Folder deletedUsersFolder =
        folderMgr.getSubFolders(templateRoot).stream()
            .filter(f -> deletedFolderName.equals(f.getName()))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("'Deleted Users' folder not found under Templates"));
    return folderMgr.getSubFolders(deletedUsersFolder).stream()
        .filter(f -> deletedUser.getUsername().equals(f.getName()))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "User folder not found under Deleted Users: " + deletedUser.getUsername()));
  }

  private Folder findDeletedUsersSubfolder(Folder categoryFolder, User deletedUser, User sysadmin) {
    String deletedFolderName =
        messages.getMessage(TemplateTransferService.DELETED_USER_TEMPLATES_FOLDER);
    Folder deletedUsersFolder =
        folderMgr.getSubFolders(categoryFolder).stream()
            .filter(f -> deletedFolderName.equals(f.getName()))
            .findFirst()
            .orElseThrow(
                () ->
                    new AssertionError("'Deleted Users' subfolder not found in gallery category"));
    return folderMgr.getSubFolders(deletedUsersFolder).stream()
        .filter(f -> deletedUser.getUsername().equals(f.getName()))
        .findFirst()
        .orElseThrow(
            () ->
                new AssertionError(
                    "User subfolder not found in Deleted Users gallery: "
                        + deletedUser.getUsername()));
  }

  private void assertFormExistsOwnedBySysadmin(Long formId, User sysadmin) {
    RSForm form = formMgr.get(formId);
    assertEquals(sysadmin, form.getOwner());
  }

  private UserDeletionPolicy getDeleteTempUserPolicy() {
    return new UserDeletionPolicy(UserTypeRestriction.TEMP_USER);
  }

  @Test
  public void removeUserWhoRaisedASampleRequest() throws Exception {
    SampleRequestFixture fixture = createSampleRequestBetweenTwoUsers();

    User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(
            fixture.requester.getId(),
            new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION),
            sysadmin);

    assertTrue(result.isSucceeded(), "deleting a user who raised a sample request must succeed");
    assertUserNotExist(fixture.requester);

    // the request survives for the sample owner, still naming who asked
    ApiSampleRequest surviving =
        sampleRequestApiMgr.getRequestById(fixture.requestId, fixture.owner);
    assertEquals(fixture.requesterUsername, surviving.getRequester().getUsername());
    assertEquals(fixture.requester.getId(), surviving.getRequester().getId());
    assertNull(surviving.getRequester().getFirstName(), "a deleted requester has no name to show");
    assertEquals(
        fixture.requesterUsername,
        surviving.getStatusChanges().get(0).getCreatedBy().getUsername());

    // the owner's listing names them the same way, and the owner can still close the request
    logoutAndLoginAs(fixture.owner);
    ApiSampleRequestInfo listed =
        sampleRequestApiMgr
            .getRequestsForUser(
                PaginationCriteria.createDefaultForClass(SampleRequest.class),
                SampleRequestRole.OWNER,
                null,
                null,
                fixture.owner)
            .getRequests()
            .get(0);
    assertEquals(fixture.requester.getId(), listed.getRequester().getId());
    assertEquals(fixture.requesterUsername, listed.getRequester().getUsername());
    ApiRuntimeException transferRefused =
        assertThrows(
            ApiRuntimeException.class,
            () -> sampleRequestApiMgr.fulfilByTransfer(fixture.requestId, null, fixture.owner));
    assertEquals("errors.inventory.sampleRequest.requesterDeleted", transferRefused.getMessage());
    ApiSampleRequestStatusPut reject = new ApiSampleRequestStatusPut();
    reject.setStatus(SampleRequestStatus.REJECTED);
    reject.setReason("the requester has left");
    assertEquals(
        SampleRequestStatus.REJECTED,
        sampleRequestApiMgr.updateStatus(fixture.requestId, reject, fixture.owner).getStatus());
  }

  @Test
  public void removeOwnerOfARequestedSample() throws Exception {
    SampleRequestFixture fixture = createSampleRequestBetweenTwoUsers();

    User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(
            fixture.owner.getId(),
            new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION),
            sysadmin);

    assertTrue(result.isSucceeded(), "deleting the owner of a requested sample must succeed");
    assertUserNotExist(fixture.owner);

    // the sample is gone, so requests against it go with it
    assertThrows(
        NotFoundException.class,
        () -> sampleRequestApiMgr.getRequestById(fixture.requestId, fixture.requester));
  }

  @Test
  public void removeRecipientOfASampleThatFulfilledARequest() throws Exception {
    SampleRequestFixture fixture = createSampleRequestBetweenTwoUsers();

    // the owner fulfils the request with a new sample, then hands it over
    logoutAndLoginAs(fixture.owner);
    ApiSampleWithFullSubSamples aliquot = createBasicSampleForUser(fixture.owner);
    ApiSampleRequestStatusPut fulfil = new ApiSampleRequestStatusPut();
    fulfil.setStatus(SampleRequestStatus.FULFILLED);
    fulfil.setTransferredSampleGlobalId(aliquot.getGlobalId());
    sampleRequestApiMgr.updateStatus(fixture.requestId, fulfil, fixture.owner);
    ApiSample toRequester = new ApiSample();
    toRequester.setId(aliquot.getId());
    toRequester.setOwner(new ApiUser(fixture.requester));
    sampleApiMgr.changeApiSampleOwner(toRequester, fixture.owner);

    User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(
            fixture.requester.getId(),
            new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION),
            sysadmin);

    assertTrue(result.isSucceeded(), "deleting the recipient of a fulfilling sample must succeed");
    assertUserNotExist(fixture.requester);

    // the fulfilled request survives against the owner's sample, no longer naming the deleted
    // fulfilling sample
    ApiSampleRequest surviving =
        sampleRequestApiMgr.getRequestById(fixture.requestId, fixture.owner);
    assertEquals(SampleRequestStatus.FULFILLED, surviving.getStatus());
    assertNull(surviving.getStatusChanges().get(1).getTransferredSample());
  }

  @Test
  public void removeOriginalOwnerAfterTheRequestedSampleWasTransferred() throws Exception {
    SampleRequestFixture fixture = createSampleRequestBetweenTwoUsers();
    String ownerUsername = fixture.owner.getUsername();

    // the owner hands the requested sample to the requester, so it no longer goes with the owner
    logoutAndLoginAs(fixture.owner);
    ApiSample toRequester = new ApiSample();
    toRequester.setId(
        sampleRequestApiMgr.getRequestById(fixture.requestId, fixture.owner).getSample().getId());
    toRequester.setOwner(new ApiUser(fixture.requester));
    sampleApiMgr.changeApiSampleOwner(toRequester, fixture.owner);

    User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(
            fixture.owner.getId(),
            new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION),
            sysadmin);

    assertTrue(result.isSucceeded(), "deleting the original owner must succeed");
    assertUserNotExist(fixture.owner);

    ApiSampleRequest surviving =
        sampleRequestApiMgr.getRequestById(fixture.requestId, fixture.requester);
    assertEquals(ownerUsername, surviving.getOriginalOwner().getUsername());
    assertEquals(fixture.owner.getId(), surviving.getOriginalOwner().getId());
  }

  @Test
  public void removeUserWhoOnlyActedOnASampleRequest() throws Exception {
    User labAdmin = createAndSaveUser(getRandomAlphabeticString("srAdmin"));
    initUsers(labAdmin);
    SampleRequestFixture fixture = createSampleRequestBetweenTwoUsers(labAdmin);
    String labAdminUsername = labAdmin.getUsername();
    labAdmin = userMgr.getUserByUsername(labAdminUsername, true);

    // a lab admin of the owner's group transfers the sample, so the fulfilment is theirs
    logoutAndLoginAs(labAdmin);
    ApiSample toRequester = new ApiSample();
    toRequester.setId(
        sampleRequestApiMgr.getRequestById(fixture.requestId, fixture.owner).getSample().getId());
    toRequester.setOwner(new ApiUser(fixture.requester));
    sampleApiMgr.changeApiSampleOwner(toRequester, labAdmin);

    User sysadmin = logoutAndLoginAsSysAdmin();
    ServiceOperationResult<User> result =
        userDeletionMgr.removeUser(
            labAdmin.getId(), new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION), sysadmin);

    assertTrue(result.isSucceeded(), "deleting a user who only acted on a request must succeed");
    assertUserNotExist(labAdmin);

    ApiSampleRequest surviving =
        sampleRequestApiMgr.getRequestById(fixture.requestId, fixture.requester);
    ApiSampleRequestStatusChange fulfilment = surviving.getStatusChanges().get(1);
    assertEquals(SampleRequestStatus.FULFILLED, fulfilment.getStatus());
    assertEquals(labAdminUsername, fulfilment.getCreatedBy().getUsername());
    assertEquals(labAdmin.getId(), fulfilment.getCreatedBy().getId());
  }

  @Test
  public void reusedUsernameDoesNotInheritADeletedRequestersRequests() throws Exception {
    SampleRequestFixture fixture = createSampleRequestBetweenTwoUsers();

    User sysadmin = logoutAndLoginAsSysAdmin();
    assertTrue(
        userDeletionMgr
            .removeUser(
                fixture.requester.getId(),
                new UserDeletionPolicy(UserTypeRestriction.NO_RESTRICTION),
                sysadmin)
            .isSucceeded());
    User reused = createAndSaveUser(fixture.requesterUsername);

    assertThrows(
        NotFoundException.class,
        () -> sampleRequestApiMgr.getRequestById(fixture.requestId, reused));
    assertEquals(
        0L,
        sampleRequestApiMgr
            .getRequestsForUser(
                PaginationCriteria.createDefaultForClass(SampleRequest.class),
                null,
                null,
                null,
                reused)
            .getTotalHits()
            .longValue());
    ApiSampleRequestStatusPut cancel = new ApiSampleRequestStatusPut();
    cancel.setStatus(SampleRequestStatus.CANCELLED);
    assertThrows(
        NotFoundException.class,
        () -> sampleRequestApiMgr.updateStatus(fixture.requestId, cancel, reused));
  }

  private static class SampleRequestFixture {
    User owner;
    User requester;
    String requesterUsername;
    Long requestId;
  }

  private SampleRequestFixture createSampleRequestBetweenTwoUsers() throws Exception {
    return createSampleRequestBetweenTwoUsers(null);
  }

  /**
   * {@code labAdmin}, if given, is made a lab admin of the owner's group before the sample is
   * created.
   */
  private SampleRequestFixture createSampleRequestBetweenTwoUsers(User labAdmin) throws Exception {
    SampleRequestFixture fixture = new SampleRequestFixture();
    fixture.owner = createAndSaveUser(getRandomAlphabeticString("srOwner"), Constants.PI_ROLE);
    fixture.requester = createAndSaveUser(getRandomAlphabeticString("srReq"));
    initUsers(fixture.owner, fixture.requester);
    Group group = createGroupForUsersWithDefaultPi(fixture.owner, fixture.requester);
    if (labAdmin != null) {
      addUserToGroup(labAdmin, group, RoleInGroup.RS_LAB_ADMIN, piUser);
    }

    logoutAndLoginAsSysAdmin();
    originalSampleRequestsAvailable =
        systemPropertyMgr.findByName(SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE).getValue();
    systemPropertyMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());

    logoutAndLoginAs(fixture.owner);
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(fixture.owner);
    markSampleRequestable(sample, fixture.owner);

    logoutAndLoginAs(fixture.requester);
    ApiSampleRequestPost post = new ApiSampleRequestPost();
    post.setSampleGlobalId(sample.getGlobalId());
    post.setNote("needed for the assay");
    fixture.requestId = sampleRequestApiMgr.createRequest(post, fixture.requester).getId();
    fixture.requesterUsername = fixture.requester.getUsername();

    return fixture;
  }
}
