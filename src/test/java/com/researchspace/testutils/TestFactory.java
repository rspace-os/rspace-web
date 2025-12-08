package com.researchspace.testutils;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang.RandomStringUtils.randomAlphabetic;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.core.util.progress.ProgressMonitorImpl;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.Community;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatComment;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.Group;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.Signature;
import com.researchspace.model.Thumbnail;
import com.researchspace.model.Thumbnail.SourceType;
import com.researchspace.model.User;
import com.researchspace.model.comms.CommunicationStatus;
import com.researchspace.model.comms.CommunicationTarget;
import com.researchspace.model.comms.GroupMessageOrRequest;
import com.researchspace.model.comms.MessageOrRequest;
import com.researchspace.model.comms.MessageType;
import com.researchspace.model.comms.Notification;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.inventory.Barcode;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraNumberField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.oauth.OAuthToken;
import com.researchspace.model.oauth.OAuthTokenType;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IActiveUserStrategy;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.model.record.Snippet;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.commons.io.IOUtils;

/** Utility class for creating test fixtures . */
public class TestFactory {
  static RecordFactory rf = new RecordFactory();

  static {
    rf.setModifiedByStrategy(IActiveUserStrategy.NULL);
  }

  private static final String OLD_NAME = "oldName";

  /**
   * Creates a structured document with a single text field.
   *
   * @return
   */
  public static StructuredDocument createAnySD() {
    return createAnySD(createAnyForm());
  }

  /**
   * Creates a structured document with a single text field, to which the text is added.
   *
   * @return
   */
  public static StructuredDocument createAnySDWithText(String fieldText) {
    StructuredDocument doc = createAnySD();
    doc.getFields().get(0).setFieldData(fieldText);
    return doc;
  }

  /**
   * Creates a {@link StructuredDocument} based on the supplied Form, with a newly created user
   *
   * @param form
   * @return
   */
  public static StructuredDocument createAnySD(RSForm form) {
    return createAnySDForUser(form, createAnyUser("user"));
  }

  /**
   * Creates a {@link StructuredDocument} with a single text field
   *
   * @param form
   * @return
   */
  public static StructuredDocument createAnySDForUser(RSForm form, User owner) {
    return rf.createStructuredDocument(OLD_NAME, owner, form);
  }

  public static Snippet createAnySnippet(User owner) {
    return rf.createSnippet("name", "content", owner);
  }

  /**
   * Creates a group and populates it with users
   *
   * @param pi The group PI
   * @param others optionally, Any other group members.
   * @return
   */
  public static Group createAnyGroup(User pi, User... others) {
    Group group = new Group(CoreTestUtils.getRandomName(5), pi);
    group.setDisplayName("display" + CoreTestUtils.getRandomName(3));
    group.setProfileText(" Some information about the group");
    group.addMember(pi, RoleInGroup.PI);
    if (others == null) {
      return group;
    }
    for (User u : others) {
      group.addMember(u, RoleInGroup.DEFAULT);
    }
    return group;
  }

  public static RSForm createAnyForm() {
    return createAnyForm("name");
  }

  /**
   * Creates a form of given type with a single text field and a randomly created owner, of a .
   *
   * @param name the form's name
   * @return
   */
  public static RSForm createAnyForm(String name) {
    RSForm form = new RSForm(name, "desc", createAnyUser("user"));
    form.setCurrent(true);
    form.addFieldForm(createTextFieldForm());
    return form;
  }

  public static Folder createAFolder(String name, User owner) {
    // ensures unique equals/hashcode which uses creation time
    try {
      Thread.sleep(2);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    Folder rtd = new Folder();
    rtd.setId(new Random().nextLong());
    rtd.setCreatedBy(owner.getUsername());
    rtd.setOwner(owner);
    rtd.setDescription("desc");

    rtd.setName(name);
    rtd.addType(RecordType.FOLDER);
    rtd.setModifiedBy(owner.getUsername());
    return rtd;
  }

  public static Folder createASystemFolder(String name, User owner) {
    sleep1();
    return rf.createSystemCreatedFolder(name, owner);
  }

  public static Folder createAnImportsFolder(User owner) {
    sleep1();
    return rf.createImportsFolder(owner);
  }

  private static void sleep1() {
    try {
      Thread.sleep(1);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Creates a templateFolder where isTemplate()== true
   *
   * @param owner
   * @return
   */
  public static Folder createTemplateFolder(User owner) {
    sleep1();
    Folder rc = rf.createSystemCreatedFolder(Folder.TEMPLATE_MEDIA_FOLDER_NAME, owner);
    rc.addType(RecordType.TEMPLATE);
    return rc;
  }

  public static Notebook createANotebook(String name, User owner) {
    Notebook rtd = new Notebook();
    rtd.setId(new Random().nextLong());
    rtd.setCreatedBy(owner.getUsername());
    rtd.setOwner(owner);
    rtd.setDescription("desc");

    rtd.setName(name);
    rtd.addType(RecordType.NOTEBOOK);
    rtd.setModifiedBy(owner.getUsername());
    return rtd;
  }

  public static Notebook createANotebookWithNEntries(String name, User owner, int numEnties) {
    Notebook rtd = createANotebook(name, owner);
    IntStream.rangeClosed(1, numEnties)
        .forEach(
            (i) -> {
              rtd.addChild(TestFactory.createAnySD(), owner, true);
            });
    return rtd;
  }

  /**
   * Creates a field of any type. Does not set an ID
   *
   * @return
   */
  public static Field createAnyField() {
    Field nf = createDateFieldForm().createNewFieldFromForm();
    nf.setFieldData("1970-05-05");
    // nf.setId(5L);
    return nf;
  }

  public static DateFieldForm createDateFieldForm() {
    DateFieldForm dft = new DateFieldForm();
    dft.setFormat("yyyy-MM-dd");
    dft.setMinValue(5000000); // 01 Jan 1970
    dft.setMaxValue(10000000000000L); // 20 Nov 2286
    dft.setDefaultDate(100000000); // 02 Jan 1970
    dft.setName("datefield");
    return dft;
  }

  /**
   * Creates a transient user with the given username and hashed 'testpass' password.
   *
   * @param uname username
   * @return The created user.
   */
  public static User createAnyUser(String uname) {
    User user = createAnyUserWithPlainTextPassword(uname);

    // hash password fields so test user can be used in shiro login flow
    String testPassSha256 = SecureStringUtils.getHashForSigning(user.getPassword()).toString();
    user.setPassword(testPassSha256);
    user.setConfirmPassword(testPassSha256);

    return user;
  }

  /**
   * Create a transient user with given username and plaintext 'testpass' password. To be used for
   * user-creation test requests, which don't expect hashed password.
   *
   * @param uname username
   * @return the created user.
   */
  public static User createAnyUserWithPlainTextPassword(String uname) {
    User u = new User(uname);
    u.setFirstName("first");
    u.setLastName("last");
    u.setPassword("testpass");
    u.setConfirmPassword("testpass");
    u.setEmail(uname + "@b");
    return u;
  }

  /**
   * Creates n users; usernames will be prefix + 0-based index
   *
   * @param numUsers
   * @param unamePrefix
   * @return A List of n users
   */
  public static List<User> createNUsers(int numUsers, String unamePrefix) {
    return IntStream.range(0, numUsers)
        .mapToObj(i -> "user" + i)
        .map(TestFactory::createAnyUser)
        .collect(toList());
  }

  /**
   * Creates user with specified role
   *
   * @param uname
   * @param roleName
   * @return
   */
  public static User createAnyUserWithRole(String uname, String roleName) {
    User u = createAnyUser(uname);
    Role role = new Role(roleName);
    u.addRole(role);
    Set<String> perms =
        TransformerUtils.toSet(
            "FORM:READ:property_global=true",
            "COMMS:READ:property_name=SIMPLEMESSAGE",
            "COMMS:READ:property_name=REQUESTRECORDREVIEW",
            "FORM:CREATE,READ,WRITE,SHARE,DELETE:property_owner=${self}",
            "FORM:CREATE");
    ConstraintPermissionResolver parser = new ConstraintPermissionResolver();
    for (String perm : perms) {
      role.addPermission(parser.resolvePermission(perm));
    }
    return u;
  }

  public static Record createAnyRecord(User user) {
    return rf.createAnyRecord(user, CoreTestUtils.getRandomName(10));
  }

  public static EcatImage createEcatImage(long id) {
    EcatImage ecatImage = new EcatImage();
    ecatImage.setId(id);
    ecatImage.setFileName("image.png");
    ecatImage.setContentType("image/png");
    ecatImage.setExtension("png");
    ecatImage.setName("anImage");
    ecatImage.setModifiedBy("someone");
    ecatImage.setCreatedBy("someone");

    return ecatImage;
  }

  public static EcatVideo createEcatVideo(long id) {
    EcatVideo ecatVideo = new EcatVideo();
    ecatVideo.setId(id);
    ecatVideo.setFileName("video.mp4");
    ecatVideo.setContentType("video/mp4");
    ecatVideo.setExtension("mp4");
    ecatVideo.setName(ecatVideo.getFileName());
    return ecatVideo;
  }

  /**
   * Creates an example EcatAudio file
   *
   * @param id
   * @param anyuser
   * @return
   */
  public static EcatAudio createEcatAudio(long id, User anyuser) {
    EcatAudio ecatAudio = new EcatAudio();
    ecatAudio.setName("any");
    ecatAudio.setOwner(anyuser);
    ecatAudio.setCreatedBy(anyuser.getUsername());
    ecatAudio.setModifiedBy(anyuser.getUsername());
    ecatAudio.setId(id);

    ecatAudio.setFileName("audio.mp3");
    ecatAudio.setContentType("audio/mp3");
    ecatAudio.setExtension("mp3");
    return ecatAudio;
  }

  public static EcatDocumentFile createEcatDocument(long id, User user) {
    EcatDocumentFile ecatDocumentFile = new EcatDocumentFile();
    ecatDocumentFile.setId(id);
    ecatDocumentFile.setFileName("document.doc");
    ecatDocumentFile.setContentType("application/msword");
    ecatDocumentFile.setExtension("doc");
    ecatDocumentFile.setCreatedBy(user.getUsername());
    ecatDocumentFile.setOwner(user);
    ecatDocumentFile.setModifiedBy(user.getUsername());
    ecatDocumentFile.setName("documentDisplayName.doc");
    return ecatDocumentFile;
  }

  /**
   * Creates a fileproperty ready for saving to the filestore
   *
   * @param actualFile
   * @param owner
   * @param fileRoot
   * @return
   */
  public static FileProperty createAFileProperty(
      File actualFile, User owner, FileStoreRoot fileRoot) {
    FileProperty fp = new FileProperty();
    fp.setRoot(fileRoot);
    fp.setFileName(actualFile.getName());
    fp.setFileUser(owner.getUsername());
    fp.setRelPath("/a/b/c");
    return fp;
  }

  public static EcatImageAnnotation createImageAnnoatation(long parentId, long id) {
    EcatImageAnnotation ecatImageAnnotation = new EcatImageAnnotation();
    ecatImageAnnotation.setParentId(parentId);
    ecatImageAnnotation.setId(id);
    ecatImageAnnotation.setAnnotations("JSON OBJECT WITH ANNOTATIONS");
    return ecatImageAnnotation;
  }

  public static EcatImageAnnotation createSketch(long parentId, long id) {
    EcatImageAnnotation ecatImageAnnotation = new EcatImageAnnotation();
    ecatImageAnnotation.setParentId(parentId);
    ecatImageAnnotation.setId(id);
    ecatImageAnnotation.setAnnotations("JSON OBJECT WITH ANNOTATIONS");
    return ecatImageAnnotation;
  }

  /**
   * Creates a transient chem element with a default chemical string.
   *
   * @param parentId the field id
   * @param id the id that this
   * @return
   * @throws IOException
   */
  public static RSChemElement createChemElement(Long parentId, long id) throws IOException {
    RSChemElement rsChemElement = new RSChemElement();
    rsChemElement.setParentId(parentId);
    rsChemElement.setId(id);
    rsChemElement.setChemId(1);
    rsChemElement.setChemElementsFormat(ChemElementsFormat.MOL);

    InputStream molInput = TestFactory.class.getResourceAsStream("/TestResources/Amfetamine.mol");
    String chemElementMolString = IOUtils.toString(molInput, StandardCharsets.UTF_8);
    molInput.close();

    rsChemElement.setChemElements(chemElementMolString);
    return rsChemElement;
  }

  /**
   * Creates a new {@link EcatComment} linked to the parentId
   *
   * @param parentId
   * @param id
   * @return
   */
  public static EcatComment createEcatComment(long parentId, Record record, long id) {
    EcatComment ecatComment = new EcatComment();
    ecatComment.setParentId(parentId);
    ecatComment.setRecord(record);
    ecatComment.setComId(id);
    return ecatComment;
  }

  public static ArchivalCheckSum createAnArchivalChecksum() {
    ArchivalCheckSum csum = new ArchivalCheckSum();
    csum.setArchivalDate(new Date().getTime());
    csum.setUid(CoreTestUtils.getRandomName(10));
    csum.setZipName("archive" + ".zip");
    csum.setZipSize(12345);
    csum.setCheckSum(19876);
    return csum;
  }

  /**
   * Creates a Message of type SimpleMessage
   *
   * @param originator
   * @return
   */
  public static MessageOrRequest createAnyMessage(User originator) {
    MessageOrRequest mor = new MessageOrRequest(MessageType.SIMPLE_MESSAGE);
    mor.setOriginator(originator);
    mor.setStatus(CommunicationStatus.NEW);
    return mor;
  }

  /**
   * Creates a Message of type SimpleMessage with a single CommunicationTarget recipient.
   *
   * @param originator
   * @param recipient
   * @return
   */
  public static MessageOrRequest createAnyMessageForRecipuent(User originator, User recipient) {
    return createAnyMessageForRecipientOfType(originator, recipient, MessageType.SIMPLE_MESSAGE);
  }

  /**
   * Creates a Message of specified type with a single CommunicationTarget recipient.
   *
   * @param originator
   * @param recipient
   * @param type
   * @return
   */
  public static MessageOrRequest createAnyMessageForRecipientOfType(
      User originator, User recipient, MessageType type) {
    MessageOrRequest mor = new MessageOrRequest(type);
    mor.setOriginator(originator);
    mor.setStatus(CommunicationStatus.NEW);
    CommunicationTarget ct = new CommunicationTarget();
    ct.setRecipient(recipient);
    mor.addRecipient(ct);
    return mor;
  }

  /**
   * Creates a Request of some type for a record
   *
   * @param originator
   * @return
   */
  public static MessageOrRequest createAnyRequest(User originator, Record r) {
    MessageOrRequest mor = new MessageOrRequest(MessageType.REQUEST_RECORD_REVIEW);
    mor.setOriginator(originator);
    mor.setStatus(CommunicationStatus.NEW);
    mor.setRecord(r);
    return mor;
  }

  public static GroupMessageOrRequest createAnyGroupRequest(User originator, Record r, Group g) {
    GroupMessageOrRequest mor = new GroupMessageOrRequest(MessageType.REQUEST_JOIN_LAB_GROUP);
    mor.setGroup(g);
    mor.setId(999L);
    mor.getGroup().setDisplayName("GroupName");
    mor.setOriginator(originator);
    mor.setStatus(CommunicationStatus.NEW);
    mor.setRecord(r);
    return mor;
  }

  /**
   * Creates a root folder and document owned by the same user with dummy ids. This is for use by
   * non-database tests that need to work with a document with its associations already set up. <br>
   * A User is also created by this method, who is the owner of the folder and document.
   *
   * @return
   */
  public static StructuredDocument createWiredFolderAndDocument() {
    StructuredDocument sd = TestFactory.createAnySD();
    Folder root = TestFactory.createAFolder("root", sd.getOwner());
    root.setId(2L);
    root.addType(RecordType.ROOT);
    root.addChild(sd, sd.getOwner(), true);
    sd.getOwner().setRootFolder(root);

    sd.getFields().get(0).setFieldData("data");
    sd.getFields().get(0).setId(3L);
    sd.setId(1L);
    return sd;
  }

  /*
   * Creates a notification message
   */
  public static Notification createANotification(User sender) {
    Notification not = new Notification();
    not.setMessage("a message");
    not.setNotificationMessage(" a notification message");
    not.setNotificationType(NotificationType.NOTIFICATION_DOCUMENT_EDITED);
    not.setOriginator(sender);
    return not;
  }

  /**
   * Creates a textfield form with
   *
   * @return
   */
  public static TextFieldForm createTextFieldForm() {
    TextFieldForm sft = new TextFieldForm();
    sft.setName("text");
    sft.setDefaultValue("x");
    return sft;
  }

  /** creates a thumnnail with all fields id and parent ID, and a real image set in its ImageBlob */
  public static Thumbnail createThumbnail(int width, int height) throws IOException {
    InputStream inputStream = RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("tester.png");
    byte[] imageBytes = IOUtils.toByteArray(inputStream);

    Thumbnail thumbnail1 = new Thumbnail();
    thumbnail1.setSourceId(1l);
    thumbnail1.setSourceType(SourceType.IMAGE);
    thumbnail1.setHeight(height);
    thumbnail1.setWidth(width);
    thumbnail1.setRotation((byte) 1);
    ImageBlob blob1 = new ImageBlob(imageBytes);
    thumbnail1.setImageBlob(blob1);
    return thumbnail1;
  }

  public static ProgressMonitor createAProgressMonitor(int totalWork, String desc) {
    return new ProgressMonitorImpl(totalWork, desc);
  }

  /**
   * Create a Community with names and profile text set, but no ID or admin IDs
   *
   * @return A Community
   */
  public static Community createACommunity() {
    Community comm = new Community();
    comm.setDisplayName("A display name");
    comm.setUniqueName(randomAlphabetic(10));
    comm.setProfileText("Some profile text");
    return comm;
  }

  /**
   * Creates a Signature object, setting in associations to the documetn and the signer, but not the
   * ID
   *
   * @param doc
   * @param any
   * @return
   */
  public static Signature createASignature(StructuredDocument doc, User any) {
    Signature sig = new Signature();
    sig.setRecordSigned(doc);
    sig.setSigner(any);
    sig.setSignatureDate(new Date());
    sig.setStatement("I have signed this");
    return sig;
  }

  /**
   * Creates a populated {@link FileProperty} with a dummy {@link FileStoreRoot} object and backed
   * by a temporary file.
   *
   * @param owner
   * @return
   * @throws IOException
   */
  public static FileProperty createAnyTransientFileProperty(User owner) throws IOException {
    FileStoreRoot fsRoot = new FileStoreRoot("/some/path");
    FileProperty fp = createAFileProperty(File.createTempFile("any", ".txt"), owner, fsRoot);
    return fp;
  }

  /**
   * Creates a RSMath object with a real SVG content for 'x^2'. ID is not set.
   *
   * @return
   * @throws IOException
   */
  public static RSMath createAMathElement() throws IOException {
    RSMath math = new RSMath();
    byte[] data = RSpaceTestUtils.getResourceAsByteArray("mathEquation.svg");
    math.setLatex("x^2");
    math.setMathSvg(new ImageBlob(data));
    return math;
  }

  /**
   * Creates a UserConnection object with all fields populated, non-null
   *
   * @return
   */
  public static UserConnection createUserConnection(String username) {
    UserConnectionId id = new UserConnectionId(username, "provider", "providerUserId");
    UserConnection conn = new UserConnection(id, "accessToken");
    conn.setDisplayName("Display name");
    conn.setExpireTime(-1L);
    conn.setImageUrl("http://some.image");
    conn.setProfileUrl("http://some.profile");
    conn.setRefreshToken("refresh");
    conn.setSecret("secret");
    return conn;
  }

  /**
   * Creates an OAuth token with access and refresh tokens set
   *
   * @param anyUser
   * @return
   */
  public static OAuthToken createOAuthTokenForUI(User anyUser) {
    OAuthToken token = new OAuthToken(anyUser, "clientId", OAuthTokenType.UI_TOKEN);
    token.setHashedRefreshToken("refreshToken");
    return token;
  }

  /**
   * Creates numRecords records with an owner.
   *
   * @param numRecords
   * @return
   */
  public static List<BaseRecord> createNRecords(int numRecords) {
    User any = createAnyUser("anyUser");
    return IntStream.range(0, numRecords).mapToObj(i -> createAnyRecord(any)).collect(toList());
  }

  public static Sample createBasicSampleInContainer(User user) {
    Container cont = rf.createListContainer("test", user);
    Sample sample = createBasicSampleOutsideContainer(user);
    sample.getSubSamples().get(0).moveToNewParent(cont);
    return sample;
  }

  public static Sample createBasicSampleOutsideContainer(User user) {
    return rf.createSample("test sample", user);
  }

  public static Sample createComplexSampleInContainer(User user) {
    Container cont = rf.createListContainer("test", user);
    Sample sampleTemplate = rf.createComplexSampleTemplate("complex template", "for tests", user);
    Sample sample = rf.createSample("test complex sample", user, sampleTemplate);
    sample.getSubSamples().get(0).moveToNewParent(cont);
    return sample;
  }

  public static Sample createBasicSampleWithSubSamples(User user, int numSubSamples) {
    Sample sample = createBasicSampleInContainer(user);
    sample.setSubSamples(createBasicSubSamples(user, sample, numSubSamples));
    return sample;
  }

  /**
   * Creates a sample with a fully populated first subsample with fileproperties, extra fields and
   * notes added
   *
   * @param user
   * @param numSubSamples
   * @return
   * @throws IOException
   */
  public static Sample createSampleWithSubSamplesAndEverything(User user, int numSubSamples)
      throws IOException {
    Sample sample = createBasicSampleInContainer(user);
    sample.addExtraField(TestFactory.createExtraNumberField("enf1", user, sample));
    sample.addBarcode(new Barcode("testData", user.getUsername()));
    FileProperty f1 = createAnyTransientFileProperty(user);
    FileProperty f2 = createAnyTransientFileProperty(user);
    sample.setSampleSource(SampleSource.OTHER);
    sample.setImageFileProperty(f1);
    sample.setThumbnailFileProperty(f2);
    sample.setStorageTempMax(QuantityInfo.of(BigDecimal.TEN, RSUnitDef.CELSIUS));
    sample.setStorageTempMin(QuantityInfo.of(BigDecimal.ONE, RSUnitDef.CELSIUS));
    sample.setSubSamples(createBasicSubSamples(user, sample, numSubSamples));
    FileProperty f3 = createAnyTransientFileProperty(user);
    InventoryFile invFile = new InventoryFile("testFileName.txt", f3);
    sample.addAttachedFile(invFile);
    DigitalObjectIdentifier igsnIdentifier =
        new DigitalObjectIdentifier("testIGSN", "testIGSN title");
    igsnIdentifier.addOtherData(
        DigitalObjectIdentifier.IdentifierOtherProperty.CREATOR_NAME, "testCreator");
    igsnIdentifier.addOtherListData(
        DigitalObjectIdentifier.IdentifierOtherListProperty.SUBJECTS,
        List.of("subject1", "subject2"));
    sample.addIdentifier(igsnIdentifier);

    SubSample subSample = sample.getActiveSubSamples().get(0);
    subSample.addBarcode(new Barcode("12345", user.getUsername()));
    subSample.setIconId(2L);
    subSample.setDescription("desc");
    subSample.setTags("abc,def");

    subSample.setImageFileProperty(f1);
    subSample.setThumbnailFileProperty(f2);
    subSample.addExtraField(TestFactory.createExtraNumberField("enf1", user, subSample));
    subSample.addExtraField(TestFactory.createExtraTextField("text1", user, subSample));
    subSample.addNote("note1", user);
    subSample.addNote("note2", user);
    subSample.setQuantity(QuantityInfo.of(BigDecimal.TEN, RSUnitDef.GRAM));
    return sample;
  }

  private static List<SubSample> createBasicSubSamples(
      User user, Sample sample, int numSubSamples) {
    List<SubSample> result = new ArrayList<>();
    Container container = sample.getSubSamples().get(0).getParentContainer();
    for (int i = 0; i < numSubSamples; i++) {
      SubSample newSubSample = rf.createSubSample("test subSample", user, sample);
      newSubSample.moveToNewParent(container);
      result.add(newSubSample);
    }
    return result;
  }

  public static ExtraNumberField createExtraNumberField(
      String name, User createdBy, InventoryRecord invRec) {
    return (ExtraNumberField) rf.createExtraField(name, FieldType.NUMBER, createdBy, invRec);
  }

  public static ExtraTextField createExtraTextField(
      String name, User createdBy, InventoryRecord invRec) {
    return (ExtraTextField) rf.createExtraField(name, FieldType.TEXT, createdBy, invRec);
  }

  /**
   * Creates an empty List container with metadata and images, that can store containers and
   * subsamples.
   *
   * @param owner
   * @return
   * @throws IOException
   */
  public static Container createListContainer(User owner) throws IOException {
    Container listContainer = Container.createListContainer(true, true);
    setContainerData(owner, listContainer);
    return listContainer;
  }

  /**
   * Creates an empty Grid container of required dimensions with metadata and images.
   *
   * @param owner
   * @return
   * @throws IOException
   */
  public static Container createGridContainer(User owner, int cols, int rows) throws IOException {
    Container gridContainer = Container.createGridContainer(cols, rows, true, true);
    setContainerData(owner, gridContainer);
    return gridContainer;
  }

  /**
   * Creates an empty Image container with 2 predefined locations and all image fields set.
   *
   * @param owner
   * @return
   * @throws IOException
   */
  public static Container createImageContainer(User owner) throws IOException {
    Container imageContainer = Container.createImageContainer(true, true);
    setContainerData(owner, imageContainer);
    imageContainer.createNewImageContainerLocation(3, 4);
    imageContainer.createNewImageContainerLocation(8, 10);
    FileProperty f1 = createAnyTransientFileProperty(owner);
    imageContainer.setLocationsImageFileProperty(f1);
    return imageContainer;
  }

  public static Container createWorkbench(User owner) {
    return rf.createWorkbench(owner);
  }

  private static void setContainerData(User owner, Container container) throws IOException {
    container.setName("c2");
    container.setTags("tag1,tag2");
    container.setDescription("d1");
    container.setOwner(owner);
    container.setCreatedBy(owner.getUsername());
    container.setModifiedBy(owner.getUsername());
    container.addBarcode(new Barcode("barcode", owner.getUsername()));
    FileProperty f1 = createAnyTransientFileProperty(owner);
    FileProperty f2 = createAnyTransientFileProperty(owner);
    container.setImageFileProperty(f1);
    container.setThumbnailFileProperty(f2);
  }
}
