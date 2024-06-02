package com.researchspace.api.v1.controller;

import static com.researchspace.service.FolderManager.API_INBOX_LOCK;

import com.researchspace.api.v1.ImportApi;
import com.researchspace.api.v1.model.ApiDocumentInfo;
import com.researchspace.api.v1.model.ApiFolder;
import com.researchspace.document.importer.ExternalFileImporter;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.service.FolderManager;
import java.io.IOException;
import java.util.Optional;
import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@ApiController
public class ImportsApiController extends BaseApiController implements ImportApi {
  @Autowired
  @Qualifier("externalWordFileImporter")
  private ExternalFileImporter externalWordFileImporter;

  @Qualifier("evernoteFileImporter")
  @Autowired
  private ExternalFileImporter evernoteFileImporter;

  private @Autowired FolderManager folderMgr;
  private @Autowired IPermissionUtils permissionUtils;

  @Override
  public ApiDocumentInfo importWord(
      @RequestParam(value = "folderId", required = false) Long folderId,
      @RequestParam(value = "imageFolderId", required = false) Long imageFolderId,
      @RequestParam("file") MultipartFile file,
      @RequestAttribute(name = "user") User user)
      throws IOException {

    Folder targetFolder = getTargetFolder(folderId, user);
    Optional<Folder> imageFolder = getImageFolder(imageFolderId, user);
    try {
      BaseRecord created = doImport(file, user, targetFolder, imageFolder);
      ApiDocumentInfo docInfo = new ApiDocumentInfo(created.asStrucDoc(), user);
      buildAndAddSelfLink(DOCUMENTS_ENDPOINT, docInfo);
      return docInfo;
    } catch (IllegalStateException e) { // if conversion failed.
      // will cause 422 code
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  @Override
  public ApiFolder importEvernote(
      @RequestParam(value = "folderId", required = false) Long folderId,
      @RequestParam(value = "imageFolderId", required = false) Long imageFolderId,
      @RequestParam("file") MultipartFile file,
      @RequestAttribute(name = "user") User user)
      throws IOException {
    if (!file.getOriginalFilename().endsWith("enex")) {
      throw new IllegalArgumentException("File must be in Evernote XML ending in .enex");
    }
    Folder targetFolder = getTargetFolder(folderId, user);
    permissionUtils.isRecordAccessPermitted(user, targetFolder, PermissionType.READ);
    Optional<Folder> imageFolder = getImageFolder(imageFolderId, user);
    try {
      BaseRecord created = doImport(file, user, targetFolder, imageFolder);
      // this will always be a folder
      ApiFolder docInfo = new ApiFolder((Folder) created, user);
      buildAndAddSelfLink(FOLDERS_ENDPOINT, docInfo);
      return docInfo;

    } catch (IllegalStateException e) { // if conversion failed.
      // will cause 422 code
      throw new IllegalArgumentException(e.getMessage(), e);
    }
  }

  private Folder getTargetFolder(Long folderId, User user) {
    Folder target = null;
    synchronized (API_INBOX_LOCK) {
      target = folderMgr.getApiUploadTargetFolder("", user, folderId);
    }
    return target;
  }

  private BaseRecord doImport(
      MultipartFile file, User user, Folder targetFolder, Optional<Folder> imageFolder)
      throws IOException {
    return getFileExporterForMultipartFile(file)
        .create(
            file.getInputStream(),
            user,
            targetFolder,
            imageFolder.isPresent() ? imageFolder.get() : null,
            file.getOriginalFilename());
  }

  private Optional<Folder> getImageFolder(Long imageFolderId, User user) {
    Optional<Folder> imageFolder = Optional.empty();
    if (imageFolderId != null) {
      imageFolder = folderMgr.getFolderSafe(imageFolderId, user);
    }
    return imageFolder;
  }

  private ExternalFileImporter getFileExporterForMultipartFile(MultipartFile mf) {
    if (FilenameUtils.getExtension(mf.getOriginalFilename()).equalsIgnoreCase("enex")) {
      return evernoteFileImporter;
    } else {
      return externalWordFileImporter;
    }
  }
}
