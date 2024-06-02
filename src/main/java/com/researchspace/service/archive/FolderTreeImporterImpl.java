package com.researchspace.service.archive;

import static com.researchspace.core.util.XMLReadWriteUtils.fromXML;

import com.researchspace.archive.ArchivalImportConfig;
import com.researchspace.archive.ArchiveFolder;
import com.researchspace.archive.ExportRecordList;
import com.researchspace.archive.IArchiveModel;
import com.researchspace.core.util.MediaUtils;
import com.researchspace.core.util.version.SemanticVersion;
import com.researchspace.core.util.version.Versionable;
import com.researchspace.dao.FolderDao;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.ImportOverride;
import com.researchspace.model.record.Notebook;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordContext;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserManager;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

/** Creates a new folder tree from the contents of the folderTree.xml document */
public class FolderTreeImporterImpl implements FolderTreeImporter {

  private static final String TEMPLATES_FOLDER_TYPE = "FOLDER:SYSTEM:TEMPLATE";

  private @Autowired FolderManager folderManager;
  private @Autowired UserManager userMgr;

  @Autowired
  @Qualifier("propertyHolder")
  private Versionable rspaceVersion;

  private @Autowired RecordManager recordManager;
  private @Autowired FolderDao folderDao;

  @Value("${import.allowCreationDateAfterModificationDate}")
  private boolean allowCreationDateAfterModificationDate;

  Logger log = LoggerFactory.getLogger(FolderTreeImporterImpl.class);

  @Override
  public Map<Long, Folder> createFolderTree(
      File folderTree,
      RecordContext context,
      ArchivalImportConfig iconfig,
      IArchiveModel archiveModel,
      ImportArchiveReport report) {
    User user = userMgr.getUserByUsername(iconfig.getUser());

    // just holds the current versions
    Folder targetRoot = folderDao.getRootRecordForUser(user);
    Map<Long, Folder> oldIdToNewFolder = new HashMap<>();
    try {
      ExportRecordList rl = fromXML(folderTree, ExportRecordList.class, null, null);
      List<ArchiveFolder> topLEvelFolders = rl.getTopLevelFolders();
      for (ArchiveFolder topLevel : topLEvelFolders) {
        createFolder(
            oldIdToNewFolder,
            context,
            targetRoot.getId(),
            topLevel,
            user,
            rl,
            archiveModel,
            report);
      }
    } catch (Exception e) {
      log.error(e.getMessage());
      throw new ImportFailureException(e);
    }
    return oldIdToNewFolder;
  }

  // depth first traversal of folder tree; ensuring that child folders are always created after
  // parent folders
  private void createFolder(
      Map<Long, Folder> oldIdToNewFolder,
      RecordContext context,
      Long newParentId,
      ArchiveFolder archiveFlder,
      User owner,
      ExportRecordList rl,
      IArchiveModel archiveModel,
      ImportArchiveReport report) {

    Folder newFolder = null;
    ImportOverride override =
        archiveFlder.createImportOverride(allowCreationDateAfterModificationDate);
    if (RecordType.NOTEBOOK.name().equals(archiveFlder.getType())) {
      Notebook nb =
          folderManager.createNewNotebook(
              newParentId, archiveFlder.getName(), context, owner, override);
      saveArchiveFolderTagsIntoImportedFolder(archiveFlder, owner, nb);
      report.addImportedNotebook(nb);
      newFolder = nb;
    } else if (rl.archiveParentFolderMatches(archiveFlder.getId(), af -> af.isRootMediaFolder())) {
      // this is a pre-existing gallery folder created after user initialisation
      if (isTemplateFolderMigrationRequired(archiveFlder, archiveModel)) {
        newFolder = folderDao.getTemplateFolderForUser(owner);
      }
      newFolder = recordManager.getGallerySubFolderForUser(archiveFlder.getName(), owner);
    } else if (archiveFlder.isApiFolder()) {
      // which api folder?
      Optional<ArchiveFolder> parent = rl.getArchiveFolder(archiveFlder.getParentId());
      String apiFolderContentType = "";
      // It is not present in case it is an export of selected folders but root folder is not
      // selected.
      if (parent.isPresent()) {
        apiFolderContentType = parent.get().isRootFolder() ? "" : parent.get().getName();
      }
      newFolder = folderManager.getApiUploadTargetFolder(apiFolderContentType, owner, null);
    } else if (archiveFlder.isRootMediaFolder()) {
      newFolder = folderDao.getGalleryFolderForUser(owner);
      // we're importing a media folder from a selection. If we're importing whole Gallery,
      // this condition won't be met as these user-created gallery folders won't be top-level export
      // folders.
    } else if (rl.getTopLevelFolders().contains(archiveFlder) && archiveFlder.isMedia()) {
      Folder galleryTop =
          recordManager.getGallerySubFolderForUser(archiveFlder.getMediaType(), owner);
      // default to miscellaneous
      if (galleryTop == null) {
        log.warn("Could not located media folder {}, defaulting to 'Miscellaneous' folder");
        galleryTop =
            recordManager.getGallerySubFolderForUser(MediaUtils.MISC_MEDIA_FLDER_NAME, owner);
      }
      newFolder =
          folderManager.createNewFolder(
              galleryTop.getId(), archiveFlder.getName(), owner, override);
    } else if (TEMPLATES_FOLDER_TYPE.equals(archiveFlder.getType())) {
      newFolder = folderDao.getTemplateFolderForUser(owner);
    } else {
      newFolder =
          folderManager.createNewFolder(newParentId, archiveFlder.getName(), owner, override);
      saveArchiveFolderTagsIntoImportedFolder(archiveFlder, owner, newFolder);
    }
    oldIdToNewFolder.put(archiveFlder.getId(), newFolder);
    for (ArchiveFolder child : rl.getChildren(archiveFlder.getId())) {
      createFolder(
          oldIdToNewFolder, context, newFolder.getId(), child, owner, rl, archiveModel, report);
    }
  }

  private void saveArchiveFolderTagsIntoImportedFolder(
      ArchiveFolder archiveFlder, User owner, Folder folder) {
    folder.setDocTag(archiveFlder.getTag());
    folder.setTagMetaData(archiveFlder.getTagMetaData());
    folderManager.save(folder, owner);
  }

  private boolean isTemplateFolderMigrationRequired(
      ArchiveFolder archiveFlder, IArchiveModel archiveModel) {
    return archiveFlder.getName().equals(Folder.TEMPLATE_MEDIA_FOLDER_NAME)
        && archiveModel.getSourceRSpaceVersion().isOlderThan(new SemanticVersion("1.36"))
        && rspaceVersion.getVersion().isSameOrNewerThan(new SemanticVersion("1.36"));
  }
}
