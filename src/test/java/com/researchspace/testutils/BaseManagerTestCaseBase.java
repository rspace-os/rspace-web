package com.researchspace.testutils;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.core.util.TransformerUtils.toSet;
import static org.junit.Assert.assertFalse;

import com.axiope.search.IFileIndexer;
import com.researchspace.Constants;
import com.researchspace.api.v1.controller.InventoryBulkOperationsApiController.InventoryBulkOperationConfig;
import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiBasket;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiContainerInfo.ApiContainerGridLayoutConfig;
import com.researchspace.api.v1.model.ApiContainerLocationWithContent;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiExtraField.ExtraFieldTypeEnum;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationPost.BulkApiOperationType;
import com.researchspace.api.v1.model.ApiInventoryBulkOperationResult;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiGroupInfoWithSharedFlag;
import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiQuantityInfo;
import com.researchspace.api.v1.model.ApiSampleField;
import com.researchspace.api.v1.model.ApiSampleField.ApiInventoryFieldDef;
import com.researchspace.api.v1.model.ApiSampleTemplate;
import com.researchspace.api.v1.model.ApiSampleTemplatePost;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.api.v1.model.ApiSubSampleNote;
import com.researchspace.auth.ShiroRealm;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.testutil.Invokable;
import com.researchspace.core.testutil.StringAppenderForTestLogging;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.FormDao;
import com.researchspace.dao.ListOfMaterialsDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.SampleDao;
import com.researchspace.dao.UserDao;
import com.researchspace.linkedelements.FieldLinksEntitiesSynchronizer;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.Community;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.MsgOrReqstCreationCfg;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.core.UniquelyIdentifiable;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.field.Field;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleSeriesHelper2;
import com.researchspace.model.netfiles.NetFilesTestFactory;
import com.researchspace.model.netfiles.NfsElement;
import com.researchspace.model.netfiles.NfsFileStore;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DeltaType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.ObjectToIdPropertyTransformer;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.units.RSUnitDef;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.properties.IMutablePropertyHolder;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.CommunityServiceManager;
import com.researchspace.service.DefaultRecordContext;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.DocumentTagManager;
import com.researchspace.service.EcatChemistryFileManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.FolderManager;
import com.researchspace.service.FormManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.LicenseService;
import com.researchspace.service.MediaManager;
import com.researchspace.service.MessageOrRequestCreatorManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.NfsManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RSMathManager;
import com.researchspace.service.RSpaceRequestManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.UserApiKeyManager;
import com.researchspace.service.UserManager;
import com.researchspace.service.chemistry.ChemistryProvider;
import com.researchspace.service.impl.AbstractAppInitializor;
import com.researchspace.service.impl.AbstractContentInitializer;
import com.researchspace.service.impl.ContentInitializerForDevRunManager;
import com.researchspace.service.impl.CustomFormAppInitialiser;
import com.researchspace.service.inventory.BasketApiManager;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.InventoryBulkOperationApiManager;
import com.researchspace.service.inventory.InventoryFileApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.InventoryImportManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.service.inventory.InventoryTagApiManager;
import com.researchspace.service.inventory.ListOfMaterialsApiManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.service.inventory.SubSampleApiManager;
import com.researchspace.service.inventory.impl.InventoryEditLockTracker;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.session.UserSessionTracker;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import liquibase.util.Validate;
import lombok.Data;
import lombok.Value;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.mgt.SecurityManager;
import org.hibernate.LazyInitializationException;
import org.hibernate.SessionFactory;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * It is really important that this is the single point at which the application context is
 * configured by the ContextConfiguration classes.
 *
 * <p>This is because there are both transactional tests (that really make changes to the database)
 * and Spring semi-transactioal tests ( that rollback).<br>
 * Having two application contexts causes a real headache for the database realm of Shiro security -
 * the userDao that is initially configured in {@link ShiroRealm} ends up being a different instance
 * of UserHibernateDao from that creating the user, so all the permissions tests fail.
 *
 * <p>In order to get round this, the @ContextConfiguration needs to be defined in a base class of
 * both Spring Transactional tests and {@link RealTransactionSpringTestBase} , so that a single
 * instance of each bean is created.
 */
@DefaultTestContext
public abstract class BaseManagerTestCaseBase extends AbstractJUnit4SpringContextTests {

  protected static final Logger log = LoggerFactory.getLogger(BaseManagerTestCaseBase.class);

  @BeforeClass
  public static void BeforeClass() throws Exception {
    TestRunnerController.ignoreIfFastRun();
  }

  /** Configurer class to set details of group structure */
  public static class TestGroupConfig {

    protected boolean includeLabAdmin;

    /**
     * @param includeLabAdmin {@link Boolean} as to whether or not to include labAdmin group
     */
    public TestGroupConfig(boolean includeLabAdmin) {
      this.includeLabAdmin = includeLabAdmin;
    }
  }

  /** Password for all users created by createUser.. methods */
  public static final String TESTPASSWD = "testpass";

  protected static final String SYS_ADMIN_PWD = AbstractAppInitializor.SYSADMIN_PWD;
  protected static final String SYS_ADMIN_UNAME = AbstractAppInitializor.SYSADMIN_UNAME;

  protected static final String ADMIN_UNAME = AbstractAppInitializor.ADMIN_UNAME;

  protected static final PaginationCriteria<BaseRecord> DEFAULT_RECORD_PAGINATION =
      PaginationCriteria.createDefaultForClass(BaseRecord.class);

  protected PermissionFactory perFactory;

  public void setUp() throws Exception {
    perFactory = new DefaultPermissionFactory();
  }

  /** Dummy set that mimics the servlet context variable in UI. */
  protected UserSessionTracker activeUsers = new UserSessionTracker();

  protected ResourceBundle rb;
  @Autowired protected DocumentTagManager documentTagManager;
  protected @Autowired MessageSourceUtils messages;
  protected @Autowired Environment env;
  protected @Autowired IMutablePropertyHolder propertyHolder;
  protected @Autowired LicenseService licenseService;
  protected @Autowired GroupManager grpMgr;
  protected @Autowired FieldParser fieldParser;
  protected @Autowired FieldManager fieldMgr;
  protected @Autowired IRecordFactory recordFactory;
  protected @Autowired CommunicationManager communicationMgr;
  protected @Autowired CommunityServiceManager communityMgr;
  protected @Autowired UserApiKeyManager userApiKeyMgr;
  protected @Autowired IFileIndexer fileIndexer;
  protected @Autowired FolderDao folderDao;
  protected @Autowired FolderManager folderMgr;
  protected @Autowired FormDao formDao;
  protected @Autowired IPermissionUtils permissionUtils;
  protected @Autowired RecordDao recordDao;
  protected @Autowired RecordManager recordMgr;
  protected @Autowired BaseRecordManager baseRecordMgr;
  protected @Autowired SessionFactory sessionFactory;
  protected @Autowired UserDao userDao;
  protected @Autowired UserManager userMgr;
  protected @Autowired FormManager formMgr;
  protected @Autowired RecordSharingManager sharingMgr;
  protected @Autowired MediaManager mediaMgr;
  protected @Autowired RSChemElementManager rsChemElementManager;
  protected @Autowired EcatChemistryFileManager chemistryFileManager;
  protected @Autowired ChemistryProvider chemistryProvider;
  protected @Autowired RichTextUpdater richTextUpdater;
  protected @Autowired RSpaceRequestManager reqUpdateMgr;
  protected @Autowired MessageOrRequestCreatorManager reqCreateMgr;
  protected @Autowired RSMathManager mathMgr;
  protected @Autowired FieldLinksEntitiesSynchronizer fieldSyncher;
  protected @Autowired NfsManager nfsMgr;
  protected @Autowired SampleApiManager sampleApiMgr;
  protected @Autowired SampleDao sampleDao;
  protected @Autowired ContainerDao containerDao;
  protected @Autowired SubSampleApiManager subSampleApiMgr;
  protected @Autowired InventoryTagApiManager inventoryTagsApiManager;
  protected @Autowired ContainerApiManager containerApiMgr;
  protected @Autowired InventoryBulkOperationApiManager inventoryBulkOpApiMgr;
  protected @Autowired InventoryImportManager importApiMgr;
  protected @Autowired InventoryEditLockTracker invLockTracker;
  protected @Autowired ListOfMaterialsApiManager listOfMaterialsApiMgr;
  protected @Autowired ListOfMaterialsDao listOfMaterialsDao;
  protected @Autowired InventoryFileApiManager inventoryFileApiMgr;
  protected @Autowired InventoryIdentifierApiManager inventoryIdentifierApiMgr;
  protected @Autowired BasketApiManager basketApiMgr;
  protected @Autowired InventoryPermissionUtils invPermissionUtils;

  @Autowired
  @Qualifier("securityManagerTest")
  public void setSecurityManager(SecurityManager mgr) {
    SecurityUtils.setSecurityManager(mgr);
  }

  /** Default constructor will set the ResourceBundle if needed. */
  public BaseManagerTestCaseBase() {
    // Since a ResourceBundle is not required for each class, just
    // do a simple check to see if one exists
    String className = this.getClass().getName();

    try {
      rb = ResourceBundle.getBundle(className);
    } catch (MissingResourceException mre) {
      // log.warn("No resource bundle found for: " + className);
    }
  }

  /** Mock principal that be passed into controller methods. */
  @Value
  public class MockPrincipal implements Principal {
    private String name;

    public MockPrincipal(User subject) {
      this.name = subject.getUsername();
    }

    public MockPrincipal(String name) {
      this.name = name;
    }
  }

  /**
   * Logs out and logs in a new user (Created from createandSaveUser). Assumes that the user has the
   * default TESTPASSWD value
   *
   * @param user
   */
  protected void logoutAndLoginAs(User user) {
    logoutAndLoginAs(user, TESTPASSWD);
  }

  protected void logoutAndLoginAs(User user, String password) {
    removeCurrentUserFromActiveUsers();
    RSpaceTestUtils.logoutCurrUserAndLoginAs(user.getUsername(), password);
    addLoggedInUserToActiveUsers(user);
  }

  protected void logoutCurrentUser() {
    removeCurrentUserFromActiveUsers();
    RSpaceTestUtils.logout();
  }

  protected void addLoggedInUserToActiveUsers(User user) {
    activeUsers.addUser(user.getUsername(), new MockHttpSession());
    addUserAsSessionAttribute(user);
  }

  private void removeCurrentUserFromActiveUsers() {
    String curruname = (String) SecurityUtils.getSubject().getPrincipal();
    activeUsers.forceRemoveUser(curruname);
  }

  /**
   * Logs in as core sysadmin user role
   *
   * @return the sysadmin User
   */
  protected User logoutAndLoginAsSysAdmin() {
    removeCurrentUserFromActiveUsers();
    RSpaceTestUtils.logoutCurrUserAndLoginAs(
        AbstractAppInitializor.SYSADMIN_UNAME, AbstractAppInitializor.SYSADMIN_PWD);
    User sysadmin = userMgr.getUserByUsername(SYS_ADMIN_UNAME);
    addLoggedInUserToActiveUsers(sysadmin);
    return sysadmin;
  }

  /**
   * Logs in as community admin user role using default 'admin' username
   *
   * @return the 'admin' username user with role ROLE_ADMIN
   */
  protected User logoutAndLoginAsCommunityAdmin() {
    removeCurrentUserFromActiveUsers();
    RSpaceTestUtils.logoutCurrUserAndLoginAs(
        AbstractAppInitializor.ADMIN_UNAME, AbstractAppInitializor.ADMIN_PWD);
    User sysadmin = userMgr.getUserByUsername(ADMIN_UNAME);
    addLoggedInUserToActiveUsers(sysadmin);
    return sysadmin;
  }

  /**
   * Gets the sysadmin user as a User object, withou logging in as sysadmin
   *
   * @return
   */
  protected User getSysAdminUser() {
    return userMgr.getUserByUsername(SYS_ADMIN_UNAME);
  }

  /**
   * Returns a random alphanumeric string length 8
   *
   * @param prefix an optional prefix
   * @return
   */
  protected String getRandomAlphabeticString(String prefix) {
    if (prefix == null) {
      prefix = "";
    }
    return prefix + getRandomName(8);
  }

  /**
   * Adds specified user as a session attribute for testing those methods which assume such an
   * attribute has been set
   *
   * @param user
   */
  private void addUserAsSessionAttribute(User user) {
    org.apache.shiro.session.Session session = SecurityUtils.getSubject().getSession();
    session.setAttribute(SessionAttributeUtils.USER, user);
  }

  /**
   * Creates a community
   *
   * @param admin a user with Admin or SysAdmin role
   * @param uniqueName a unique name for the community
   * @return the saved community
   */
  protected Community createAndSaveCommunity(User admin, String uniqueName) {
    Community community = new Community();
    community.addAdmin(admin);
    community.setUniqueName(uniqueName);
    community.setDisplayName(uniqueName);

    community = communityMgr.save(community);
    // sysadmin already has permission, so don't need to add again
    if (!admin.hasRole(Role.SYSTEM_ROLE)) {
      // refresh
      admin = userMgr.get(admin.getId());
      perFactory.createCommunityPermissionsForAdmin(admin, community);
      userMgr.save(admin);
    }
    return community;
  }

  /**
   * For tests that rely on search, or save a document in the file store, this method will make sure
   * the lucene full text search index is initialised.
   *
   * @throws IOException
   */
  protected void initialiseFileIndexer() throws IOException {
    initialiseFileIndexer(true);
  }

  protected void initialiseFileIndexer(boolean force) throws IOException {
    if (force || !fileIndexer.isInitialised()) {
      if (fileIndexer.isInitialised()) {
        fileIndexer.close();
      }
      fileIndexer.init(true);
      fileIndexer.indexFile(RSpaceTestUtils.getResource("testTxt.txt"));
    }
  }

  /**
   * Creates and persists a single structured document with a single text field, and inserts the
   * supplied text into that field and saves it.
   *
   * <p>For realism this should be an HTML string since the textfields store HTML added in from
   * tinyMCE in the application.
   *
   * @return The created {@link StructuredDocument}.
   */
  protected StructuredDocument createBasicDocumentInRootFolderWithText(
      User user, final String text) {
    Folder root = folderDao.getRootRecordForUser(user);
    return createBasicDocumentInFolder(user, root, text);
  }

  protected Folder createFolder(Folder parentFolder, User user, String newFolderName) {
    Folder newFolder = TestFactory.createAFolder(newFolderName, user);
    newFolder = folderMgr.save(newFolder, user);
    parentFolder.addChild(newFolder, user);
    folderMgr.save(parentFolder, user);
    return newFolder;
  }

  /**
   * Creates a subfolder with name <code>subfolderName</code> in the user's root folder, then
   * creates a basic document with specified <code>text</code> in that folder.
   *
   * @param user
   * @param subfolderName
   * @param text
   * @return The created BasicDocument.
   */
  protected StructuredDocument createBasicDocumentInRootSubfolderWithText(
      User user, String subfolderName, String text) {
    Folder root = folderDao.getRootRecordForUser(user);
    Folder subFolder = createFolder(root, user, subfolderName);
    return createBasicDocumentInFolder(user, subFolder, text);
  }

  /**
   * Facade method to create a document from a template and put it in the user's home folder.
   *
   * @param subject The logged in user
   * @param template A template
   * @param docName The name of the document that will be created from the template
   * @return The new document created from the template
   */
  protected StructuredDocument createFromTemplate(
      User subject, StructuredDocument template, String docName) {
    Validate.isTrue(template.isTemplate(), "the supplied template is not actually a template!");
    return recordMgr
        .createFromTemplate(template.getId(), docName, subject, subject.getRootFolder().getId())
        .getUniqueCopy()
        .asStrucDoc();
  }

  /**
   * Creates a BasicDocument with specified <code>text</code> into the supplied <code>folder</code>
   *
   * @param user
   * @param folder
   * @param text
   * @return
   */
  protected StructuredDocument createBasicDocumentInFolder(
      User user, Folder folder, final String text) {
    StructuredDocument doc = recordMgr.createBasicDocument(folder.getId(), user);
    doc.getFields().get(0).setFieldData(text); // add some content
    doc.setName(getRandomAlphabeticString("BasicDocument"));
    recordMgr.save(doc, user);
    return doc;
  }

  protected StructuredDocument createOntologyDocumentInFolder(
      User user, Folder folder, final String text) {
    RSForm ontologyForm = formMgr.findOldestFormByName(CustomFormAppInitialiser.ONTOLOGY_FORM_NAME);
    StructuredDocument doc =
        recordMgr.createNewStructuredDocument(folder.getId(), ontologyForm.getId(), user);
    Field field = doc.getFields().get(0);
    field.setData(text);
    doc.setName(getRandomAlphabeticString("BasicDocument"));
    recordMgr.save(doc, user);
    return doc;
  }

  /**
   * Saves a "Picture1.png" image file in user's image gallery
   *
   * @param user
   * @return the saved EcatImage
   * @throws IOException
   */
  protected EcatImage addImageToGallery(User user) throws IOException {
    return addImageToGallery(user, "Picture1.png");
  }

  /**
   * Adds an image to the Gallery
   *
   * @param user
   * @param filename A filename in testresources folder
   * @return
   * @throws IOException
   */
  protected EcatImage addImageToGallery(User user, String filename) throws IOException {
    return mediaMgr.saveNewImage(
        filename, RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder(filename), user, null);
  }

  /** Upload "Picture2.png" file as a new version of an image with given id */
  protected EcatImage updateImageInGallery(Long imageId, User user) throws IOException {
    InputStream picture2InputStream =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture2.png");
    EcatMediaFile updatedImg =
        mediaMgr.updateMediaFile(imageId, picture2InputStream, "Picture2.png", user, null);
    return (EcatImage) updatedImg;
  }

  /** Upload "base64Image.txt" content as an edited version of an image with given id */
  protected EcatImage editImageInGallery(Long imageId, User user) throws IOException {
    EcatImage sourceImage = recordMgr.getEcatImage(imageId, false);
    String base64png =
        "image/png,"
            + FileUtils.readFileToString(RSpaceTestUtils.getResource("base64Image.txt"), "UTF-8");
    EcatMediaFile updatedImg = mediaMgr.saveEditedImage(sourceImage, base64png, user);
    return (EcatImage) updatedImg;
  }

  protected Folder createImgGallerySubfolder(String subfolderName, User user) {
    Folder imgGallery =
        recordMgr.getGallerySubFolderForUser(MediaUtils.IMAGES_MEDIA_FLDER_NAME, user);
    return folderMgr.createNewFolder(imgGallery.getId(), subfolderName, user);
  }

  protected EcatImage addImageToGalleryFolder(Folder folder, User user) throws IOException {
    return mediaMgr.saveNewImage(
        "Picture1.png",
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png"),
        user,
        folder);
  }

  protected EcatDocumentFile addFileAttachmentToField(Field field, User user)
      throws IOException, URISyntaxException {
    return addAttachmentDocumentToField(RSpaceTestUtils.getAnyAttachment(), field, user);
  }

  protected EcatDocumentFile updateFileAttachmentInGallery(Long attachmentId, User user)
      throws IOException {
    EcatMediaFile updatedAttachment =
        mediaMgr.updateMediaFile(
            attachmentId,
            new FileInputStream(RSpaceTestUtils.getAnyAttachment()),
            "genFilesi2.txt",
            user,
            null);
    return (EcatDocumentFile) updatedAttachment;
  }

  /**
   * Saves a document in the gallery, and adds attachment text to a specified field, and creates
   * media file link between the field and the media file
   *
   * @param afile
   * @param field
   * @param user
   * @return the created {@link EcatDocumentFile}
   * @throws URISyntaxException
   * @throws IOException
   */
  protected EcatDocumentFile addAttachmentDocumentToField(File afile, Field field, User user)
      throws IOException, URISyntaxException {
    EcatDocumentFile docFile =
        mediaMgr.saveNewDocument(afile.getName(), new FileInputStream(afile), user, null, null);

    String attach = richTextUpdater.generateURLString(docFile);
    String data = field.getFieldData() + attach;
    setDataAndSave(field, user, data);
    docFile =
        (EcatDocumentFile)
            fieldMgr
                .addMediaFileLink(docFile.getId(), user, field.getId(), false)
                .get()
                .getMediaFile();
    fieldMgr.save(field, user);
    return docFile;
  }

  protected Field removeAttachmentFromField(EcatDocumentFile emf, Field field, User subject) {
    String link = richTextUpdater.generateURLString(emf);
    String currData = field.getFieldData();
    String newData = currData.replace(link, "");
    Field newField = (Field) field.shallowCopy();
    newField.setFieldData(newData);
    fieldSyncher.syncFieldWithEntitiesOnautosave(
        field, (Field) field.shallowCopy(), newField.getFieldData(), subject);
    return newField;
  }

  /**
   * Adds an unspecified attachment to the Gallery
   *
   * @param user
   * @return the created attachment
   * @throws IOException
   * @throws URISyntaxException
   */
  protected EcatDocumentFile addDocumentToGallery(User user)
      throws IOException, URISyntaxException {
    File afile = RSpaceTestUtils.getAnyAttachment();
    return addToGallery(afile, user);
  }

  /**
   * Adds particular Document to the Gallery
   *
   * @param user
   * @return the created attachment
   * @throws IOException
   * @throws URISyntaxException
   */
  protected EcatDocumentFile addToGallery(File file, User user)
      throws IOException, URISyntaxException {
    return mediaMgr.saveNewDocument(file.getName(), new FileInputStream(file), user, null, null);
  }

  protected EcatDocumentFile addDocumentFromTestResourcesToGallery(String fileName, User user)
      throws IOException, URISyntaxException {
    File afile = RSpaceTestUtils.getResource(fileName);
    return mediaMgr.saveNewDocument(afile.getName(), new FileInputStream(afile), user, null, null);
  }

  /**
   * Saves an unspecified TIFF image to to user's image gallery
   *
   * @param user
   * @return the saved ECatImage
   * @throws IOException
   */
  protected EcatImage addTiffImageToGallery(User user) throws IOException {
    return mediaMgr.saveNewImage(
        "Picture1.tiff",
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.tiff"),
        user,
        null);
  }

  /** Adds an image to the gallery, and creates a link in the text field */
  protected EcatImage addImageToField(Field field, User user) throws IOException {
    return addImageToField(field, user, null);
  }

  /**
   * Adds an image to the gallery, and creates a link in the text field.
   *
   * @param field
   * @param user
   * @param img optional image to use, if null new one will be added to Gallery
   */
  protected EcatImage addImageToField(Field field, User user, EcatImage img) throws IOException {
    EcatImage addedImg = addImageToFieldButNoFieldAttachment(field, user, img);
    fieldMgr.addMediaFileLink(addedImg.getId(), user, field.getId(), false);
    return addedImg;
  }

  /**
   * @param field a text field.
   * @param user
   * @param img optional image to use, if null new one will be added to Gallery
   */
  protected EcatImage addImageToFieldButNoFieldAttachment(Field field, User user, EcatImage img)
      throws IOException {
    if (img == null) {
      img = addImageToGallery(user);
    }
    String fData = field.getFieldData();
    String imgLink = richTextUpdater.generateRawImageElement(img, field.getId() + "");
    fData = fData + imgLink;
    setDataAndSave(field, user, fData);
    return img;
  }

  /**
   * Appends String HTML to field content
   *
   * @param field
   * @param htmlContent
   * @param user
   * @return The updated Field content
   * @throws IOException
   */
  protected String appendContentToField(Field field, String htmlContent, User user)
      throws IOException {
    String fData = field.getFieldData();
    fData = fData + htmlContent;
    setDataAndSave(field, user, fData);
    return fData;
  }

  private void setDataAndSave(Field field, User user, String fData) {
    field.setFieldData(fData);
    field.getStructuredDocument().notifyDelta(DeltaType.FIELD_CHG);
    recordMgr.save(field.getStructuredDocument(), user);
  }

  /**
   * Saves audio file in the gallery, and adds to a specified field
   *
   * @param field
   * @param user
   * @return
   * @throws IOException
   */
  protected EcatAudio addAudioFileToField(Field field, User user) throws IOException {
    File afile = RSpaceTestUtils.getResource("mpthreetest.mp3");
    return (EcatAudio) addAudioVideoFileToGalleryAndField(field, user, afile);
  }

  protected EcatAudio updateAudioInGallery(Long audioId, User user) throws IOException {
    InputStream mp3InputStream =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("mpthreetest.mp3");
    EcatMediaFile updatedImg =
        mediaMgr.updateMediaFile(audioId, mp3InputStream, "mpthreetest2.mp3", user, null);
    return (EcatAudio) updatedImg;
  }

  protected EcatVideo addVideoFileToField(Field field, User user) throws IOException {
    File afile = RSpaceTestUtils.getResource("small.mp4");
    return (EcatVideo) addAudioVideoFileToGalleryAndField(field, user, afile);
  }

  protected EcatVideo updateVideoInGallery(Long videoId, User user) throws IOException {
    InputStream mp4InputStream =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("small.mp4");
    EcatMediaFile updatedVideo =
        mediaMgr.updateMediaFile(videoId, mp4InputStream, "small2.mp4", user, null);
    return (EcatVideo) updatedVideo;
  }

  private EcatMediaFile addAudioVideoFileToGalleryAndField(Field field, User user, File afile)
      throws IOException, FileNotFoundException {
    if (!MediaUtils.isAVFile(afile)) {
      throw new IllegalArgumentException("file is not an av file");
    }
    EcatMediaFile addedMedia = null;
    String avHTML = "";
    if (MediaUtils.isVideoFile(FilenameUtils.getExtension(afile.getName()))) {
      addedMedia =
          mediaMgr.saveNewVideo(afile.getName(), new FileInputStream(afile), user, null, null);
      avHTML = richTextUpdater.generateURLString((EcatVideo) addedMedia, field.getId());
    } else {
      addedMedia =
          mediaMgr.saveNewAudio(afile.getName(), new FileInputStream(afile), user, null, null);
      avHTML = richTextUpdater.generateURLString((EcatAudio) addedMedia, field.getId());
    }

    String data = field.getFieldData() + avHTML;
    setDataAndSave(field, user, data);
    fieldMgr.addMediaFileLink(addedMedia.getId(), user, field.getId(), false);
    return addedMedia;
  }

  protected EcatAudio addAudioFileToGallery(User user) throws IOException, URISyntaxException {
    File afile = RSpaceTestUtils.getResource("mpthreetest.mp3");
    return mediaMgr.saveNewAudio(afile.getName(), new FileInputStream(afile), user, null, null);
  }

  protected EcatChemistryFile addChemistryFileToGallery(String fileName, User user)
      throws IOException {
    File chemFile = RSpaceTestUtils.getResource(fileName);
    EcatChemistryFile chemistryFile =
        mediaMgr.saveNewChemFile(
            chemFile.getName(), new FileInputStream(chemFile), user, null, null);
    String converted = chemistryProvider.convert(chemistryFile.getChemString());
    // Create Basic Chem Element to simulate file being uploaded to gallery
    RSChemElement rsChemElement =
        RSChemElement.builder()
            .ecatChemFileId(chemistryFile.getId())
            .dataImage(getBase64Image().getBytes(StandardCharsets.UTF_8))
            .chemElements(converted)
            .chemElementsFormat(ChemElementsFormat.MRV)
            .build();
    rsChemElementManager.save(rsChemElement, user);
    return chemistryFile;
  }

  /**
   * Adds a new image annotation on a new image to a text field. This method
   *
   * <ul>
   *   <li>creates an EcatImage
   *   <li>adds an annotation
   *   <li>adds annotation link to the field text
   * </ul>
   *
   * @param textField
   * @return The EcatImageAnnotation created. The text field is populated with the
   *     EcatImageAnnotation link
   * @throws IOException
   */
  protected EcatImageAnnotation addImageAnnotationToField(Field textField, User user)
      throws IOException {

    String base64Image = getBase64Image();
    EcatImage img = addImageToGallery(user);

    EcatImageAnnotation annot =
        mediaMgr.saveImageAnnotation(
            getTestZwibblerAnnotationString(""), base64Image,
            textField.getId(), textField.getStructuredDocument(),
            img.getId(), user);

    String annotationLink =
        richTextUpdater.generateAnnotatedImageElement(annot, textField.getId() + "");

    String newData = textField.getFieldData() + annotationLink;
    textField.setFieldData(newData);
    textField.getStructuredDocument().notifyDelta(DeltaType.IMAGE_ANNOTATION);
    recordMgr.save(textField.getStructuredDocument(), user);
    return annot;
  }

  /**
   * Creates a new sketch in the specified field and a new {@link EcatImageAnnotation} object in the
   * DB
   *
   * @param textField
   * @param user
   * @return the created sketch as an {@link EcatImageAnnotation} object
   * @throws IOException
   */
  protected EcatImageAnnotation addSketchToField(Field textField, User user) throws IOException {

    String base64Image = getBase64Image();
    EcatImageAnnotation sketch =
        mediaMgr.saveSketch(
            getTestZwibblerAnnotationString("text"),
            base64Image,
            "",
            textField.getId(),
            textField.getStructuredDocument(),
            user);

    String sketchContent = richTextUpdater.generateImgLinkForSketch(sketch);
    textField.setFieldData(textField.getFieldData() + sketchContent);
    textField.getStructuredDocument().notifyDelta(DeltaType.IMAGE_ANNOTATION);
    recordMgr.save(textField.getStructuredDocument(), user);

    return sketch;
  }

  protected String getTestZwibblerAnnotationString(String includeText) {
    return "zwibbler3.[{\"test\":\"" + includeText + "\"}]";
  }

  /**
   * Gets any Base64 image as a string
   *
   * @return A Base64 image as a String.
   * @throws IOException
   */
  protected String getBase64Image() throws IOException {
    File base64 = RSpaceTestUtils.getResource("base64Image.txt");
    return "data:image/png;base64," + FileUtils.readFileToString(base64, Charset.defaultCharset());
  }

  /**
   * Adds a new chemical structure to a text field with a linked chemistry file in the gallery
   *
   * @param chemFile The chemistry file to link to
   * @param field the field to add the chemical element to
   * @param owner the user who owns the document containing the field
   * @return The RSChemElement created. The text field is populated with the chem image
   * @throws IOException
   */
  protected RSChemElement addChemStructureToFieldWithLinkedChemFile(
      EcatChemistryFile chemFile, Field field, User owner) throws IOException {

    RSChemElement chem =
        rsChemElementManager.generateRsChemElementFromChemistryFile(chemFile, field.getId(), owner);

    String chemLink =
        richTextUpdater.generateURLStringForRSCheElementLinkWithFileId(
            chem.getId(), chem.getParentId(), chemFile.getId(), 50, 50);
    String newData = field.getFieldData() + chemLink;
    fieldMgr.addMediaFileLink(chemFile.getId(), owner, field.getId(), false);
    setDataAndSave(field, owner, newData);

    return chem;
  }

  /**
   * Adds a new chemical structure to a text field
   *
   * @param field the field to add the chem element to
   * @return The RSChemElement created. The text field is populated with the chem image
   * @throws IOException
   */
  protected RSChemElement addChemStructureToField(Field field, User owner) throws IOException {
    String imageBytes = RSpaceTestUtils.getChemImage();
    String chemStr = RSpaceTestUtils.getExampleChemString();
    ChemicalDataDTO chemicalData =
        ChemicalDataDTO.builder()
            .chemElements(chemStr)
            .fieldId(field.getId())
            .imageBase64(imageBytes)
            .fieldId(field.getId())
            .chemElementsFormat(ChemElementsFormat.MOL.getLabel())
            .build();

    RSChemElement chem = rsChemElementManager.saveChemElement(chemicalData, owner);

    String chemLink =
        richTextUpdater.generateURLStringForRSChemElementLink(
            chem.getId(), chem.getParentId(), 50, 50);
    String newData = field.getFieldData() + chemLink;
    setDataAndSave(field, owner, newData);

    return chem;
  }

  /**
   * Adds a new chemical structure to a text field with specified chem string
   *
   * @param field the field to add the chem element to
   * @param chemString the chemical string to add to the field
   * @return The RSChemElement created. The text field is populated with the chem image
   * @throws IOException
   */
  protected RSChemElement addChemStructureToField(String chemString, Field field, User owner)
      throws IOException {
    String imageBytes = RSpaceTestUtils.getChemImage();

    String mrv = chemistryProvider.convert(chemString);
    ChemicalDataDTO chemicalData =
        ChemicalDataDTO.builder()
            .chemElements(mrv)
            .fieldId(field.getId())
            .imageBase64(imageBytes)
            .fieldId(field.getId())
            .chemElementsFormat(ChemElementsFormat.MOL.getLabel())
            .build();

    RSChemElement chem = rsChemElementManager.saveChemElement(chemicalData, owner);

    String chemLink =
        richTextUpdater.generateURLStringForRSChemElementLink(
            chem.getId(), chem.getParentId(), 50, 50);
    String newData = field.getFieldData() + chemLink;
    setDataAndSave(field, owner, newData);

    return chem;
  }

  /**
   * Adds a new RSMath element to a text field and saves to database.
   *
   * @param field
   * @param owner
   * @return
   * @throws IOException
   */
  protected RSMath addMathToField(Field field, User owner) throws IOException {
    RSMath transientMath = TestFactory.createAMathElement();
    RSMath saved =
        mediaMgr.saveMath(
            transientMath.getMathSvgString(), field.getId(), transientMath.getLatex(), null, owner);
    String mathHTml =
        richTextUpdater.generateURLStringForRSMathLink(
            saved.getId(), saved.getLatex(), "1ex", "1ex");
    String newData = field.getFieldData() + mathHTml;
    setDataAndSave(field, owner, newData);
    return saved;
  }

  /**
   * Creates a new file store and file system.<br>
   * Adds a new NfsElement element to a text field and saves to database. <br>
   * This is not backed by a real filesystem, it is just creating the database entries and adding a
   * link to a pretend file called "anyfile.txt"
   *
   * @param field
   * @param owner
   * @param filePath relative file path, to enable creation of distinct {@link NfsElement}
   * @return
   */
  protected NfsElement addNfsFileStoreAndLink(Field field, User owner, String filePath) {
    NfsFileStore store = NetFilesTestFactory.createAnyNfsFileStore(owner);
    nfsMgr.saveNfsFileSystem(store.getFileSystem());
    nfsMgr.saveNfsFileStore(store);
    return addNfsFileStoreLink(field, owner, store.getId(), filePath, false);
  }

  protected NfsElement addNfsFileStoreLink(
      Field field, User owner, Long fileStoreId, String filePath, boolean isFolder) {
    String nfsHtml = richTextUpdater.generateURLStringForNfs(fileStoreId, filePath, isFolder);
    String newData = field.getFieldData() + nfsHtml;
    setDataAndSave(field, owner, newData);
    NfsElement nfsElement = new NfsElement(fileStoreId, filePath);
    if (isFolder) {
      nfsElement.setLinkType(NfsElement.LINKTYPE_DIR);
    }
    return nfsElement;
  }

  protected void addEmbeddedIframeToField(Field field, User user) {
    String embedContent = richTextUpdater.generateIframeEmbedFromJove();
    String newData = field.getFieldData() + embedContent;
    setDataAndSave(field, user, newData);
  }

  /**
   * Asserts that a particular exception is thrown.
   *
   * @param invokable The code to be run that should throw an exception
   * @param clazz The expected exception class
   * @throws Exception
   */
  protected void assertExceptionThrown(Invokable invokable, Class<? extends Throwable> clazz)
      throws Exception {
    CoreTestUtils.assertExceptionThrown(invokable, clazz);
  }

  /**
   * Convenience method to assert that an arbitrary piece of code throws an AuthorizationException
   *
   * @param invokable
   * @throws Exception
   */
  protected void assertAuthorisationExceptionThrown(Invokable invokable) throws Exception {
    assertExceptionThrown(invokable, AuthorizationException.class);
  }

  /**
   * Convenience method to assert that an arbitrary piece of code throws an
   * LazyInitializationException, useful for testing lazy-loading strategies.
   *
   * @param invokable
   * @throws Exception
   */
  protected void assertLazyInitializationExceptionThrown(Invokable invokable) throws Exception {
    assertExceptionThrown(invokable, LazyInitializationException.class);
  }

  /**
   * Will clear the Hibernate session and detach all objects, useful for testing lazy-loading
   * strategies. <br>
   * The session is still open after invoking this method and new queries can be issued.
   */
  protected void clearSessionAndEvictAll() {
    sessionFactory.getCurrentSession().clear();
  }

  protected String getMsgFromResourceBundler(String key) {
    return messages.getMessage(key);
  }

  public String getMsgFromResourceBundler(String msgKey, String arg) {
    return messages.getMessage(msgKey, new Object[] {arg});
  }

  /**
   * Shares a record with a previously created group with read permission
   *
   * @param user - must be a group member, probably the doc owner
   * @param group - the group to share with, must be initialised with sharing folders etc
   * @param sd The document to share.
   */
  protected ServiceOperationResult<Set<RecordGroupSharing>> shareRecordWithGroup(
      final User user, Group group, StructuredDocument sd) {
    return sharingMgr.shareRecord(
        user, sd.getId(), new ShareConfigElement[] {new ShareConfigElement(group.getId(), "read")});
  }

  /**
   * Shares record into a group notebook
   *
   * @param record
   * @param notebook
   * @param group
   * @param docOwner
   * @return the sharedGroup Entity or empty optional if not found
   */
  protected Optional<RecordGroupSharing> shareRecordIntoGroupNotebook(
      BaseRecord record, Notebook notebook, Group group, User docOwner) {

    ShareConfigElement cfg = new ShareConfigElement();
    cfg.setGroupFolderId(notebook.getId());
    cfg.setGroupid(group.getId());
    cfg.setOperation("read"); // doesn't matter, notebook permission will decide

    ServiceOperationResult<Set<RecordGroupSharing>> shared =
        sharingMgr.shareRecord(docOwner, record.getId(), new ShareConfigElement[] {cfg});
    return shared.getEntity().isEmpty()
        ? Optional.empty()
        : Optional.ofNullable(shared.getEntity().iterator().next());
  }

  /**
   * Shares a record with a user in a previously created group with read permission
   *
   * @param owner - must be a group member, probably the doc owner
   * @param sharee - the user to share with, must be initialised with sharing folders etc
   * @param toShare The document to share.
   * @return
   */
  protected ServiceOperationResult<Set<RecordGroupSharing>> shareRecordWithUser(
      User owner, StructuredDocument toShare, User sharee) {
    return doShare(owner, sharee, toShare, false);
  }

  /**
   * Shares a record with a user in a previously created group with edit permission
   *
   * @param owner - must be a group member, probably the doc owner
   * @param sharee - the user to share with, must be initialised with sharing folders etc
   * @param toShare The document to share.
   * @return
   */
  protected ServiceOperationResult<Set<RecordGroupSharing>> shareRecordWithUserForEdit(
      User owner, StructuredDocument toShare, User sharee) {
    return doShare(owner, sharee, toShare, true);
  }

  private ServiceOperationResult<Set<RecordGroupSharing>> doShare(
      User owner, User sharee, StructuredDocument toShare, boolean write) {
    ShareConfigElement cfg = new ShareConfigElement(sharee.getId(), "read");
    cfg.setUserId(sharee.getId());
    if (write) {
      cfg.setOperation("write");
    }
    return sharingMgr.shareRecord(owner, toShare.getId(), new ShareConfigElement[] {cfg});
  }

  /**
   * Sets up the given logger to log to a string, which can be queried in tests
   *
   * @param log
   * @return
   */
  protected StringAppenderForTestLogging configureTestLogger(org.apache.logging.log4j.Logger log) {
    return CoreTestUtils.configureStringLogger(log);
  }

  /**
   * Given a string, asserts that there are no unreplaced Velocity variables
   *
   * @param msg A String that is the output of a Velocity template rendering.
   */
  protected void assertVelocityVariablesReplaced(String msg) {
    assertFalse(msg.contains("$"));
  }

  /**
   * Given a structured document id, will create a template from that document, using all fields and
   * return it
   *
   * @param docId
   * @param user
   * @return A {@link StructuredDocument} of the created template.
   * @throws DocumentAlreadyEditedException
   */
  protected StructuredDocument createTemplateFromDocumentAndAddtoTemplateFolder(
      Long docId, User user) throws DocumentAlreadyEditedException {

    StructuredDocument doc = (StructuredDocument) recordMgr.get(docId);
    List<Long> ids =
        doc.getFields().stream()
            .map(new ObjectToIdPropertyTransformer())
            .collect(Collectors.toList());

    StructuredDocument template =
        recordMgr.createTemplateFromDocument(docId, ids, user, getRandomName(5));
    return template;
  }

  /**
   * Creates a new Form with a single text field called 'Data' that is world-readable
   *
   * @param user
   * @return the new RSForm
   */
  protected RSForm createAnyForm(User user) {
    RSForm simpleDocForm = recordFactory.createBasicDocumentForm(user);
    simpleDocForm.getAccessControl().setWorldPermissionType(PermissionType.READ);
    return formMgr.save(simpleDocForm, user);
  }

  /**
   * Creates a new Form with a single text field called 'Data' that is only readable by creator and
   * PI
   *
   * @param user
   * @return the new RSForm
   */
  protected RSForm createAnyPrivateForm(User user) {
    RSForm simpleDocForm = recordFactory.createBasicDocumentForm(user);
    return formMgr.save(simpleDocForm, user);
  }

  /**
   * Creates a collaboration group between 2 existing lab groups For this to work:
   *
   * <ul>
   *   <li>g1 and g2 should both be lab groups
   *   <li>they should be persisted groups
   *   <li>This method will return the first collaboration group found; therefore there shouldn't be
   *       previously created collab groups involving these groups already created in the test.
   *
   * @param g1
   * @param g2
   * @return the collaboration group.
   */
  protected Group createCollabGroupBetweenGroups(Group g1, Group g2) {
    User pi1 = IterableUtils.get(g1.getPiusers(), 0);
    User pi2 = IterableUtils.get(g2.getPiusers(), 0);
    MsgOrReqstCreationCfg config = new MsgOrReqstCreationCfg();
    config.setMessageType(MessageType.REQUEST_EXTERNAL_SHARE);
    config.setOptionalMessage("msg");

    MessageOrRequest mor =
        reqCreateMgr.createRequest(config, pi1.getUsername(), toSet(pi2.getUsername()), null, null);
    logoutAndLoginAs(pi2);

    reqUpdateMgr.updateStatus(
        pi2.getUsername(), CommunicationStatus.COMPLETED, mor.getId(), "updated");

    pi1 = userMgr.get(pi1.getId());
    return pi1.getCollaborationGroups().iterator().next();
  }

  /**
   * Creates a notebook with <code>numEntries</code> entries, all empty, named "entry_1", "entry_2",
   * etc.
   *
   * @param parentFlderId parent folder id to hold the notebook
   * @param nbookname a name
   * @param numEntries the number of entries to created
   * @param user Authenticated user
   * @return Notebook with numEntries children
   * @throws InterruptedException
   */
  protected Notebook createNotebookWithNEntries(
      Long parentFlderId, String nbookname, int numEntries, User user) throws InterruptedException {
    return createNotebookWithNEntriesAndDelayBetweenEntries(
        parentFlderId, nbookname, numEntries, user, 5);
  }

  protected Notebook createNotebookWithNEntriesAndDelayBetweenEntries(
      Long parentFlderId,
      String nbookname,
      int numEntries,
      User user,
      int delayMillisBetweenCreations)
      throws InterruptedException {
    Notebook nbook =
        (Notebook)
            folderMgr.createNewNotebook(parentFlderId, nbookname, new DefaultRecordContext(), user);
    for (int i = 1; i <= numEntries; i++) {
      Thread.sleep(delayMillisBetweenCreations);
      StructuredDocument doc = recordMgr.createBasicDocument(nbook.getId(), user);
      doc.setName("entry_" + i);
      recordMgr.save(doc, user);
      log.info("Created notebook entry {} [{}]", doc.getName(), doc.getId());
    }
    return folderMgr.getNotebook(nbook.getId());
  }

  /**
   * Shares nb with an individual in the group with write permission
   *
   * @param sharing
   * @param nb
   * @param sharee
   * @return the shared notebook or empty optional if was not shared (e.g. if it was already shared)
   */
  protected Optional<Notebook> shareNotebookWithGroupMember(
      User sharing, Notebook nb, User sharee) {
    ShareConfigElement el = new ShareConfigElement(sharee.getId(), "write");
    el.setUserId(sharee.getId());
    return extractDoc(sharingMgr.shareRecord(sharing, nb.getId(), new ShareConfigElement[] {el}))
        .map(br -> (Notebook) br);
  }

  private Optional<BaseRecord> extractDoc(
      ServiceOperationResult<Set<RecordGroupSharing>> shareRecord) {
    return shareRecord.getEntity().isEmpty()
        ? Optional.empty()
        : Optional.ofNullable(shareRecord.getEntity().iterator().next().getShared());
  }

  /**
   * Shares notebook with a group for read or write.
   *
   * @param sharing
   * @param nb
   * @param group
   * @param permission "read" or "write"
   * @return the shared notebook or empty optional if was not shared (e.g. if it was already shared)
   */
  protected Optional<Notebook> shareNotebookWithGroup(
      User sharing, Notebook nb, Group group, String permission) {
    ShareConfigElement el = new ShareConfigElement(group.getId(), permission);
    return extractDoc(sharingMgr.shareRecord(sharing, nb.getId(), new ShareConfigElement[] {el}))
        .map(br -> (Notebook) br);
  }

  /**
   * Unshares a previously shared nb with an individual in the group with write permission
   *
   * @param sharing
   * @param nb
   * @param sharee
   * @return the shared notebook
   */
  protected void unshareNotebookWithGroupMember(User sharing, Notebook nb, User sharee) {
    ShareConfigElement el = new ShareConfigElement(sharee.getId(), "write");
    el.setUserId(sharee.getId());
    sharingMgr.unshareRecord(sharing, nb.getId(), new ShareConfigElement[] {el});
  }

  /**
   * Unshares a previously shared nb with the group with write permission
   *
   * @param sharing
   * @param nbOrDoc
   * @param group
   * @return the shared notebook
   */
  protected void unshareRecordORNotebookWithGroup(
      User sharing, BaseRecord nbOrDoc, Group group, String perm) {
    ShareConfigElement el = new ShareConfigElement(group.getId(), perm);
    sharingMgr.unshareRecord(sharing, nbOrDoc.getId(), new ShareConfigElement[] {el});
  }

  /**
   * Creates and persists a group for the specified users. Runs in its own transaction.
   *
   * @param sessionUser
   * @param pi
   * @param admin
   * @param users ALL users including PIs and admins
   * @return the created group
   * @throws IllegalAddChildOperation
   */
  protected Group createGroupForUsers(User sessionUser, String pi, String admin, User... users)
      throws IllegalAddChildOperation {

    Group grp = new Group(getRandomName(10), users[0]);
    grp.setOwner(sessionUser);
    grp.setDisplayName(grp.getUniqueName() + "display");

    for (ConstraintBasedPermission cbp : perFactory.createDefaultGlobalGroupPermissions(grp)) {
      grp.addPermission(cbp);
    }
    grpMgr.saveGroup(grp, sessionUser);
    grpMgr.addMembersToGroup(grp.getId(), Arrays.asList(users), pi, admin, sessionUser);
    grpMgr.createSharedCommunalGroupFolders(grp.getId(), sessionUser.getUsername());

    return grpMgr.getGroup(grp.getId());
  }

  protected long getRecordCountInFolderForUser(long folderid) {
    // need this as we have > 10 now
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pgCrit.setResultsPerPage(20);
    ISearchResults<BaseRecord> rootListing = recordMgr.listFolderRecords(folderid, pgCrit);
    return rootListing.getTotalHits();
  }

  protected long getRecordCountInRootFolderForUser(User user) {
    // need this as we have  > 10 now
    PaginationCriteria<BaseRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(BaseRecord.class);
    pgCrit.setResultsPerPage(20);
    ISearchResults<BaseRecord> rootListing =
        recordMgr.listFolderRecords(user.getRootFolder().getId(), pgCrit);
    return rootListing.getTotalHits();
  }

  /**
   * Gets an interface cast to original implementation class, stripped of Spring AOP proxies, for
   * test setup. To be used with caution, as this somewhat defeats the purpose of interfaces, but is
   * useful to call setXXX methods on the implementation class to programmatically configure the
   * bean at runtime.
   *
   * @param proxy
   * @param targetClass
   * @return
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  protected <T> T getTargetObject(Object proxy, Class<T> targetClass) throws Exception {
    while ((AopUtils.isJdkDynamicProxy(proxy))) {
      return (T) getTargetObject(((Advised) proxy).getTargetSource().getTarget(), targetClass);
    }
    return (T) proxy; // expected to be cglib proxy then, which is simply a specialized class
  }

  /**
   * Boolean test that a returned ISearchResult does not contain elements with duplicate ids, and
   * does not contain <code>null</code> elements.
   *
   * @param results
   * @return
   */
  protected boolean assertSearchResultsAreDistinct(
      ISearchResults<? extends UniquelyIdentifiable> results) {
    Set<Long> ids = new HashSet<Long>();
    for (UniquelyIdentifiable ui : results.getResults()) {
      if (ui == null) {
        return false;
      }
      ids.add(ui.getId());
    }
    return ids.size() == results.getResults().size();
  }

  /**
   * Utility method to get a bean of the specified class from the application context without need
   * for casting.
   *
   * @param clazz
   * @return
   */
  protected <T> T getBeanOfClass(Class<T> clazz) {
    return (T) applicationContext.getBean(clazz);
  }

  protected int getActiveRequestCountForUser(User u) {
    ISearchResults<MessageOrRequest> mors = searchDBForRequests(u);
    return mors.getTotalHits().intValue();
  }

  protected ISearchResults<MessageOrRequest> searchDBForRequests(User u) {
    PaginationCriteria<CommunicationTarget> pc = new PaginationCriteria<CommunicationTarget>();
    pc.setOrderBy("communication.creationTime");
    ISearchResults<MessageOrRequest> mors =
        communicationMgr.getActiveMessagesAndRequestsForUserTarget(u.getUsername(), pc);
    return mors;
  }

  protected UserSessionTracker anySessionTracker() {
    return new UserSessionTracker();
  }

  /**
   * @see #createTestGroup(int, TestGroupConfig)
   */
  protected TestGroup createTestGroup(int numUsers) {
    return createTestGroup(numUsers, new TestGroupConfig(false));
  }

  /**
   * Logs in as <code>admin</code> Creates a test Community , adds groups to the community and logs
   * out
   *
   * @param groups
   * @param admin An initialised admin user
   * @return
   */
  protected TestCommunity createTestCommunity(Set<TestGroup> groups, User admin) {
    TestCommunity rc = new TestCommunity(admin, groups);
    Community comm = createAndSaveCommunity(admin, getRandomAlphabeticString("comm"));
    logoutAndLoginAs(admin);
    for (TestGroup tg : groups) {
      comm = communityMgr.addGroupToCommunity(tg.getGroup().getId(), comm.getId(), admin);
    }
    rc.setCommunity(comm);
    RSpaceTestUtils.logout();
    return rc;
  }

  @Data
  public static class TestCommunity {
    private User admin;
    private Set<TestGroup> groups;
    private Community community;

    TestCommunity(User admin, Set<TestGroup> groups) {
      super();
      this.admin = admin;
      this.groups = groups;
    }
  }

  /**
   * Creates a LabGroup, with all entities saved to database and ids available. There are some
   * conventions:
   *
   * <ol>
   *   <li>All users will be created with unique names.
   *   <li>PI username will be prefixed with 'pi'
   *   <li>User usernames will be prefixed 'u1', 'u2' .. 'uN' where N is <code>numUsers</code>
   * </ol>
   *
   * Folder structure for each user is initialised, but no content is added.
   *
   * @param numUsers The number of users in the group, not including the PI
   * @param config A {@link TestGroupConfig} to refine the default group creation code
   * @return a {@link TestGroup} providing access to the created users and groups
   */
  protected TestGroup createTestGroup(int numUsers, TestGroupConfig config) {

    User pi = doCreateAndInitUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);

    TestGroup rc = new TestGroup(pi);
    for (int i = 1; i <= numUsers; i++) {
      User u = doCreateAndInitUser(getRandomAlphabeticString("u" + i));
      rc.addUser(u);
    }
    String labAdminUname = "";
    if (config.includeLabAdmin) {
      User u = doCreateAndInitUser(getRandomAlphabeticString(TestGroup.LABADMIN_PREFIX));
      rc.addUser(u);
      labAdminUname = u.getUsername();
    }
    User[] users = new User[rc.unameToUser.keySet().size()];
    Group labGroup =
        createGroupForUsers(
            rc.pi, pi.getUsername(), labAdminUname, rc.unameToUser.values().toArray(users));
    rc.group = labGroup;
    return rc;
  }

  /** Overridden by subclasses */
  protected abstract User doCreateAndInitUser(String username, String role);

  /** Overridden by subclasses */
  protected abstract User doCreateAndInitUser(String username);

  /**
   * Creates a new API key, assuming that 1 doesn't already exist.
   *
   * @param apiUser
   * @return
   */
  protected String createNewApiKeyForUser(User apiUser) {
    return userApiKeyMgr.createKeyForUser(apiUser).getApiKey();
  }

  protected String createNewSysAdminApiKey() {
    User sysadmin = userMgr.getUserByUsername(Constants.SYSADMIN_UNAME);
    return createNewApiKeyForUser(sysadmin);
  }

  protected abstract Group reloadGroup(Group group);

  /**
   * Adds the pi and any other users to the group. Other users have default Role i n group.
   *
   * @param pi
   * @param group
   * @param other
   * @return
   * @throws IllegalAddChildOperation
   */
  protected Group addUsersToGroup(User pi, Group group, User... other)
      throws IllegalAddChildOperation {
    for (User u : other) {
      grpMgr.addUserToGroup(u.getUsername(), group.getId(), RoleInGroup.DEFAULT);
    }

    grpMgr.addUserToGroup(pi.getUsername(), group.getId(), RoleInGroup.PI);
    grpMgr.createSharedCommunalGroupFolders(group.getId(), pi.getUsername());
    reloadGroup(group);
    return group;
  }

  /*
   * Create a sample with name, 1 subsample with quantity 5g
   */
  protected ApiSampleWithFullSubSamples createBasicSampleForUser(User user) {
    return createBasicSampleForUser(user, "mySample");
  }

  protected ApiSampleWithFullSubSamples createBasicSampleForUser(User user, String sampleName) {
    return createBasicSampleForUser(user, sampleName, "mySubSample", null);
  }

  protected ApiSampleWithFullSubSamples createBasicSampleForUserAndGroups(
      User user, String sampleName, List<Group> whitelistedGroups) {
    return createBasicSampleForUser(user, sampleName, "mySubSample", whitelistedGroups);
  }

  protected ApiSampleWithFullSubSamples createBasicSampleForUser(
      User user, String sampleName, String subSampleName, List<Group> whitelistedGroups) {
    ApiSubSample subSample = new ApiSubSample();
    subSample.setName(subSampleName);
    subSample.setQuantity(new ApiQuantityInfo(BigDecimal.valueOf(5), RSUnitDef.GRAM));
    return createBasicSampleWithSubsampleForUser(subSample, sampleName, whitelistedGroups, user);
  }

  /*
   * Create a sample with name, with a given subsample.
   */
  protected ApiSampleWithFullSubSamples createBasicSampleWithSubsampleForUser(
      ApiSubSample subsample, String sampleName, List<Group> whitelistedGroups, User user) {

    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName(sampleName);
    newSample.setSubSamples(List.of(subsample));
    setItemSharedModeToWhitelistWithGroups(newSample, whitelistedGroups, user);
    return sampleApiMgr.createNewApiSample(newSample, user);
  }

  protected void setItemSharedModeToWhitelistWithGroups(
      ApiInventoryRecordInfo invRec, List<Group> whitelistedGroups, User user) {
    if (whitelistedGroups != null) {
      invRec.setSharingMode(ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST);
      List<ApiGroupInfoWithSharedFlag> groupsToShareWith =
          whitelistedGroups.stream()
              .map(group -> ApiGroupInfoWithSharedFlag.forSharingWithGroup(group, user))
              .collect(Collectors.toList());
      invRec.setSharedWith(groupsToShareWith);
    }
  }

  protected List<ApiInventoryRecordInfo> createMultipleSamplesForUser(
      String sampleBaseName, int numberOfSamples, User user) {
    InventoryBulkOperationConfig bulkOpConfig =
        new InventoryBulkOperationConfig(
            BulkApiOperationType.CREATE, new ArrayList<>(), true, user);
    for (int i = 1; i <= numberOfSamples; i++) {
      String sampleName =
          SampleSeriesHelper2.getSerialNameForSample(sampleBaseName, i, numberOfSamples);
      bulkOpConfig.getRecords().add(new ApiSampleWithFullSubSamples(sampleName));
    }
    ApiInventoryBulkOperationResult sampleCreationResult =
        inventoryBulkOpApiMgr.runBulkOperation(bulkOpConfig);
    List<ApiInventoryRecordInfo> createdSamples =
        sampleCreationResult.getResults().stream()
            .map(result -> result.getRecord())
            .collect(Collectors.toList());
    return createdSamples;
  }

  protected ApiSampleWithFullSubSamples createComplexSampleForUser(User user) {

    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("myComplexSample");

    Sample complexTemplate = findComplexSampleTemplate(user);
    newSample.setTemplateId(complexTemplate.getId());
    newSample.setApiTagInfo("complexSampleTag");
    newSample.setDescription("complexSampleDescription");
    // add extra numeric to sample
    List<ApiExtraField> apiSampleExtraFields = new ArrayList<>();
    ApiExtraField extraNumeric = new ApiExtraField();
    extraNumeric.setType(ExtraFieldTypeEnum.NUMBER);
    extraNumeric.setContent("3.14");
    apiSampleExtraFields.add(extraNumeric);
    newSample.setExtraFields(apiSampleExtraFields);
    newSample.setBarcodes(List.of(new ApiBarcode("ABC123")));

    // add subsample with extra field and a note
    ApiSubSample subSample = new ApiSubSample();
    subSample.setName("mySubSample");
    ApiExtraField extraText = new ApiExtraField();
    extraText.setNewFieldRequest(true);
    extraText.setContent("any content");
    ApiSubSampleNote note = new ApiSubSampleNote("test note");
    subSample.setExtraFields(List.of(extraText));
    subSample.setNotes(List.of(note));
    subSample.setQuantity(new ApiQuantityInfo(BigDecimal.ONE, RSUnitDef.MILLI_LITRE));
    newSample.setSubSamples(List.of(subSample));
    newSample.setExpiryDate(LocalDate.now().plus(1, ChronoUnit.YEARS));

    return sampleApiMgr.createNewApiSample(newSample, user);
  }

  protected ApiSampleWithFullSubSamples createASubSampleWithTagValue(
      String tagValue, boolean isTemplate, User user) {
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("myComplexSample");
    newSample.setTemplate(isTemplate);
    ApiSubSample subSample = new ApiSubSample();
    subSample.setName("mySubSample");
    subSample.setApiTagInfo(tagValue);
    newSample.setSubSamples(List.of(subSample));
    return sampleApiMgr.createNewApiSample(newSample, user);
  }

  protected ApiSampleWithFullSubSamples createASampleWithTagValue(
      String tagValue, boolean isTemplate, User user) {
    ApiSampleWithFullSubSamples newSample = new ApiSampleWithFullSubSamples();
    newSample.setName("myComplexSample");
    newSample.setApiTagInfo(tagValue);
    newSample.setTemplate(isTemplate);
    return sampleApiMgr.createNewApiSample(newSample, user);
  }

  protected Sample findComplexSampleTemplate(User user) {
    return findSampleTemplateByName(
        user, ContentInitializerForDevRunManager.COMPLEX_SAMPLE_TEMPLATE_NAME);
  }

  protected Sample findBasicSampleTemplate(User user) {
    return findSampleTemplateByName(user, AbstractContentInitializer.DEFAULT_SAMPLE_TEMPLATE_NAME);
  }

  private Sample findSampleTemplateByName(User user, String templateName) {
    return sampleApiMgr.getAllTemplates(user).stream()
        .filter(t -> t.getName().equals(templateName))
        .findAny()
        .get();
  }

  protected ApiSampleTemplate createBasicSampleTemplate(User user) {
    // create template named "junit test template"
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("junit test template");
    sampleTemplatePost
        .getFields()
        .add(createBasicApiSampleField("text", ApiFieldType.TEXT, "text value"));

    return sampleApiMgr.createSampleTemplate(sampleTemplatePost, user);
  }

  protected ApiSampleWithFullSubSamples createBasicSampleTemplateAndSample(User user) {
    ApiSampleTemplate customTemplate = createBasicSampleTemplate(user);

    ApiSampleWithFullSubSamples sampleFromCustomTemplate = new ApiSampleWithFullSubSamples();
    sampleFromCustomTemplate.setName("sample from junit test template");
    sampleFromCustomTemplate.setTemplateId(customTemplate.getId());
    return sampleApiMgr.createNewApiSample(sampleFromCustomTemplate, user);
  }

  protected ApiSampleTemplate createSampleTemplateWithRadioAndNumericFields(User user) {

    // prepare new template with radio & numeric field
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("test template with radio and number");
    ApiSampleField radioField =
        createBasicApiSampleOptionsField("my radio", ApiFieldType.RADIO, List.of("r2"));
    ApiInventoryFieldDef radioDef = new ApiInventoryFieldDef(List.of("r1", "r2", "r3"), false);
    radioField.setDefinition(radioDef);
    sampleTemplatePost.getFields().add(radioField);
    ApiSampleField numberField =
        createBasicApiSampleField("my number", ApiFieldType.NUMBER, "3.14");
    sampleTemplatePost.getFields().add(numberField);

    return sampleApiMgr.createSampleTemplate(sampleTemplatePost, user);
  }

  /**
   * Saves new template with 3 text and radio fields: - 1st mandatory text with default value - 2nd
   * mandatory text without default value - 3rd non-mandatory text without default value - 4th
   * mandatory radio with default value - 5th mandatory radio without default value - 6th
   * non-mandatory radio without default value
   */
  protected ApiSampleTemplate createSampleTemplateWithMandatoryFields(User user) {
    ApiSampleTemplatePost sampleTemplatePost = new ApiSampleTemplatePost();
    sampleTemplatePost.setName("test template with mandatory text field");

    ApiSampleField textField =
        createBasicApiSampleField(
            "myText (mandatory - with default value)", ApiFieldType.TEXT, "default value");
    textField.setMandatory(true);
    sampleTemplatePost.getFields().add(textField);

    ApiSampleField textField2 =
        createBasicApiSampleField("myText (mandatory - no default value)", ApiFieldType.TEXT, "");
    textField2.setMandatory(true);
    sampleTemplatePost.getFields().add(textField2);

    ApiSampleField textField3 =
        createBasicApiSampleField("myText (not mandatory)", ApiFieldType.TEXT, "");
    sampleTemplatePost.getFields().add(textField3);

    ApiSampleField radioField =
        createBasicApiSampleOptionsField(
            "myRadio (mandatory - with default value)", ApiFieldType.RADIO, List.of("a"));
    radioField.setMandatory(true);
    ApiInventoryFieldDef radioDef = new ApiInventoryFieldDef(List.of("a", "b", "c"), false);
    radioField.setDefinition(radioDef);
    sampleTemplatePost.getFields().add(radioField);

    ApiSampleField radioField2 =
        createBasicApiSampleOptionsField(
            "myRadio (mandatory - no default value)", ApiFieldType.RADIO, null);
    radioField2.setMandatory(true);
    ApiInventoryFieldDef radioDef2 = new ApiInventoryFieldDef(List.of("a", "b", "c"), false);
    radioField2.setDefinition(radioDef2);
    sampleTemplatePost.getFields().add(radioField2);

    ApiSampleField radioField3 =
        createBasicApiSampleOptionsField("myRadio (not mandatory)", ApiFieldType.RADIO, null);
    ApiInventoryFieldDef radioDef3 = new ApiInventoryFieldDef(List.of("a", "b", "c"), false);
    radioField3.setDefinition(radioDef3);
    sampleTemplatePost.getFields().add(radioField3);

    return sampleApiMgr.createSampleTemplate(sampleTemplatePost, user);
  }

  /** Create empty list container (named "listContainer") in user's workbench. */
  protected ApiContainer createBasicContainerForUser(User user) {
    return createBasicContainerForUser(user, "listContainer");
  }

  /** Create empty list container with given name in user's workbench. */
  protected ApiContainer createBasicContainerForUser(User user, String name) {
    return createBasicContainerForUser(user, name, null);
  }

  protected ApiContainer createBasicContainerForUser(
      User user, String name, List<Group> whitelistedGroups) {
    ApiContainer newContainer = new ApiContainer(name, ContainerType.LIST);
    setItemSharedModeToWhitelistWithGroups(newContainer, whitelistedGroups, user);
    return containerApiMgr.createNewApiContainer(newContainer, user);
  }

  protected ApiContainer createBasicContainerForUserWithTag(
      User user, String containerName, String tag) {
    ApiContainer newContainer = new ApiContainer(containerName, ContainerType.LIST);
    newContainer.setApiTagInfo(tag);
    setItemSharedModeToWhitelistWithGroups(newContainer, null, user);
    return containerApiMgr.createNewApiContainer(newContainer, user);
  }

  /** Create empty grid container (named "gridContainer") in user's workbench. */
  protected ApiContainer createBasicGridContainerForUser(
      User user, int colsNumber, int rowsNumber) {
    ApiContainer newContainer = new ApiContainer();
    newContainer.setName("gridContainer");
    newContainer.setGridLayout(new ApiContainerGridLayoutConfig(colsNumber, rowsNumber));

    return containerApiMgr.createNewApiContainer(newContainer, user);
  }

  /** Create empty image container (named "imageContainer") in user's workbench. */
  protected ApiContainer createBasicImageContainerForUser(User user) {
    ApiContainer newContainer = new ApiContainer("imageContainer", ContainerType.IMAGE);
    String base64Png =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAQIAAAESAQMAAAAsV0mIAAAAAXNSR0IArs4c6QAAAARn"
            + "QU1BAACxjwv8YQUAAAAGUExURf///wAAAFXC034AAAAJcEhZcwAADsMAAA7DAcdvqGQAAABWSURBVGje7dUhDsAgEETR5VYcv8f"
            + "CgUEg1rXdhOR9/ZKRE5LO2kwbH4snHe8EQRAEQWxR88g+myAIgiDeCo9MEARBEP+Lmr/1yARBEARxhyj5fSktYgFPS1k85TqeJQ"
            + "AAAABJRU5ErkJggg==";
    newContainer.setNewBase64LocationsImage(base64Png);

    // add two locations
    ApiContainerLocationWithContent newLocation1 = new ApiContainerLocationWithContent(15, 25);
    ApiContainerLocationWithContent newLocation2 = new ApiContainerLocationWithContent(25, 35);
    newContainer.setLocations(List.of(newLocation1, newLocation2));

    return containerApiMgr.createNewApiContainer(newContainer, user);
  }

  protected ApiSampleField createBasicApiSampleField(
      String name, ApiFieldType type, String content) {
    ApiSampleField field = new ApiSampleField();
    field.setName(name);
    field.setType(type);
    field.setContent(content);
    return field;
  }

  protected ApiSampleField createBasicApiSampleOptionsField(
      String name, ApiFieldType type, List<String> selectedOptions) {
    ApiSampleField field = new ApiSampleField();
    field.setName(name);
    field.setType(type);
    field.setSelectedOptions(selectedOptions);
    return field;
  }

  /** Moves container outside of current parent container. */
  protected void moveContainerToTopLevel(ApiContainer container, User user) {
    containerApiMgr.moveContainerToTopLevel(container.getId(), user);
  }

  protected ApiSubSample moveSubSampleIntoListContainer(
      Long subSampleId, Long containerId, User user) {
    ApiSubSample updateRequest = new ApiSubSample();
    updateRequest.setId(subSampleId);
    ApiContainerInfo targetContainer = new ApiContainerInfo();
    targetContainer.setId(containerId);
    updateRequest.setParentContainer(targetContainer);
    return subSampleApiMgr.updateApiSubSample(updateRequest, user);
  }

  protected ApiContainer moveContainerIntoListContainer(
      Long containerId, Long targetContainerId, User user) {
    ApiContainer updateRequest = new ApiContainer();
    updateRequest.setId(containerId);
    ApiContainerInfo targetContainer = new ApiContainerInfo();
    targetContainer.setId(targetContainerId);
    updateRequest.setParentContainer(targetContainer);
    return containerApiMgr.updateApiContainer(updateRequest, user);
  }

  protected ApiContainer getWorkbenchForUser(User user) {
    return containerApiMgr.getApiWorkbenchById(containerApiMgr.getWorkbenchIdForUser(user), user);
  }

  protected ApiListOfMaterials createBasicListOfMaterialsForUserAndDocField(
      User user, Field field, List<ApiMaterialUsage> materials) {
    ApiListOfMaterials newLom = new ApiListOfMaterials();
    newLom.setName("basic list of materials");
    newLom.setElnFieldId(field.getId());
    if (materials != null) {
      newLom.setMaterials(materials);
    }
    return listOfMaterialsApiMgr.createNewListOfMaterials(newLom, user);
  }

  protected ApiBasket createBasicBasketWithItems(List<String> globalIds, User user) {
    return basketApiMgr.createNewBasket("basic basket", globalIds, user);
  }

  protected InventoryFile addFileAttachmentToInventoryItem(
      GlobalIdentifier globalIdToAttachTo, User user) throws IOException {
    InventoryFile attachedFile;
    try (InputStream fileIS =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Picture1.png")) {
      attachedFile =
          inventoryFileApiMgr.attachNewInventoryFileToInventoryRecord(
              globalIdToAttachTo, "Picture1.png", fileIS, user);
    }
    return attachedFile;
  }

  protected InventoryFile addGalleryFileToInventoryItem(
      GlobalIdentifier globalIdToAttachTo, User user) throws IOException {
    EcatImage ecatImage = addImageToGallery(user);
    return inventoryFileApiMgr.attachGalleryFileToInventoryRecord(
        globalIdToAttachTo, ecatImage.getOid(), user);
  }

  /**
   * Gets total of table rows in DB
   *
   * @return
   * @throws Exception
   */
  protected Long getCountOfEntityTable(String table) throws Exception {
    return sessionFactory
        .getCurrentSession()
        .createQuery("select count(*) from " + table, Long.class)
        .uniqueResult();
  }

  protected MockMultipartFile createAnyMultipartFile() {
    return new MockMultipartFile(
        "file", "afile.dat", "application/octet-stream", new byte[] {1, 2, 3, 4, 5});
  }

  protected MockMultipartFile getChemicalMockFile() throws IOException {
    return new MockMultipartFile(
        "file",
        "Amfetamine.mol",
        "chemical/x-mdl-molfile",
        getTestResourceFileStream("Amfetamine.mol"));
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
}
