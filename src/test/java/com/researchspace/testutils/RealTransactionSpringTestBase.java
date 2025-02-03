package com.researchspace.testutils;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.model.preference.Preference.NOTIFICATION_DOCUMENT_DELETED_PREF;
import static com.researchspace.model.preference.Preference.NOTIFICATION_DOCUMENT_SHARED_PREF;
import static com.researchspace.model.preference.Preference.NOTIFICATION_DOCUMENT_UNSHARED_PREF;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.axiope.search.SearchManager;
import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ExportScope;
import com.researchspace.archive.model.ArchiveExportConfig;
import com.researchspace.core.testutil.Invokable;
import com.researchspace.core.testutil.InvokableWithResult;
import com.researchspace.core.util.CryptoUtils;
import com.researchspace.dao.AuditDaoIT;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.Community;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.Group;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.comms.Communication;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.comms.ShareRecordMessageOrRequestCreationConfiguration;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.EcatImageAnnotationManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.RecordSigningManager;
import com.researchspace.service.RoleManager;
import com.researchspace.service.SharingHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.Value;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.hibernate.criterion.Projections;
import org.junit.AfterClass;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.jdbc.SqlScriptsTestExecutionListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;

/**
 * Abstract base class with injected services and utility methods for tests which need transactions
 * to be committed.<br>
 * (E.g., tests that require cache refreshes, or post-commit listeners, such as auditing, or where
 * we want to test what fields are initialized from a service call).
 */
@TestExecutionListeners(value = {SqlScriptsTestExecutionListener.class})
@Configuration()
@Profile("dev")
public class RealTransactionSpringTestBase extends BaseManagerTestCaseBase {

  /*
   * Creates a JdbCTemplate for use in some convenient JdbcTestUtil methods
   */
  @Bean
  JdbcTemplate JdbcTemplate() {
    return new JdbcTemplate(dataSource);
  }

  protected @Autowired DataSource dataSource;

  protected @Autowired IContentInitializer contentInitializer;
  protected @Autowired EcatImageAnnotationManager imgAnnotationMgr;
  protected @Autowired FieldManager fieldMgr;

  protected @Autowired RoleManager roleMgr;
  protected @Autowired RecordDeletionManager recordDeletionMgr;
  protected @Autowired RecordSigningManager signingManager;
  protected @Autowired SearchManager searchMgr;
  @Autowired protected SharingHandler sharingHandler;

  /* hand-wired fields below */

  protected Folder root;

  protected User piUser;

  protected Model model;

  protected ExtendedModelMap modelTss;

  /** Represents the 'piUser' user. */
  protected Principal mockPrincipal = () -> piUser.getUsername();

  protected TransactionStatus status;

  @Before
  public void beforeEach() {
    sampleDao.resetDefaultTemplateOwner();
  }

  @AfterClass
  public static void after() {
    DatabaseCleaner.cleanUp();
  }

  /**
   * Sets up a user with PI Roles
   *
   * @throws Exception
   */
  public void setUp() throws Exception {
    super.setUp();
    root = null;
    piUser = createAndSaveUser(getRandomName(10), Constants.PI_ROLE);
    modelTss = new ExtendedModelMap();
    model = modelTss;
  }

  /**
   * Encapsulates and simplifies complex content creation in a document. <br>
   * Each method will update database and field content.
   */
  @Value
  public class ContentBuilder {
    private User owner;
    private Field field;

    /** A list of added content in the order that items were added. */
    private List<IFieldLinkableElement> content = new ArrayList<>();

    public ContentBuilder addImageAnnotation() throws IOException {
      content.add(addImageAnnotationToField(field, owner));
      return this;
    }

    public ContentBuilder addImage() throws IOException {
      content.add(addImageToField(field, owner));
      return this;
    }

    public ContentBuilder addMath() throws IOException {
      content.add(addMathToField(field, owner));
      return this;
    }
  }

  /**
   * Gets the Spring-managed transaction manager
   *
   * @return
   */
  protected PlatformTransactionManager getTxMger() {
    return applicationContext.getBean(PlatformTransactionManager.class);
  }

  /** Commits a transaction */
  protected void commitTransaction() {
    getTxMger().commit(status);
  }

  /**
   * Creates a group for provided users, with default piUser being a PI
   *
   * @param users
   * @return
   * @throws IllegalAddChildOperation
   */
  protected Group createGroupForUsersWithDefaultPi(User... users) throws IllegalAddChildOperation {
    return createGroupForPiAndUsers(null, users);
  }

  /**
   * @throws IllegalAddChildOperation @Param piUserForGroup if null then existing piUser will be
   *     used as PI for the Group Creates and persists a group for the specified users with the
   *     already existing 'piUser' as pi unless a piUser is supplied. Runs in its own transaction.
   */
  protected Group createGroupForPiAndUsers(User piUserForGroup, User[] users)
      throws IllegalAddChildOperation {

    openTransaction();
    User pi = piUserForGroup == null ? piUser : piUserForGroup;
    Group grp = new Group(getRandomName(10), pi);
    grp.setDisplayName(grp.getUniqueName());
    for (ConstraintBasedPermission cbp : perFactory.createDefaultGlobalGroupPermissions(grp)) {
      grp.addPermission(cbp);
    }
    permissionUtils.refreshCache();
    grp = grpMgr.saveGroup(grp, pi);
    grpMgr.addMembersToGroup(grp.getId(), Arrays.asList(users), pi.getUsername(), null, pi);
    grpMgr.createSharedCommunalGroupFolders(grp.getId(), pi.getUsername());
    commitTransaction();
    return grp;
  }

  public Group addUserToGroup(User toAdd, Group grp, RoleInGroup role, User grpPi)
      throws IllegalAddChildOperation {

    openTransaction();
    grp = grpMgr.saveGroup(grp, grpPi);
    permissionUtils.refreshCache();
    grpMgr.addMembersToGroup(grp.getId(), Arrays.asList(toAdd), grpPi.getUsername(), null, grpPi);
    commitTransaction();
    return grp;
  }

  /**
   * Gets the first StructuredDocument from the specified folder,
   *
   * @param root
   * @param user
   * @return A {@link StructuredDocument} or <code>null</code> if there is no document in the
   *     specified folder.
   * @throws Exception
   */
  protected StructuredDocument getADocumentFromFolder(Folder root, User user) {
    for (BaseRecord br : root.getChildrens()) {
      if (br.isStructuredDocument()) {
        return recordMgr.getRecordWithFields(br.getId(), user).asStrucDoc();
      }
    }
    return null;
  }

  /**
   * Creates content for a new user. This must be a newly created user with no root folder yet.
   *
   * @returns the User's Root folder.
   */
  protected Folder setUpUserWithInitialisedContent(User toInit) throws Exception {
    logoutAndLoginAs(toInit);
    contentInitializer.setCustomInitActive(true);
    return contentInitializer.init(toInit.getId()).getUserRoot();
  }

  /**
   * logs in and Creates folder structure for a new user, including some example records. This must
   * be a newly created user with no root folder yet.
   *
   * @returns the User's Root folder.
   */
  protected Folder setUpUserWithoutCustomContent(User toInit) throws Exception {
    logoutAndLoginAs(toInit);
    contentInitializer.setCustomInitActive(false);
    Folder root = contentInitializer.init(toInit.getId()).getUserRoot();
    contentInitializer.setCustomInitActive(true);
    return root;
  }

  /**
   * Logs in as 'user', creates root folder etc and a basic form and document with a single text
   * field.<br>
   * The created form is world-readable.
   */
  protected StructuredDocument setUpLoginAsPIUserAndCreateADocument() {
    logoutAndLoginAs(piUser);
    root = initUser(piUser);
    RSForm form = createAnyForm(piUser);
    StructuredDocument doc = createDocumentInFolder(root, form, piUser);
    return doc;
  }

  protected StructuredDocument setUpLoginAsUserWithRoleAndCreateADocument(
      String role, String name) {
    User created = createAndSaveUser(name, role);
    logoutAndLoginAs(created);
    root = initUser(created);
    RSForm form = createAnyForm(created);
    StructuredDocument doc = createDocumentInFolder(root, form, created);
    return doc;
  }

  /**
   * Initialises a new user's default folder set up but does not create content ( example records
   * etc)
   *
   * @param user A previously created user
   * @return
   * @throws IllegalAddChildOperation
   */
  protected Folder initUser(User user) throws IllegalAddChildOperation {
    return initUser(user, false);
  }

  /**
   * initialises a user's folder tree; optionally can create example records.
   *
   * @param user
   * @param createContent whether to create records for user or not.
   * @return
   * @throws IllegalAddChildOperation
   */
  protected Folder initUser(User user, boolean createContent) throws IllegalAddChildOperation {
    contentInitializer.setCustomInitActive(createContent);
    Folder rc = contentInitializer.init(user.getId()).getUserRoot();
    contentInitializer.setCustomInitActive(true);
    user.setRootFolder(rc);
    return rc;
  }

  /**
   * initialised >=1 user with empty root folder. Identical to initUsers(false, ...users)
   *
   * @param users
   * @throws IllegalAddChildOperation
   */
  protected void initUsers(User... users) throws IllegalAddChildOperation {
    for (User u : users) {
      initUser(u);
    }
  }

  protected void initUsers(boolean createContent, User... users) throws IllegalAddChildOperation {
    for (User user : users) {
      initUser(user, createContent);
    }
  }

  /**
   * Creates link to another record, adds it to field data, then saves the document the field
   * belongs to.
   *
   * @param field
   * @param toLinkTo a persisted record to link to
   * @throws Exception
   */
  protected void addLinkToOtherRecord(Field field, BaseRecord toLinkTo) {
    addLinkToOtherRecord(field, toLinkTo, false);
  }

  /**
   * Creates link to another record, adds it to field data, then saves the document the field
   * belongs to. If 'toLinkTo' points to structured document, and 'versioned' is true, will create a
   * versioned link.
   *
   * @param field
   * @param toLinkTo a persisted record to link to
   * @param versioned whether link should point to current version of toLinkTo document
   * @throws Exception
   */
  protected void addLinkToOtherRecord(Field field, BaseRecord toLinkTo, boolean versioned) {
    String linkStr =
        versioned
            ? richTextUpdater.generateURLStringForVersionedInternalLink(toLinkTo.asStrucDoc())
            : richTextUpdater.generateURLStringForInternalLink(toLinkTo);
    String docData = field.getFieldData();
    docData += linkStr;
    field.setFieldData(docData);
    field.getStructuredDocument().notifyDelta(DeltaType.FIELD_CHG);
    recordMgr.save(field.getStructuredDocument(), piUser);
  }

  /**
   * Adds a new comment to a a text field
   *
   * @param commentMsg the comment
   * @return The EcatComment created. The text field is populated with the comment image
   * @throws Exception
   */
  protected EcatComment addNewCommentToField(String commentMsg, Field textField, User user)
      throws Exception {

    Long fieldId = textField.getId();
    EcatComment ecatComment = mediaMgr.insertEcatComment(fieldId + "", commentMsg, user);
    // ecatComment.addCommentItem(item);
    String comment = richTextUpdater.generateURLStringForCommentLink(ecatComment.getComId() + "");
    String newData = textField.getFieldData() + comment;
    textField.setFieldData(newData);
    textField.getStructuredDocument().notifyDelta(DeltaType.COMMENT);
    recordMgr.save(textField.getStructuredDocument(), user);

    return ecatComment;
  }

  protected EcatComment addNewCommentItemToExistingComment(
      String msg, Long commentId, Field parent, User user) throws Exception {
    return mediaMgr.addEcatComment(parent.getId() + "", commentId + "", msg, user);
  }

  protected String addNonRSpaceImageToField(User user, Field field) {
    String fieldData =
        field.getFieldData() + String.format("<img src=\"%s\">", "TestResources/Picture2.png");
    field.setFieldData(fieldData);
    field.getStructuredDocument().notifyDelta(DeltaType.FIELD_CHG);
    recordMgr.save(field.getStructuredDocument(), user);
    return fieldData;
  }

  protected EcatChemistryFile updateChemistryFileInGallery(Long chemFileId, User user)
      throws IOException {
    InputStream chem2InputStream =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Aminoglutethimide.mol");
    EcatMediaFile updatedImg =
        mediaMgr.updateMediaFile(
            chemFileId, chem2InputStream, "Aminoglutethimide2.mol", user, null);
    return (EcatChemistryFile) updatedImg;
  }

  protected RSChemElement updateExistingChemElement(Long chemElementId, Field field, User user)
      throws Exception {

    RSChemElement chemElement = rsChemElementManager.get(chemElementId, user);

    InputStream molInput = getClass().getResourceAsStream("/TestResources/Aminoglutethimide.mol");
    // System.out.println("molInput null: " + (molInput == null));
    String newChemElementMolString = IOUtils.toString(molInput, "UTF-8");
    molInput.close();

    chemElement.setChemElements(newChemElementMolString);
    chemElement.setDataImage("testData".getBytes());
    rsChemElementManager.save(chemElement, user);

    return chemElement;
  }

  /**
   * Adds a new image annotation to a a text field
   *
   * @param newannnotation
   * @param imageAnnotationId
   * @return The EcatImageAnnotation created. The text field is populated with the
   *     EcatImageAnnotation link
   * @throws Exception
   */
  protected EcatImageAnnotation updateExistingImageAnnotation(
      Long imageAnnotationId, Field field, User user, String newannnotation) throws Exception {

    String base64Image = getBase64Image();
    EcatImageAnnotation ann = imgAnnotationMgr.get(imageAnnotationId, user);
    return mediaMgr.saveImageAnnotation(
        newannnotation,
        base64Image,
        field.getId(),
        field.getStructuredDocument(),
        ann.getImageId(),
        user);
  }

  protected void createRootFolderForUsers(User... users) {
    for (User user : users) {
      Folder root = recordFactory.createRootFolder("root", user);
      user.setRootFolder(root);
      openTransaction();
      folderDao.save(root);
      commitTransaction();
    }
  }

  // creates and saves a new SD. Must be called form within transaction
  protected StructuredDocument createDocumentInFolder(Folder folder, RSForm form, User user) {
    StructuredDocument doc =
        recordMgr.createNewStructuredDocument(folder.getId(), form.getId(), user);
    return doc;
  }

  /**
   * creates and saves a new SD in home folder
   *
   * @param form
   * @param user
   * @return the {@link StructuredDocument}
   */
  protected StructuredDocument createDocumentInRootFolder(RSForm form, User user) {
    Folder folder = folderMgr.getRootFolderForUser(user);
    return createDocumentInFolder(folder, form, user);
  }

  /**
   * Creates and saves a new subfolder of the supplied parent folder
   *
   * @param parent
   * @param foldername
   * @param u
   * @return the newly created subfolder
   */
  protected Folder createSubFolder(Folder parent, String foldername, User u) {
    return folderMgr.createNewFolder(parent.getId(), foldername, u);
  }

  /**
   * Opens a new transaction. This is only needed if you're calling a DAO method from a test case,
   * rather than a service method.
   */
  protected TransactionStatus openTransaction() {
    // for some reason it's necessary to go through AuditDaoIT (or maybe any IT class)
    status = AuditDaoIT.openTransaction(getTxMger());
    return status;
  }

  /**
   * Creates a new user with given name and 'User' role
   *
   * @param name
   * @return
   */
  protected User createAndSaveUser(String name) {
    return createAndSaveUser(name, Constants.USER_ROLE);
  }

  /** Creates a new user with given name, plaintext password and role name */
  protected User createAndSaveUser(String name, String roleName, String password) {
    if (!Role.isRoleStringIdentifiable(roleName)) {
      throw new IllegalArgumentException("Invalid role name : " + roleName);
    }

    User user = TestFactory.createAnyUser(name);

    String hashedPassword = CryptoUtils.hashWithSha256inHex(password);

    user.setPassword(hashedPassword);
    user.setConfirmPassword(hashedPassword);
    user.addRole(roleMgr.getRole(roleName));

    if (roleName.equals(Constants.PI_ROLE)) {
      user.addRole(roleMgr.getRole(Constants.USER_ROLE));
    }

    user = userMgr.save(user);
    permissionUtils.refreshCache();

    return user;
  }

  /**
   * Creates a new user with given name and will be assigned the given role
   *
   * @param name
   * @param rolename valid rolename, see {@link Role}.
   * @return
   */
  protected synchronized User createAndSaveUser(String name, String rolename) {
    if (!Role.isRoleStringIdentifiable(rolename)) {
      throw new IllegalArgumentException("Invalid role name : " + rolename);
    }

    User user = TestFactory.createAnyUser(name);
    user.setEmail(name + "@test.com");

    user.addRole(roleMgr.getRole(rolename));
    if (rolename.equals(Constants.PI_ROLE)) {
      user.addRole(roleMgr.getRole(Constants.USER_ROLE));
    }

    user = userMgr.save(user);
    permissionUtils.refreshCache();

    return user;
  }

  /**
   * Creates, inits and logs in a new user with 'User' role and a random username
   *
   * @return the user
   */
  protected User createInitAndLoginAnyUser() {
    User user = createAndSaveUser(getRandomName(10));
    initUser(user);
    logoutAndLoginAs(user);
    return user;
  }

  /**
   * Convenience method to create many documents at a time in a single folder. Creates <code>
   * numDocuments</code> structured documents created from the the speocified Form in the given
   * folder.
   *
   * @param numDocuments
   * @param user
   * @param flder
   * @param form
   * @throws Exception
   */
  protected void createNDocumentsInFolderForUserFor(
      int numDocuments, User user, Folder flder, RSForm form) throws Exception {
    for (int i = 0; i < numDocuments; i++) {
      recordMgr.createNewStructuredDocument(flder.getId(), form.getId(), user);
    }
  }

  /** Simple class to hold entities created in setUpSharedDocument() method for use in tests */
  public class GroupSetUp {
    public StructuredDocument structuredDocument;
    public User user;
    public Group group;
    public Notebook notebook;
  }

  /**
   * Sets up 'user' as owner of a document,shared with 'other' user with edit permission.
   *
   * @throws Exception
   */
  protected GroupSetUp setUpDocumentGroupForPIUserAndShareRecord() throws Exception {
    return setUpDocumentGroupForPIUserAndShareRecord(true);
  }

  /**
   * GEts a FileInput stream to a file resource located in src/test/resources/TestResources/
   *
   * @param fileName
   * @return
   * @throws FileNotFoundException
   */
  protected FileInputStream getTestResourceFileStream(String fileName)
      throws FileNotFoundException {
    return new FileInputStream(new File("src/test/resources/TestResources/" + fileName));
  }

  /**
   * GEts a File object a file resource located in src/test/resources/TestResources/
   *
   * @param fileName
   * @return
   * @throws FileNotFoundException
   */
  protected File getTestResourceFile(String fileName) throws FileNotFoundException {
    return new File("src/test/resources/TestResources/" + fileName);
  }

  /**
   * If share is false, will create group but not share the document. Creates a group with 'piUser'
   * == PI and 'otherPi' also is a global PI
   *
   * @param share
   * @return
   * @throws Exception
   */
  protected GroupSetUp setUpDocumentGroupForPIUserAndShareRecord(boolean share) throws Exception {
    GroupSetUp setup = new GroupSetUp();
    setup.structuredDocument = setUpLoginAsPIUserAndCreateADocument();
    setup.user = createAndSaveUser("pi" + getRandomName(10), Constants.PI_ROLE);
    initUser(setup.user);

    setup.group = createGroupForUsersWithDefaultPi(setup.user, piUser);

    permissionUtils.refreshCache();
    if (share) {
      ShareConfigElement gsCommand = new ShareConfigElement(setup.group.getId(), "edit");
      sharingMgr.shareRecord(
          piUser, setup.structuredDocument.getId(), new ShareConfigElement[] {gsCommand});
    }
    return setup;
  }

  protected GroupSetUp setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
      String name, String role) throws Exception {
    return setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(name, role, false);
  }

  /**
   * Sets up a group with a user having role 'role', creates and then publishes a document
   *
   * @param role The role of the user who will publish the document
   * @throws Exception
   */
  protected GroupSetUp setUpDocumentGroupAndPublishRecordWithPublisherHavingRole(
      String name, String role, boolean publishNotebook) throws Exception {
    GroupSetUp setup =
        setUpGroupCreateDocumentAndUserWithUserHavingRole(name, role, publishNotebook, 1);
    publishDocumentForUser(
        setup.user,
        setup.structuredDocument != null
            ? setup.structuredDocument.getId()
            : setup.notebook.getId());
    return setup;
  }

  protected GroupSetUp setUpGroupCreateDocumentAndUserWithUserHavingRole(
      String name, String role, boolean docIsNotebook, int notebookEntries) throws Exception {
    GroupSetUp setup = new GroupSetUp();
    User created = createAndSaveUser(name + getRandomName(10), role);
    logoutAndLoginAs(created);
    root = initUser(created);
    RSForm form = createAnyForm(created);
    if (!docIsNotebook) {
      StructuredDocument doc = createDocumentInFolder(root, form, created);
      setup.structuredDocument = doc;
    } else {
      Notebook testNotebook =
          createNotebookWithNEntriesAndDelayBetweenEntries(
              root.getId(), "aNotebook_" + name, notebookEntries, created, 100);
      setup.notebook = testNotebook;
    }
    if (role.equals(Constants.PI_ROLE)) {
      setup.group = createGroupForPiAndUsers(created, new User[] {created});
    } else {
      setup.group = createGroupForUsersWithDefaultPi(created);
    }
    setup.user = created;
    permissionUtils.refreshCache();
    return setup;
  }

  /**
   * Sets up a group with a user having role 'role', creates and then publishes a notebook with 2
   * entries
   *
   * @param role The role of the user who will publish the notebook
   * @throws Exception
   */
  protected GroupSetUp setUpNotebookWith2EntriesAndGroupAndPublishRecordWithPublisherHavingRole(
      String name, String role) throws Exception {
    GroupSetUp setup = setUpGroupCreateDocumentAndUserWithUserHavingRole(name, role, true, 2);
    publishDocumentForUser(setup.user, setup.notebook.getId());
    return setup;
  }

  public void publishDocumentForUser(User publisher, Long docIDToPublish) {
    publishDocumentForUser(publisher, docIDToPublish, false, false, "");
  }

  public void publishDocumentForUser(
      User publisher,
      Long docIDToPublish,
      boolean publishOnInternet,
      boolean displayContactDetails,
      String publicationSummary) {
    User anonymous = userMgr.getUserByUsername(RecordGroupSharing.ANONYMOUS_USER);
    ShareConfigElement anonymousShare = new ShareConfigElement();
    anonymousShare.setUserId(anonymous.getId());
    anonymousShare.setOperation("read");
    anonymousShare.setPublishOnInternet(publishOnInternet);
    anonymousShare.setDisplayContactDetails(displayContactDetails);
    anonymousShare.setPublicationSummary(publicationSummary);
    logoutAndLoginAs(publisher);
    sharingMgr.shareRecord(publisher, docIDToPublish, new ShareConfigElement[] {anonymousShare});
  }

  public void unPublishDocumentForUser(User publisher, Long recordGroupShareId) {
    sharingHandler.unshare(recordGroupShareId, publisher);
  }

  protected PaginationCriteria<AuditedRecord> createDefaultAuditedRecordListPagCrit() {
    return PaginationCriteria.createDefaultForClass(AuditedRecord.class);
  }

  /**
   * Renames a document n times with a random name - used to test revision history
   *
   * @param doc
   * @param n
   * @return
   * @throws Exception
   */
  protected StructuredDocument renameDocumentNTimes(final StructuredDocument doc, int n)
      throws Exception {
    for (int i = 0; i < n; i++) {
      doInTransaction(
          () -> {
            StructuredDocument doc2 = (StructuredDocument) recordMgr.get(doc.getId());
            doc2.setName(getRandomName(10));
          });
    }
    return doc;
  }

  public void tearDown() throws Exception {
    // rollback if txn still open; e.g., in event of exception
    if (status != null && !status.isCompleted()) {
      getTxMger().rollback(status);
    }
  }

  /**
   * Gets count of all template records in the system
   *
   * @return
   * @throws Exception
   * @throws
   */
  protected Long totalTemplateCount() throws Exception {
    return doInTransaction(
        () -> {
          Long count =
              (Long)
                  sessionFactory
                      .getCurrentSession()
                      .createQuery(
                          "select count (id) from StructuredDocument where type like '%TEMPLATE%'")
                      .uniqueResult();
          return count;
        });
  }

  /**
   * Gets count of all child Records/Folders of the specified folder
   *
   * @return
   */
  protected Long totalAllChildrenInFolder(long folderId) {
    openTransaction();
    Long count =
        (Long)
            sessionFactory
                .getCurrentSession()
                .createQuery("select count (id) from RecordToFolder rtf where folder.id=:id")
                .setParameter("id", folderId)
                .uniqueResult();
    commitTransaction();
    return count;
  }

  /**
   * Creates a complex document in the specified folder
   *
   * @param user
   * @param parentFolder
   * @return
   * @throws Exception
   */
  protected StructuredDocument createComplexDocumentInFolder(User user, Folder parentFolder)
      throws Exception {
    StructuredDocument doc = createBasicDocumentInFolder(user, parentFolder, "");
    return addComplexContent(user, doc);
  }

  /**
   * Creates a structured document with comments, image annotation, chem element, math element,
   * image, attachment, sketch, audio file, a static image, list of materials, and linked record in
   * field text in the user's root folder.
   *
   * @param user
   * @return the document with fields loaded and initialised
   * @throws Exception
   */
  protected StructuredDocument createComplexDocument(User user) throws Exception {
    logoutAndLoginAs(user);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "toLinkFrom");
    return addComplexContent(user, doc);
  }

  /**
   * Convenience method to get root folder for a user
   *
   * @param user
   * @return
   */
  protected Folder getRootFolderForUser(User user) {
    return folderMgr.getRootFolderForUser(user);
  }

  private StructuredDocument addComplexContent(User user, StructuredDocument doc) throws Exception {
    Folder userRootFolder = getRootFolderForUser(user);
    Field toAddTo = doc.getFields().get(0);
    // add comments
    EcatComment comment = addNewCommentToField("comment1", toAddTo, user);
    EcatImageAnnotation sketch = addSketchToField(toAddTo, user);
    EcatImageAnnotation annotation = addImageAnnotationToField(toAddTo, user);
    // and a raw image
    EcatImage image = addImageToField(toAddTo, user);
    // add an edited image
    EcatImage editedImage = editImageInGallery(image.getId(), user);
    addImageToField(toAddTo, user, editedImage);

    // chemelements
    RSChemElement chemElement = addChemStructureToField(toAddTo, user);
    // add an attachment
    File afile = RSpaceTestUtils.getResource("testTxt.txt");
    addAttachmentDocumentToField(afile, toAddTo, user);
    // add a math:
    RSMath math = addMathToField(toAddTo, user);
    addAudioFileToField(toAddTo, user);
    addNonRSpaceImageToField(user, toAddTo);

    // create two lists of materials: one with a sample, another with subsample with quantity
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    ApiSubSample subSample = sample.getSubSamples().get(0);
    createBasicListOfMaterialsForUserAndDocField(
        user, toAddTo, List.of(new ApiMaterialUsage(subSample, subSample.getQuantity())));
    createBasicListOfMaterialsForUserAndDocField(
        user, toAddTo, List.of(new ApiMaterialUsage(sample, null)));

    // create other doc and empty folder/notebook, and link to them
    StructuredDocument toLinkTo = createBasicDocumentInRootFolderWithText(user, "toLinkTo");
    Notebook emptyNbook =
        createNotebookWithNEntries(userRootFolder.getId(), "notebookToLinkTo", 0, user);
    Folder folder = createSubFolder(userRootFolder, "folderToLinkTo", user);
    addLinkToOtherRecord(toAddTo, toLinkTo);
    addLinkToOtherRecord(toAddTo, emptyNbook);
    addLinkToOtherRecord(toAddTo, folder);

    return recordMgr.getRecordWithFields(doc.getId(), user).asStrucDoc();
  }

  /** Overrides superclass method to wrap in transaction */
  protected StructuredDocument createBasicDocumentInRootFolderWithText(
      User user, String fieldText) {
    openTransaction();
    StructuredDocument rc = super.createBasicDocumentInRootFolderWithText(user, fieldText);
    commitTransaction();
    return rc;
  }

  /**
   * Default is XML, selection, no revisions
   *
   * @param user
   * @param topLEvelFolder
   * @return
   * @throws IOException
   */
  protected ArchiveExportConfig createDefaultArchiveConfig(User user, File topLEvelFolder)
      throws IOException {
    ArchiveExportConfig expCfg = new ArchiveExportConfig();
    expCfg.setExporter(user);
    expCfg.setExportScope(ExportScope.SELECTION);

    expCfg.setTopLevelExportFolder(topLEvelFolder);
    expCfg.setHasAllVersion(false);
    return expCfg;
  }

  protected ArchivalImportConfig createDefaultArchiveImportConfig(User user, File unzipFolder)
      throws IOException {
    ArchivalImportConfig iconfig = new ArchivalImportConfig();

    iconfig.setZipPath(unzipFolder.getCanonicalPath());
    iconfig.setUnzipPath(unzipFolder.getCanonicalPath());
    iconfig.setUser(user.getUsername());
    return iconfig;
  }

  /**
   * Utility method to create a mock multipart file from a regular file
   *
   * @param file
   * @return a {@link MultipartFile}
   * @throws IOException
   */
  protected MockMultipartFile fileToMultipartfile(String paramname, File file) throws IOException {
    return new MockMultipartFile(
        paramname, file.getName(), "unknown", FileUtils.readFileToByteArray(file));
  }

  /**
   * Gets the number of new notifications.
   *
   * @param user
   * @return
   */
  protected int getNewNotificationCount(final User user) {
    return communicationMgr
        .getNewNotificationsForUser(
            user.getUsername(), PaginationCriteria.createDefaultForClass(CommunicationTarget.class))
        .getTotalHits()
        .intValue();
  }

  /**
   * Sends a simple message from sender to one or more other users
   *
   * @param sender
   * @param msg
   * @param recipients
   * @return
   */
  protected Communication sendSimpleMessage(User sender, String msg, User... recipients) {
    openTransaction();
    // create a simple message FROM sender To recipients.
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(MessageType.SIMPLE_MESSAGE);
    config.setOptionalMessage(msg);
    Communication comm =
        reqCreateMgr.createRequest(
            config, sender.getUsername(), createUserSet(recipients), null, null);
    commitTransaction();
    // Commented following assertion because it is pointless.
    // assertEquals(0, getActiveRequestCountForUser(user));
    return comm;
  }

  /**
   * Sends a shared record request to target (user).
   *
   * @param source originator user or sender who send the shared request
   * @param doc structured document to be shared
   * @param permission read or write permission
   * @param target recipient of the request
   * @return a {@link MessageOrRequest}
   * @throws Exception
   */
  protected MessageOrRequest sendSharedRecordRequest(
      User source, StructuredDocument doc, String permission, User target) throws Exception {
    return doInTransaction(
        () -> {
          ShareRecordMessageOrRequestCreationConfiguration cgf =
              new ShareRecordMessageOrRequestCreationConfiguration();
          cgf.setTarget(target);
          cgf.setPermission(permission);
          cgf.setRecordId(doc.getId());
          cgf.setMessageType(MessageType.REQUEST_SHARE_RECORD);
          MessageOrRequest mor =
              reqCreateMgr.createRequest(
                  cgf,
                  source.getUsername(),
                  new HashSet<String>(Arrays.asList(target.getUsername())),
                  null,
                  null);
          return mor;
        });
  }

  protected Set<String> createUserSet(User... users) {
    Set<String> rc = new HashSet<String>();
    for (User u : users) {
      rc.add(u.getUsername());
    }
    return rc;
  }

  protected Set<User> createUsersSet(User... users) {
    Set<User> rc = new HashSet<User>();
    for (User user : users) {
      rc.add(user);
    }
    return rc;
  }

  /**
   * Assertion for the state of the system following the sharing of a notebook or ecord.
   * Specifically:
   *
   * <ul>
   *   <li>Shared doc appears in sharer's 'Shared Documents' listing
   *   <li>Shared doc appears in various shared folders
   *   <li>Users have the correct permission on the document.
   * </ul>
   *
   * @param sharer The User who initiated the sharing
   * @param documentOrNotebook The shared item
   * @param sharedWith A Collection of user or groups that the document was shared with
   * @param permission The expected Permission type ( read /write).
   * @param expectSharing whether sharing was expected to succeed or not
   * @throws Exception
   */
  protected void assertDocumentSharedConsequences(
      User sharer,
      BaseRecord documentOrNotebook,
      Collection<? extends AbstractUserOrGroupImpl> sharedWith,
      PermissionType permission,
      boolean expectSharing)
      throws Exception {
    logoutAndLoginAs(sharer);
    // check that shared doc is in shared records.
    List<RecordGroupSharing> rgs = sharingMgr.getSharedRecordsForUser(sharer);
    List<BaseRecord> sharedRecords = getSharedRecordsFromList(rgs);
    assertTrue(sharedRecords.contains(documentOrNotebook));

    doInTransaction(
        () -> {
          for (AbstractUserOrGroupImpl userOrGroup : sharedWith) {
            if (userOrGroup.isUser()) {
              User individual = userOrGroup.asUser();
              logoutAndLoginAs(individual);
              // sharee has read permission?
              if (expectSharing) {
                assertTrue(permissionUtils.isPermitted(documentOrNotebook, permission, individual));
              } else {
                assertFalse(
                    permissionUtils.isPermitted(documentOrNotebook, permission, individual));
              }
              // individual has shared folder?
              Folder indiSharedItemsFolder =
                  folderDao.getIndividualSharedFolderForUsers(sharer, individual, null);

              if (expectSharing) {
                assertNotNull(indiSharedItemsFolder);
                assertTrue(indiSharedItemsFolder.getChildrens().contains(documentOrNotebook));
              } else {
                assertNull(indiSharedItemsFolder);
              }

            } else if (userOrGroup.isGroup()) {
              Group grp = userOrGroup.asGroup();
              for (User member : grp.getMembers()) {
                logoutAndLoginAs(member);
                if (expectSharing) {
                  assertTrue(permissionUtils.isPermitted(documentOrNotebook, permission, member));
                } else {
                  assertFalse(permissionUtils.isPermitted(documentOrNotebook, permission, member));
                }
              }
              if (grp.isLabGroup()) {
                Folder groupFolder = folderDao.getSharedFolderForGroup(grp);
                assertNotNull(groupFolder);
                assertTrue(groupFolder.getChildrens().contains(documentOrNotebook));
              }
            }
          }
        });
  }

  private List<BaseRecord> getSharedRecordsFromList(List<RecordGroupSharing> rgs) {
    return rgs.stream().map(RecordGroupSharing::getShared).collect(Collectors.toList());
  }

  protected Community addGroupToCommunity(Group grp, Community community, User communityAdmin) {
    return communityMgr.addGroupToCommunity(grp.getId(), community.getId(), communityAdmin);
  }

  protected Folder getGroupSharedFolder(Group labGroup) {
    openTransaction();
    Folder rc = folderDao.getSharedFolderForGroup(labGroup);
    commitTransaction();
    return rc;
  }

  protected Long getTotalGroupCount() throws Exception {
    return doInTransaction(
        () ->
            (Long)
                sessionFactory
                    .getCurrentSession()
                    .createCriteria(Group.class)
                    .setProjection(Projections.rowCount())
                    .uniqueResult());
  }

  protected User doCreateAndInitUser(String username, String role) {
    User u = createAndSaveUser(username, role);
    initUser(u);
    return u;
  }

  protected User doCreateAndInitUser(String username) {
    User u = createAndSaveUser(username);
    initUser(u);
    return u;
  }

  /**
   * Convenience method which opens a transaction, invokes the supplied code, and commits the
   * transaction
   *
   * @param dbcode An {@link Invokable} that requires an open transaction
   * @throws Exception
   */
  protected void doInTransaction(Invokable dbcode) throws Exception {
    openTransaction();
    dbcode.invoke();
    commitTransaction();
  }

  /**
   * Convenience method which opens a transaction, invokes the supplied code, and commits the
   * transaction
   *
   * @param dbcode An {@link Invokable} that requires an open transaction
   * @return the result of the InvokableWithResult operation.
   * @throws Exception
   */
  protected <T> T doInTransaction(InvokableWithResult<T> dbcode) throws Exception {
    openTransaction();
    T rc = dbcode.invokeWithResult();
    commitTransaction();
    return rc;
  }

  /**
   * Gets total of ECatImage items in DB
   *
   * @return
   * @throws Exception
   */
  protected Long getImageCount() throws Exception {
    return getCountOfEntityTable("EcatImage");
  }

  /**
   * Gets total of table rows in DB
   *
   * @return
   * @throws Exception
   */
  protected Long getCountOfEntityTable(String table) throws Exception {
    return doInTransaction(
        () -> {
          return sessionFactory
              .getCurrentSession()
              .createQuery("select count(*) from " + table, Long.class)
              .uniqueResult();
        });
  }

  /**
   * Gets total of EcatImageAnnotation items in DB
   *
   * @return
   * @throws Exception
   */
  protected Long getImageAnnotationCount() throws Exception {
    return getCountOfEntityTable("EcatImageAnnotation");
  }

  /**
   * Wait for timeoutMillis until the supplied function T returns a non-null R. Tests for non-null R
   * every second.
   *
   * @param test
   * @param toTest
   * @param timeoutMillis
   * @return R if not null
   * @throws RuntimeException if timeoutMillis exceeded
   */
  protected <T, R> R waitUntilNotNull(Function<T, R> test, T toTest, Long timeoutMillis) {
    Duration duration = Duration.of(1000, ChronoUnit.MILLIS);
    LocalTime now = LocalTime.now();
    LocalTime timeout = now.plus(timeoutMillis, ChronoUnit.MILLIS);
    R result = null;
    while ((result = test.apply(toTest)) == null) {
      try {
        long durations = duration.get(ChronoUnit.NANOS) / 1_000_000;
        log.debug("Sleeping {} ms ", durations);
        Thread.sleep(durations);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      LocalTime after = LocalTime.now();
      if (after.isAfter(timeout)) {
        log.debug("Timeout reached {} ", after);
        throw new RuntimeException("Timeout reached");
      }
    }
    return result;
  }

  protected <T> boolean waitForConditionTrue(Predicate<T> test, T toTest, long timeoutMillis) {

    LocalTime now = LocalTime.now();
    LocalTime timeout = now.plus(timeoutMillis, ChronoUnit.MILLIS);
    boolean result = false;
    while ((result = test.test(toTest)) == false) {
      try {

        log.info("Predicate failed - Sleeping {} ms ", "1000");
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      LocalTime after = LocalTime.now();
      if (after.isAfter(timeout)) {
        log.warn("Timeout {} reached  at {} ", timeoutMillis, after);
        throw new RuntimeException("Timeout reached");
      }
    }
    return result;
  }

  /**
   * Set up to receive optional notifications for document-sharing/deletion actions
   *
   * @param testGroup
   */
  protected void setUpMessagePreferences(TestGroup testGroup) {
    for (User user : testGroup.getGroup().getMembers()) {
      logoutAndLoginAs(user);
      userMgr.setPreference(
          NOTIFICATION_DOCUMENT_SHARED_PREF, Boolean.TRUE.toString(), user.getUsername());
      userMgr.setPreference(
          NOTIFICATION_DOCUMENT_DELETED_PREF, Boolean.TRUE.toString(), user.getUsername());
      userMgr.setPreference(
          NOTIFICATION_DOCUMENT_UNSHARED_PREF, Boolean.TRUE.toString(), user.getUsername());
    }
  }

  /**
   * Creates a new Experiment form
   *
   * @param the name of the form
   * @param user
   * @return the persisted form
   */
  protected RSForm createAnExperimentForm(String formName, User user) {
    RSForm form = recordFactory.createExperimentForm(formName, " ", piUser);
    form.getAccessControl().setWorldPermissionType(PermissionType.READ);
    return formMgr.save(form, piUser);
  }

  protected Group reloadGroup(Group group) {
    return grpMgr.getGroup((group.getId()));
  }
}
