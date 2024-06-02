package com.researchspace.service.impl;

import com.axiope.model.record.init.UserFolderSetupImpl;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.dao.FolderDao;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.permissions.ACLElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.permissions.PermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.ACLPropagationPolicy;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IRecordFactory;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.service.FolderManager;
import com.researchspace.service.IContentInitialiserUtils;
import com.researchspace.service.UserFolderCreator;
import com.researchspace.service.UserFolderSetup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/** Standard folder organisation for 1.36 onwards, with Templates in Workspace */
public class DefaultUserFolderCreator implements UserFolderCreator {
  @Autowired private IContentInitialiserUtils contentUtils;
  protected final Logger log = LoggerFactory.getLogger(getClass());
  @Autowired private IRecordFactory recordFactory;
  @Autowired private FolderDao folderDao;
  @Autowired private PermissionFactory permFactory;
  @Autowired private FolderManager folderManager;

  public DefaultUserFolderCreator() {}

  public DefaultUserFolderCreator(
      IRecordFactory recordFactory,
      PermissionFactory permFactory,
      FolderDao folderDao,
      IContentInitialiserUtils contentUtils,
      FolderManager folderManager) {
    this.recordFactory = recordFactory;
    this.permFactory = permFactory;
    this.folderDao = folderDao;
    this.contentUtils = contentUtils;
    this.folderManager = folderManager;
  }

  @Override
  public UserFolderSetup initStandardFolderStructure(User subject, Folder rootForUser) {
    UserFolderSetupImpl folderSetup = new UserFolderSetupImpl();
    folderSetup.setUserRoot(rootForUser);
    folderDao.getLabGroupFolderForUser(subject);
    Folder sharedFolder = setUpSharedFolderStructure(subject, rootForUser);
    folderSetup.setShared(sharedFolder);

    log.info("Folder Setup {}", folderSetup.getShared().getName());

    Folder examples = recordFactory.createSystemCreatedFolder(Folder.EXAMPLES_FOLDER, subject);

    contentUtils.addChild(rootForUser, examples, subject);
    folderSetup.setExamples(examples);
    log.info("Folder Setup {} - {}", examples.getName(), examples.getId());
    contentUtils.delayForUniqueCreationTime();

    // create media folder
    Folder mediaFolder = createRootMediaFolder(subject, rootForUser, folderSetup);
    log.info("Folder Setup {}", folderSetup.getMediaRoot().getName());

    // Create images folder
    Folder imageFolder =
        createMediaSubfolder(MediaUtils.IMAGES_MEDIA_FLDER_NAME, mediaFolder, subject);
    folderSetup.setMediaImg(imageFolder);

    // Create example images folder
    Folder imageExampleFolder =
        createMediaSubfolder(MediaUtils.IMAGES_EXAMPLES_MEDIA_FLDER_NAME, imageFolder, subject);
    folderSetup.setMediaImgExamples(imageExampleFolder);

    // Create audio folder
    Folder audioFolder =
        createMediaSubfolder(MediaUtils.AUDIO_MEDIA_FLDER_NAME, mediaFolder, subject);
    folderSetup.setMediaAudio(audioFolder);
    log.info("Folder Setup {}", folderSetup.getMediaAudio().getName());

    // Create Video folder
    Folder videoFolder =
        createMediaSubfolder(MediaUtils.VIDEO_MEDIA_FLDER_NAME, mediaFolder, subject);
    folderSetup.setMediaVideo(videoFolder);
    log.info("Folder Setup {}", folderSetup.getMediaVideo().getName());

    // Create Chemistry folder
    Folder chemFolder =
        createMediaSubfolder(MediaUtils.CHEMISTRY_MEDIA_FLDER_NAME, mediaFolder, subject);
    folderSetup.setMediaChemistry(chemFolder);
    log.info("Folder Setup {}", folderSetup.getMediaChemistry().getName());

    // Create documents folder
    Folder documentsFolder =
        createMediaSubfolder(MediaUtils.DOCUMENT_MEDIA_FLDER_NAME, mediaFolder, subject);
    folderSetup.setMediaDocs(documentsFolder);
    log.info("Folder Setup {}", folderSetup.getMediaDocs().getName());

    // Create miscellaneous media folder
    Folder documentsMiscFolder =
        createMediaSubfolder(MediaUtils.MISC_MEDIA_FLDER_NAME, mediaFolder, subject);
    folderSetup.setMediaMiscl(documentsMiscFolder);
    log.info("Folder Setup {}", folderSetup.getMediaMiscl().getName());

    // Create PDF folder
    Folder pdfMediaFolder = createMediaSubfolder(Folder.EXPORTS_FOLDER_NAME, mediaFolder, subject);
    folderSetup.setPdfMedia(pdfMediaFolder);
    log.info("Folder Setup {}", folderSetup.getPdfMedia().getName());

    // Create snippets folder
    Folder snippet = createMediaSubfolder(Folder.SNIPPETS_FOLDER, mediaFolder, subject);
    folderSetup.setSnippet(snippet);
    log.info("Folder Setup {}", folderSetup.getSnippet().getName());
    Folder sharedSnippet = createSharedSnippetFolder(subject, snippet);
    folderSetup.setSharedSnippet(sharedSnippet);

    // Create Templates folder
    createTemplateFolder(subject, rootForUser, folderSetup);
    contentUtils.delayForUniqueCreationTime();
    log.info("Folder Setup {}", folderSetup.getTemplateFolder().getName());

    return folderSetup;
  }

  /**
   * This checks to see if a shared snippet folder has been created for the GROUP and if it has,
   * then it is added as a child folder to the newly created 'SHARED/LabGroup' folder inside
   * SNIPPETS (ie we end up with SNIPPETS/SHARED/LabGroup/<Group_shared_snippets>). Note that the
   * code which creates the shared snippet folder
   * (GroupManagerImpl.createSharedCommunalGroupFolders()) does the reciprocal check and adds the
   * GROUP shared snippet folder as a child to this user's SNIPPET/SHARED/LabGroup folder - if it
   * has not already been added.
   *
   * @param subject user doing creation
   * @param snippetFolder snippet folder in user's Gallery
   * @return shared snippet folder
   */
  @Override
  public Folder createSharedSnippetFolder(User subject, Folder snippetFolder) {
    Folder sharedSnippet =
        setUpSharedFolderStructure(
            subject, snippetFolder, UserFolderCreator.SHARED_SNIPPETS_FOLDER_PREFIX);
    Folder labGroup =
        sharedSnippet.getSubFolderByName(
            UserFolderCreator.SHARED_SNIPPETS_FOLDER_PREFIX + Folder.LAB_GROUPS_FOLDER_NAME);
    Folder collabGroup =
        sharedSnippet.getSubFolderByName(
            UserFolderCreator.SHARED_SNIPPETS_FOLDER_PREFIX
                + Folder.COLLABORATION_GROUPS_FLDER_NAME);
    log.info("Folder Shared Snippet Setup {}", sharedSnippet.getName());
    for (Group aGroup : subject.getGroups()) {
      if (aGroup.getSharedSnippetGroupFolderId() != null) {
        Folder grpSharedSnippetFolder =
            folderManager.getFolder(aGroup.getSharedSnippetGroupFolderId(), subject);
        Long groupRecordId = aGroup.isCollaborationGroup() ? collabGroup.getId() : labGroup.getId();
        folderManager.addChild(
            groupRecordId, grpSharedSnippetFolder, subject, ACLPropagationPolicy.NULL_POLICY);
      }
    }
    return sharedSnippet;
  }

  private Folder createMediaSubfolder(String folderName, Folder rootFolder, User subject) {
    Folder newFolder = recordFactory.createSystemCreatedFolder(folderName, subject);
    contentUtils.addChild(rootFolder, newFolder, subject);
    contentUtils.delayForUniqueCreationTime();
    return newFolder;
  }

  private void createTemplateFolder(
      User subject, Folder rootForUser, UserFolderSetupImpl folderSetup) {
    Folder templateFolder =
        recordFactory.createSystemCreatedFolder(Folder.TEMPLATE_MEDIA_FOLDER_NAME, subject);
    templateFolder.addType(RecordType.TEMPLATE);
    permFactory.setUpACLForIndividualTemplateFolder(templateFolder, subject);
    contentUtils.addChild(rootForUser, templateFolder, subject, ACLPropagationPolicy.NULL_POLICY);
    // folderSetup.templates=templatesx; actually for form;
    folderSetup.setTemplateFolder(templateFolder);
  }

  private Folder createRootMediaFolder(
      User subject, Folder rootForUser, UserFolderSetupImpl folderSetup) {
    Folder mediaFolder = recordFactory.createRootMediaFolder(subject);
    contentUtils.addChild(rootForUser, mediaFolder, subject);
    folderSetup.setMediaRoot(mediaFolder);
    contentUtils.delayForUniqueCreationTime();
    return mediaFolder;
  }

  private Folder setUpSharedFolderStructure(User u, Folder rootForUser) {
    return setUpSharedFolderStructure(u, rootForUser, "");
  }

  /*
   * Sets up the shared folders, which don't have crud options available
   */
  private Folder setUpSharedFolderStructure(User u, Folder rootForUser, String folderNamePrefix)
      throws IllegalAddChildOperation {
    Folder sharedFolder =
        recordFactory.createSystemCreatedFolder(
            StringUtils.hasLength(folderNamePrefix)
                ? folderNamePrefix + Folder.SHARED_FOLDER_NAME
                : Folder.SHARED_FOLDER_NAME,
            u);
    ConstraintBasedPermission cbp =
        new ConstraintBasedPermission(PermissionDomain.RECORD, PermissionType.READ);
    ACLElement element = new ACLElement(u.getUsername(), cbp);
    sharedFolder.getSharingACL().addACLElement(element);
    contentUtils.addChild(rootForUser, sharedFolder, u, ACLPropagationPolicy.NULL_POLICY);
    contentUtils.delayForUniqueCreationTime();

    Folder labgroups =
        recordFactory.createSystemCreatedFolder(
            StringUtils.hasLength(folderNamePrefix)
                ? folderNamePrefix + Folder.LAB_GROUPS_FOLDER_NAME
                : Folder.LAB_GROUPS_FOLDER_NAME,
            u);
    labgroups.getSharingACL().addACLElement(element);
    contentUtils.addChild(sharedFolder, labgroups, u);
    contentUtils.delayForUniqueCreationTime();

    Folder collabGrps =
        recordFactory.createSystemCreatedFolder(
            StringUtils.hasLength(folderNamePrefix)
                ? folderNamePrefix + Folder.COLLABORATION_GROUPS_FLDER_NAME
                : Folder.COLLABORATION_GROUPS_FLDER_NAME,
            u);
    collabGrps.getSharingACL().addACLElement(element);
    contentUtils.addChild(sharedFolder, collabGrps, u);
    contentUtils.delayForUniqueCreationTime();

    Folder individualSharedItems =
        recordFactory.createSystemCreatedFolder(
            StringUtils.hasLength(folderNamePrefix)
                ? folderNamePrefix + Folder.INDIVIDUAL_SHARE_ITEMS_FLDER_NAME
                : Folder.INDIVIDUAL_SHARE_ITEMS_FLDER_NAME,
            u);
    individualSharedItems.getSharingACL().addACLElement(element);
    contentUtils.addChild(sharedFolder, individualSharedItems, u);
    contentUtils.delayForUniqueCreationTime();
    return sharedFolder;
  }
}
