package com.researchspace.model.record;

import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.SecureStringUtils;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.Group;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.RSMath;
import com.researchspace.model.RSpaceModelTestUtils;
import com.researchspace.model.Role;
import com.researchspace.model.RoleInGroup;
import com.researchspace.model.Thumbnail;
import com.researchspace.model.Thumbnail.SourceType;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.field.DateFieldForm;
import com.researchspace.model.field.Field;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.inventory.Barcode;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.inventory.SampleTemplate;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraNumberField;
import com.researchspace.model.inventory.field.ExtraTextField;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.model.permissions.ConstraintPermissionResolver;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
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

  public static FileStoreRoot createAnyFileStoreRoot() {
    return new FileStoreRoot("file:/path/to/root/file_store/");
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

  public static Folder createAnAPiInboxFolder(User owner) {
    sleep1();
    return rf.createApiInboxFolder(owner);
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
    InputStream inputStream =
        RSpaceModelTestUtils.getInputStreamOnFromTestResourcesFolder("tester-core-model.png");
    byte[] imageBytes = IOUtils.toByteArray(inputStream);

    Thumbnail thumbnail1 = new Thumbnail();
    thumbnail1.setSourceId(1L);
    thumbnail1.setSourceType(SourceType.IMAGE);
    thumbnail1.setHeight(height);
    thumbnail1.setWidth(width);
    thumbnail1.setRotation((byte) 1);
    ImageBlob blob1 = new ImageBlob(imageBytes);
    thumbnail1.setImageBlob(blob1);
    return thumbnail1;
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
    return createAFileProperty(File.createTempFile("any", ".txt"), owner, fsRoot);
  }

  /**
   * Creates a RSMath object with a real SVG content for 'x^2'. ID is not set.
   *
   * @return
   * @throws IOException
   */
  public static RSMath createAMathElement() throws IOException {
    RSMath math = new RSMath();
    byte[] data = RSpaceModelTestUtils.getResourceAsByteArray("mathEquation.svg");
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

  public static Sample createBasicSampleInContainer(User user) {
    Container cont = rf.createListContainer("test", user);
    Sample sample = createBasicSampleOutsideContainer(user);
    sample.getSubSamples().get(0).moveToNewParent(cont);
    return sample;
  }

  public static Sample createBasicSampleOutsideContainer(User user) {
    return rf.createSample("test sample", user);
  }

  public static Instrument createBasicInstrumentInContainer(User user) {
    Container cont = rf.createListContainer("test", user);
    Instrument instrument = createBasicInstrumentOutsideContainer(user);
    instrument.moveToNewParent(cont);
    return instrument;
  }

  public static Instrument createBasicInstrumentOutsideContainer(User user) {
    return createInstrument(user);
  }

  public static Instrument createInstrument(User user) {
    return rf.createInstrument("test instrument", user, null);
  }

  public static Instrument createInstrumentFromTemplate(User user, InstrumentTemplate template) {
    if (template == null) {
      return createInstrument(user);
    }
    return (Instrument) template.copyFromTemplate(user);
  }

  public static Sample createComplexSampleInContainer(User user) {
    Container cont = rf.createListContainer("test", user);
    SampleTemplate sampleTemplate =
        rf.createComplexSampleTemplate("complex template", "for tests", user);
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
    Container listContainer = Container.createListContainer(true, true, true);
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
    Container gridContainer = Container.createGridContainer(cols, rows, true, true, true);
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
    Container imageContainer = Container.createImageContainer(true, true, true);
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
