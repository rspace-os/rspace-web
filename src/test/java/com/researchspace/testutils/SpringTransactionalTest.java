package com.researchspace.testutils;

import static org.junit.Assert.assertEquals;

import com.researchspace.Constants;
import com.researchspace.dao.*;
import com.researchspace.files.service.ExternalFileStoreProvider;
import com.researchspace.files.service.InternalFileStore;
import com.researchspace.model.*;
import com.researchspace.model.field.Field;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.record.*;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.UserConnectionManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.search.Search;
import org.junit.After;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.jdbc.SqlScriptsTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.transaction.annotation.Transactional;

/**
 * Defines transactional behaviour for Spring transactional tests. Subclasses should remember to add
 * their own @After method which invokes super.tearDown() to ensure correct tidy up after each test.
 */
@TestExecutionListeners(
    value = {TransactionalTestExecutionListener.class, SqlScriptsTestExecutionListener.class})
@Transactional
public abstract class SpringTransactionalTest extends BaseManagerTestCaseBase {

  protected @Autowired CommunicationDao commsDao;
  protected @Autowired EcatCommentDao commentsDao;
  protected @Autowired IContentInitializer contentInitializer;
  protected @Autowired GroupDao grpdao;
  protected @Autowired RecordDeletionManager recordDeletionMgr;
  private @Autowired RoleDao roleDao;

  protected @Autowired InternalLinkDao internalLinkDao;
  protected @Autowired EcatImageAnnotationDao imageAnnotationDao;
  protected @Autowired InternalFileStore fileStore;
  protected @Autowired UserConnectionManager connMgr;

  @After
  public void tearDown() throws Exception {
    contentInitializer.setCustomInitActive(true); // restore defaults
  }

  /**
   * Creates, inits and logs in a new user with 'User' role and a random username, with default
   * folder structure initialised
   *
   * @return the user
   */
  protected User createInitAndLoginAnyUser() {
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    logoutAndLoginAs(user);
    return user;
  }

  /**
   * Gets total count of all items (including deleted, folders etc) in the user's root folder
   *
   * @param user
   * @return The item count
   */
  protected int getNumChildrenInRootFolder(User user) {
    Folder toCheck = user.getRootFolder();
    return folderDao.get(toCheck.getId()).getChildren().size();
  }

  protected void setUpPermissionsForUser(User u) {
    u.addRole(roleDao.getRoleByName("ROLE_USER"));
  }

  /**
   * Creates a user with specified username, which should be unique.
   *
   * @param uname
   * @return
   */
  protected User createAndSaveUserIfNotExists(String uname) {
    return createAndSaveUserIfNotExists(uname, Constants.USER_ROLE);
  }

  /**
   * Creates a temporary user with specified username, which should be unique. Temp user has :
   *
   * <ul>
   *   <li>No roles
   *   <li>is disabled and 'temp' flag set
   * </ul>
   *
   * @param uname
   * @return
   */
  protected User createAndSaveTmpUserIfNotExists(String uname) {
    User user = TestFactory.createAnyUser(uname);
    user.setEmail(uname + "@m.com");
    user.setEnabled(true);
    user.setTempAccount(true);
    return createAndSaveUserIfNotExists(user);
  }

  protected User createAndSaveUserIfNotExists(String uname, String role) {
    User user = TestFactory.createAnyUser(uname);
    user.setEmail(uname + "@m.com");
    user.addRole(roleDao.getRoleByName(role));
    if (role.equals(Constants.PI_ROLE) || role.equals(Constants.GROUP_OWNER_ROLE)) {
      user.addRole(roleDao.getRoleByName(Constants.USER_ROLE));
    }
    return createAndSaveUserIfNotExists(user);
  }

  /**
   * Creates and saves a new user with no permissions. In most situations, unless you're testing
   * permissions, you should use createAndSaveUserIfNotExists() instead.
   *
   * @return the persisted user
   */
  protected User createAndSaveUserWithNoPermissions(String uname) {
    User user = TestFactory.createAnyUser(uname);
    return createAndSaveUserIfNotExists(user);
  }

  protected User createAndSaveUserIfNotExists(User user) {
    User dbUser;
    boolean exists = userDao.userExists(user.getUsername());
    if (exists) {
      dbUser = userMgr.getUserByUsername(user.getUsername());
      ;
    } else {
      dbUser = userMgr.save(user);
    }
    return dbUser;
  }

  /**
   * Sets up default folder system for the user without adding in example records and content.
   *
   * @param users ...
   * @throws IllegalAddChildOperation
   */
  protected List<Folder> initialiseContentWithEmptyContent(User... users)
      throws IllegalAddChildOperation {
    List<Folder> rootFolders = new ArrayList<>();
    contentInitializer.setCustomInitActive(false);
    for (User user : users) {
      rootFolders.add(doInit(user));
    }
    return rootFolders;
  }

  protected Folder createFolder(String flderName, Folder parent, User user2) {
    return folderMgr.createNewFolder(parent.getId(), flderName, user2);
  }

  /**
   * Sets up default folder system for the user with example content.
   *
   * @param user
   * @return root folder of the user
   * @throws IllegalAddChildOperation
   */
  protected Folder initialiseContentWithExampleContent(User user) throws IllegalAddChildOperation {
    contentInitializer.setCustomInitActive(true);
    return doInit(user);
  }

  private Folder doInit(User user) throws IllegalAddChildOperation {
    RSpaceTestUtils.login(user.getUsername(), TESTPASSWD);
    // replace application init for tests
    return contentInitializer.init(user.getId()).getUserRoot();
  }

  /**
   * DAOs that use native MySQL need to be flushed so that subsequent reads will detect changes (but
   * they're still rolled back at the end of the test method ).
   */
  protected void flushDatabaseState() {
    sessionFactory.getCurrentSession().flush();
  }

  /**
   * ADds n folders and m records to the specified folder. Folder names are an index suffixed by
   * 'f'. Record names are index suffixed by 'sd'
   *
   * @param root The folder to which records/folders are added
   * @param numFolders The number of folders to add
   * @param numRecords The number of records to add
   * @param user The current subject
   * @param anyForm The {@link RSForm} from which records will be created.
   * @throws Exception
   * @throws InterruptedException
   */
  protected void addNFoldersAndMRecords(
      Folder root, int numFolders, int numRecords, User user, RSForm anyForm)
      throws Exception, InterruptedException {
    for (int i = 0; i < numFolders; i++) {
      Folder newFolder = folderMgr.createNewFolder(root.getId(), "", user);
      newFolder.setName(i + "" + "f");
      folderDao.save(newFolder);
      Thread.sleep(1);
    }
    // add m records
    for (int i = 0; i < numRecords; i++) {
      StructuredDocument sd =
          recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);
      sd.setName(i + "sd");
      recordDao.save(sd);
      Thread.sleep(1);
    }
    flushDatabaseState();
  }

  /**
   * Creates a comment with a single comment item with the specified text, persisting it.
   *
   * @param f
   * @param commentText
   * @return The created EcatComment.
   */
  protected EcatComment createCommentItemForField(Field f, String commentText, User creator) {
    EcatComment comment = new EcatComment();
    comment.setParentId(f.getId());
    comment.setRecord(f.getStructuredDocument());
    commentsDao.addComment(comment);

    EcatCommentItem item = new EcatCommentItem();
    item.setItemContent(commentText);
    item.setLastUpdater(creator.getUsername());
    commentsDao.addCommentItem(comment, item);
    return comment;
  }

  /**
   * Convenience method to count the numbr of child records in the root folder.
   *
   * @param user
   * @return
   */
  protected int getNumChldrenInRootFolder(User user) {
    return folderDao.getRootRecordForUser(user).getChildren().size();
  }

  protected int getTotalNumGroups() {
    return grpdao.getAll().size();
  }

  /**
   * gets total folder count for a given user includ
   *
   * @param anyUser
   * @return
   */
  protected Long getFolderCount(User anyUser) {
    return (Long)
        sessionFactory
            .getCurrentSession()
            .createQuery("select count(*) from Folder f where f.owner = :owner")
            .setParameter("owner", anyUser)
            .uniqueResult();
  }

  /**
   * gets total notebook count for a given user including deleted
   *
   * @param anyUser
   * @return
   */
  protected Long getNotebookCount(User anyUser) {
    return (Long)
        sessionFactory
            .getCurrentSession()
            .createQuery(
                "select count(*) from Folder f where f.owner = :owner and f.type like '%NOTEBOOK%'")
            .setParameter("owner", anyUser)
            .uniqueResult();
  }

  /**
   * gets total media count for a given user including deleted
   *
   * @param anyUser
   * @return
   */
  protected Long getMediaCount(User anyUser) {
    return (Long)
        sessionFactory
            .getCurrentSession()
            .createQuery("select count(*) from EcatMediaFile emf where emf.owner = :owner")
            .setParameter("owner", anyUser)
            .uniqueResult();
  }

  protected int getTotalNumUsers() {
    return userDao.getAll().size();
  }

  /**
   * Creates and persists a group with the given display name and owner
   *
   * @param groupName
   * @param groupPi the owner of this group
   * @return the created persistent group.
   */
  protected Group createGroup(String groupName, User groupPi) {
    Group group = new Group(groupName, groupPi);
    group.setDisplayName(groupName);
    if (!groupPi.hasRole(Role.PI_ROLE)) {
      throw new IllegalArgumentException("Owner is not a PI!!");
    }
    PermissionFactory permFctry = new DefaultPermissionFactory();
    for (ConstraintBasedPermission cbp : permFctry.createDefaultGlobalGroupPermissions(group)) {
      group.addPermission(cbp);
    }

    group = grpdao.save(group);
    if (group.getCommunityId() != null) {
      Community comm = communityMgr.get(group.getCommunityId());
      comm.addLabGroup(group);
      communityMgr.save(comm);
    }
    return group;
  }

  protected Group reloadGroup(Group group) {
    group = grpdao.get(group.getId());
    return group;
  }

  /**
   * Creates and saves a new admin user with a random name prefixed by 'admin'
   *
   * @return the created admin user
   */
  protected User createAndSaveAdminUser() {
    return createAndSaveUserIfNotExists(getRandomAlphabeticString("admin"), Constants.ADMIN_ROLE);
  }

  /**
   * Creates and saves a new sysadmin user with a random name prefixed by 'sysadmin'
   *
   * @return the created sysadmin user
   */
  protected User createAndSaveSysadminUser() {
    return createAndSaveUserIfNotExists(
        getRandomAlphabeticString("sysadmin"), Constants.SYSADMIN_ROLE);
  }

  /**
   * Creates and persists a pi with a random username
   *
   * @return the saved user
   */
  protected User createAndSaveAPi() {
    return createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
  }

  /**
   * Creates and persists a user with a random username
   *
   * @return the saved user
   */
  protected User createAndSaveRandomUser() {
    return createAndSaveUserIfNotExists(getRandomAlphabeticString("user"));
  }

  /** Triggers indexing of content for search. */
  protected void flushToSearchIndices() {
    flushDatabaseState();
    Search.getFullTextSession(sessionFactory.getCurrentSession()).flushToIndexes();
  }

  protected void assertColumnIndicesAreTheSameForFieldsAndFormss(StructuredDocument child) {
    for (Field f : child.getFields()) {
      assertEquals(f.getColumnIndex(), f.getFieldForm().getColumnIndex());
    }
  }

  protected EcatImageAnnotation addImageAnnotationToField(
      StructuredDocument parent, Field txtFld, User user) throws IOException {
    EcatImageAnnotation ann = new EcatImageAnnotation();
    ann.setParentId(txtFld.getId());
    ann.setRecord(txtFld.getStructuredDocument());
    ann.setImageId(addImageToGallery(user).getId());
    imageAnnotationDao.save(ann);

    String imgContent = richTextUpdater.generateAnnotatedImageElement(ann, "" + txtFld.getId());
    txtFld.setFieldData(txtFld.getFieldData() + " and image " + imgContent);
    recordDao.save(parent); // with updated field
    return ann;
  }

  protected void addInternalLinkToField(Field txtFld, BaseRecord targetRecord) {
    String internalLink = richTextUpdater.generateURLStringForInternalLink(targetRecord);
    txtFld.setFieldData(txtFld.getFieldData() + " and link " + internalLink);
    recordDao.save(txtFld.getStructuredDocument()); // with updated field
    internalLinkDao.saveInternalLink(txtFld.getStructuredDocument().getId(), targetRecord.getId());
  }

  protected User doCreateAndInitUser(String username, String role) {
    User u = createAndSaveUserIfNotExists(username, role);
    initialiseContentWithEmptyContent(u);
    return u;
  }

  protected User doCreateAndInitUser(String username) {
    return doCreateAndInitUser(username, Constants.USER_ROLE);
  }

  protected UserConnection createAndSaveEgnyteUserConnectionWithAccessToken(
      User anyUser, String accessToken) {
    UserConnection connection = TestFactory.createUserConnection(anyUser.getUsername());
    connection.getId().setProviderId(ExternalFileStoreProvider.EGNYTE.name());
    connection.setAccessToken(accessToken);
    return connMgr.save(connection);
  }
}
