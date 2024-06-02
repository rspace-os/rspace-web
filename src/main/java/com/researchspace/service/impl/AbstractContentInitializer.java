package com.researchspace.service.impl;

import com.researchspace.dao.FolderDao;
import com.researchspace.dao.FormDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.SampleDao;
import com.researchspace.dao.UserDao;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatImage;
import com.researchspace.model.User;
import com.researchspace.model.core.UniquelyIdentifiable;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.record.IconEntity;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.FormManager;
import com.researchspace.service.IContentInitialiserUtils;
import com.researchspace.service.IContentInitializer;
import com.researchspace.service.IconImageManager;
import com.researchspace.service.InitializedContent;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserFolderCreator;
import com.researchspace.service.UserFolderSetup;
import com.researchspace.service.inventory.ContainerApiManager;
import com.researchspace.service.inventory.SampleApiManager;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

/**
 * Abstract implementation of an {@link IContentInitializer} to provide default setup of user folder
 * tree, gallery etc.
 */
public abstract class AbstractContentInitializer
    implements IContentInitializer, ApplicationContextAware, SampleTemplateInitializer {

  public static final String DEFAULT_SAMPLE_TEMPLATE_NAME = "Sample";

  protected final Logger log = LoggerFactory.getLogger(getClass());

  protected @Autowired UserDao userDao;
  protected @Autowired RecordDao recordDao;
  protected @Autowired FolderDao folderDao;
  protected @Autowired FormDao formDao;

  protected @Autowired SampleApiManager sampleApiMgr;
  protected @Autowired SampleDao sampleDao;
  protected @Autowired ContainerApiManager containerApiMgr;
  protected @Autowired IRecordFactory recordFactory;
  protected @Autowired MediaManager mediaMgr;

  protected @Autowired RecordManager recordMgr;
  protected @Autowired FormManager formMgr;
  protected @Autowired IconImageManager iconMgr;
  protected @Autowired IPermissionUtils permissnUtils;
  protected @Autowired RichTextUpdater updater;
  protected @Autowired IContentInitialiserUtils contentInitialiserUtils;

  @Qualifier("defaultUserFolderCreator")
  protected @Autowired UserFolderCreator userContentCreator;

  protected ApplicationContext appContext;

  @Override
  public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
    this.appContext = applicationContext;
  }

  /**
   * Template pattern method to handle initialisation of a user folder. Subclasses should *not*
   * override this method but should override the template methods doInit and/or initializeForms
   * instead.
   *
   * @throws IllegalAddChildOperation
   */
  public final InitializedContent init(Long userid) throws IllegalAddChildOperation {
    log.info("in init method with user {}", userid);
    User user = userDao.get(userid);
    log.info("initialising {}", userid);
    Folder rootForUser = folderDao.getRootRecordForUser(user);
    if (rootForUser == null) {
      rootForUser = contentInitialiserUtils.setupRootFolder(user);
    }
    if (user.getPermissions().size() == 0) {
      log.debug("Setting up default permissions");

      userDao.save(user);
      permissnUtils.refreshCache();
    }
    // load a default selection of files if empty
    UserFolderSetup folders = null;
    if (rootForUser.getChildrens().size() == 0) {
      Long formCount = formDao.getCount();
      RSForm basicDocument = formDao.getBasicDocumentForm();
      log.info("There are {} forms", formCount);
      if (basicDocument == null) {
        addStandardForms(user);
        addCustomForms(user);
      }
      // no longer needed as templates will be created by sysadmin on startup if needed (rsinv-433)
      createSampleTemplates(user);

      folders = userContentCreator.initStandardFolderStructure(user, rootForUser);
      modifyFolderSetUp(folders, user);
      if (isActive) {
        log.info("Is active, creating custom records");
        doInit(user, folders);
      }
      // check that basic doc form appears in menu
      RSForm basic = formDao.getBasicDocumentForm();
      if (basic != null) {
        formMgr.addFormToUserCreateMenu(user, basic.getId(), user);
      } else {
        log.warn("No Basic document form found!");
      }

      // create workbench for user, if not yet created
      containerApiMgr.getWorkbenchIdForUser(user);

      user.setContentInitialized(true);
      userDao.save(user);
    }
    return new InitializedContent(rootForUser, user, folders);
  }

  @Override
  public int createSampleTemplates(User user) {

    Long sampleTemplatesCount = sampleDao.getTemplateCount();
    if (sampleTemplatesCount > 0) {
      log.info(
          "There are already {} templates - skipping creation of system sample templates",
          sampleTemplatesCount);
      return 0;
    }
    log.info("There are no sample templates - creating them.");
    addStandardSampleTemplates(user);
    addCustomSampleTemplates(user);
    int created = sampleDao.getTemplateCount().intValue();
    log.info("{} templates created", created);
    return created;
  }

  /**
   * Subclasses can override to modify the folder setup.<br>
   * By default, this method does nothing.
   *
   * @param folders
   */
  protected void modifyFolderSetUp(UserFolderSetup folders, User user) {}

  /**
   * Adds standard forms/templates that the application relies on -e.g., 'Basic Document' form is
   * required for the notebook, and 'Basic Sample' template is a default for creating inventory
   * Sample.
   *
   * @param user who will be set as form creator
   */
  private void addStandardForms(User user) {
    RSForm basicDocument = recordFactory.createBasicDocumentForm(user);
    basicDocument.getAccessControl().setWorldPermissionType(PermissionType.READ);
    formDao.save(basicDocument);
  }

  private void addStandardSampleTemplates(User u) {
    // create new empty Sample with no fields.
    Sample basicSample = recordFactory.createSample(DEFAULT_SAMPLE_TEMPLATE_NAME, u);
    basicSample.setTemplate(true);
    sampleDao.persistSampleTemplate(basicSample);
  }

  /**
   * Template pattern hook. Subclasses should implement this method to add their own content, and
   * should assume that forms initialised by initializeForms() are available.
   *
   * @param user The user for whom the content is being created
   * @param folders A standard folder setup providing access to essential folders
   * @throws IllegalAddChildOperation
   */
  protected abstract Folder doInit(User user, UserFolderSetup folders)
      throws IllegalAddChildOperation;

  /**
   * Loads an image from the classpath into user's Gallery, e.g., for initialisation, returns html
   * img tag to be used inside text field.
   *
   * @param user user loading this image
   * @param path Path to image file
   * @param name Name to display in the gallery
   * @param fieldId Id of the text field
   * @param width
   * @param height
   * @return img element to be inserted into text field, or empty string if image couldn't be saved
   */
  public String loadImageReturnTextFieldLink(
      User user,
      String path,
      String name,
      Long fieldId,
      UserFolderSetup folders,
      int width,
      int height) {
    String url = "";
    EcatImage image = loadImage(user, path, name, folders.getMediaImgExamples());
    if (image != null) {
      String fld = "" + fieldId;
      url = updater.generateURLStringForEcatSizedImageLink(image, fld, width, height);
    }
    log.debug(" url is " + url);
    return url;
  }

  public EcatImage loadImage(User user, String path, String name, Folder target) {
    EcatImage image = null;
    Resource resource = new ClassPathResource(path);
    try {
      image = mediaMgr.saveNewImage(name, resource.getInputStream(), user, target);
    } catch (IOException e) {
      log.warn("img from path [{}] could not be saved into {}", path, target);
    }
    return image;
  }

  /**
   * Create environment-specific forms, if they are not yet created. All forms should be
   * world-readable.
   *
   * @param user for will be creator of the form
   */
  protected abstract void addCustomForms(User user);

  /**
   * Create environment-specific sample templates, if they are not yet created.
   *
   * @param user who will be creator of the sample template
   */
  protected abstract void addCustomSampleTemplates(User user);

  /**
   * @param fileName A filename of a file in the formIcons/ folder in src/main/resources
   * @param form The {@link RSForm} to associate with the icon.
   * @return The created {@link IconEntity} object.
   * @throws IOException
   */
  protected IconEntity createAndSaveIconEntity(String fileName, RSForm form) throws IOException {
    IconEntity ice = saveIconEntity(fileName, form);
    log.info("saved icon " + ice.getId() + " for form " + form.getName());
    form.setIconId(ice.getId());
    formDao.save(form);
    return ice;
  }

  protected IconEntity createAndSaveIconEntity(String fileName, Sample template)
      throws IOException {
    IconEntity ice = saveIconEntity(fileName, template);
    log.info("saved icon " + ice.getId() + " for template " + template.getName());
    template.setIconId(ice.getId());
    sampleDao.save(template);
    return ice;
  }

  private IconEntity saveIconEntity(String fileName, UniquelyIdentifiable associatedEntity)
      throws IOException {
    Resource resource = appContext.getResource("classpath:formIcons/" + fileName);
    InputStream is = resource.getInputStream();
    byte[] bytes = IOUtils.toByteArray(is);
    IconEntity ice = new IconEntity();
    ice.setId(null);
    ice.setImgType("png");
    ice.setIconImage(bytes);
    String imageName = fileName;
    ice.setImgName(imageName);
    ice.setParentId(associatedEntity.getId());
    ice = iconMgr.saveIconEntity(ice, associatedEntity instanceof RSForm);
    return ice;
  }

  protected void createAndSavePreviewImage(String fileName, User user, Sample template)
      throws IOException {
    Resource resource = appContext.getResource("classpath:formIcons/" + fileName);
    InputStream is = resource.getInputStream();
    byte[] bytes = IOUtils.toByteArray(is);
    String base64image =
        "data:image/png;base64," + new String(Base64.encodeBase64(bytes), StandardCharsets.UTF_8);

    sampleApiMgr.setPreviewImageForInvRecord(template, base64image, user);
    sampleDao.save(template);
  }

  /**
   * This is duplicated from FolderMger to avoid a cyclic dependency between foldermgr and recordMgr
   * which buggers up the spring wiring. Handles all persistence of a adding a newly created object
   * to a persisted parent
   *
   * @throws IllegalAddChildOperation
   */
  public Folder addChild(Folder f, BaseRecord newTransientChild, User owner)
      throws IllegalAddChildOperation {
    return contentInitialiserUtils.addChild(f, newTransientChild, owner);
  }

  @Override
  public void saveRecord(StructuredDocument record) {
    recordDao.save(record);
  }

  @Override
  public void saveForm(RSForm form) {
    formDao.save(form);
  }

  @Override
  public void saveSampleTemplate(Sample template) {
    sampleDao.persistSampleTemplate(template);
  }

  @Override
  public RSForm findFormByName(String name) {
    return formDao.findOldestFormByName(name);
  }

  private boolean isActive = true;

  public void setCustomInitActive(boolean isActive) {
    this.isActive = isActive;
  }

  /* ======================
   *   for testing
   * ======================
   */
  void setContentInitialiserUtils(IContentInitialiserUtils contentInitialiserUtils) {
    this.contentInitialiserUtils = contentInitialiserUtils;
  }

  void setFormDao(FormDao formDao) {
    this.formDao = formDao;
  }

  void setRecordDao(RecordDao recordDao) {
    this.recordDao = recordDao;
  }

  void setFormMgr(FormManager formMgr) {
    this.formMgr = formMgr;
  }

  void setUserDao(UserDao userDao) {
    this.userDao = userDao;
  }

  void setFolderDao(FolderDao folderDao) {
    this.folderDao = folderDao;
  }

  void setRecordFactory(IRecordFactory recordFactory) {
    this.recordFactory = recordFactory;
  }

  void setPermissnUtils(IPermissionUtils permissnUtils) {
    this.permissnUtils = permissnUtils;
  }

  void setRecordMgr(RecordManager manager) {
    this.recordMgr = manager;
  }

  void setIconMgr(IconImageManager iconMgr) {
    this.iconMgr = iconMgr;
  }

  void setMediaMgr(MediaManager mediaMgr) {
    this.mediaMgr = mediaMgr;
  }

  void setRichTextUpdater(RichTextUpdater updater) {
    this.updater = updater;
  }

  public void setUserFolderCreator(UserFolderCreator userContentCreator) {
    this.userContentCreator = userContentCreator;
  }

  void setSampleDao(SampleDao sampleDao) {
    this.sampleDao = sampleDao;
  }

  void setSampleApiMgr(SampleApiManager sampleApiMgr) {
    this.sampleApiMgr = sampleApiMgr;
  }

  void setContainerApiMgr(ContainerApiManager containerApiMgr) {
    this.containerApiMgr = containerApiMgr;
  }
}
