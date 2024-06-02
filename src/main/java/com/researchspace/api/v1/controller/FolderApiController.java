package com.researchspace.api.v1.controller;

import static com.researchspace.core.util.TransformerUtils.toSet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;

import com.google.common.base.Supplier;
import com.researchspace.api.v1.FolderApi;
import com.researchspace.api.v1.model.ApiFolder;
import com.researchspace.api.v1.model.ApiRecordTreeItemListing;
import com.researchspace.api.v1.model.RecordTreeItemInfo;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.DefaultRecordContext;
import com.researchspace.service.DocumentAlreadyEditedException;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.impl.RecordDeletionManagerImpl.DeletionSettings;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@ApiController
public class FolderApiController extends BaseApiController implements FolderApi {

  private @Autowired FolderManager folderMgr;
  private @Autowired RecordDeletionManager recordDeletionManager;

  /** Only name field of folder is required. */
  @Override
  public ApiFolder createNewFolder(
      @RequestBody @Valid ApiFolder toCreate,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    Folder targetFolder = null;
    if (toCreate.getParentFolderId() != null) {
      targetFolder = folderMgr.getFolder(toCreate.getParentFolderId(), user);
      if (targetFolder.isNotebook()) {
        errors.reject("notebook.nestednotebook.error", "Nested notebooks are prohibited");
        throw new BindException(errors);
      }
    } else {
      targetFolder = folderMgr.getRootFolderForUser(user);
    }
    if (targetFolder.hasType(RecordType.ROOT_MEDIA)) {
      errors.reject("gallery.api.no_top_level_folder", "Can't create top-level Gallery folder");
      throw new BindException(errors);
    }

    Folder folder = null;
    if (toCreate.isNotebook()) {
      if (!targetFolder.isInWorkspace()) {
        errors.reject(
            "notebook.no_notebook_in_gallery.error", "Notebooks can only exist in the Workspace");
        throw new BindException(errors);
      }
      folder =
          folderMgr.createNewNotebook(
              targetFolder.getId(), toCreate.getName(), new DefaultRecordContext(), user);
    } else {
      folder = folderMgr.createNewFolder(targetFolder.getId(), toCreate.getName(), user);
    }

    ApiFolder rc = new ApiFolder(folder, user);
    buildAndAddSelfLink(FOLDERS_ENDPOINT, rc);
    return rc;
  }

  @Override
  public ApiFolder getFolder(@PathVariable Long id, @RequestAttribute(name = "user") User user) {
    Folder folder = loadFolder(id, user);

    ApiFolder rc = new ApiFolder(folder, user);
    buildAndAddSelfLink(FOLDERS_ENDPOINT, rc);
    return rc;
  }

  @Override
  public void deleteFolder(@PathVariable Long id, @RequestAttribute(name = "user") User user) {
    Folder folder = loadFolder(id, user); // test it exists and is a folder.
    if (folder.isSystemFolder() || folder.isRootFolder()) {
      throw new IllegalArgumentException("Cannot delete user home folder or a system folder");
    }
    DeletionSettings settings =
        DeletionSettings.builder()
            .currentUsers(getCurrentActiveUsers())
            .notebookEntryDeletion(false)
            .noAccessHandler((a, b) -> {})
            .build();
    try {
      ServiceOperationResultCollection<CompositeRecordOperationResult, Long> result =
          recordDeletionManager.doDeletion(
              new Long[] {id}, user::getUsername, settings, user, ProgressMonitor.NULL_MONITOR);
      if (!result.isAllSucceeded()) {
        throw new RuntimeException("Unknown server error deleting folder: " + id);
      }
    } catch (DocumentAlreadyEditedException e) {
      // this is just thrown when deleting documents, so this should not happen
      log.error("Error deleting folder: ", e);
    }
  }

  private Folder loadFolder(Long id, User user) {
    return folderMgr
        .getFolderSafe(id, user)
        .orElseThrow(() -> new NotFoundException(createNotFoundMessage("Folder", id)));
  }

  private static final Set<String> ACCEPTABLE_TYPES = toSet("notebook", "folder", "document");

  @Override
  public ApiRecordTreeItemListing rootFolderTree(
      @RequestParam(name = "typesToInclude", required = false) Set<String> typesToInclude,
      @Valid DocumentApiPaginationCriteria pgCrit,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    return doListing(
        typesToInclude, pgCrit, errors, user, () -> folderMgr.getRootFolderForUser(user), false);
  }

  @Override
  public ApiRecordTreeItemListing folderTreeById(
      @PathVariable Long id,
      @RequestParam(name = "typesToInclude", required = false) Set<String> recordTypes,
      @Valid DocumentApiPaginationCriteria pgCrit,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    // validates read permission
    Folder toListFolder = loadFolder(id, user);
    return doListing(
        recordTypes,
        pgCrit,
        errors,
        user,
        () -> toListFolder,
        toListFolder.hasAncestorOfType(RecordType.ROOT_MEDIA, true));
  }

  private ApiRecordTreeItemListing doListing(
      Set<String> typesToInclude,
      DocumentApiPaginationCriteria pgCrit,
      BindingResult errors,
      User user,
      Supplier<Folder> folderSupplier,
      boolean isMediaFolder)
      throws BindException {
    // validate
    throwBindExceptionIfErrors(errors);
    validateTypesToInclude(typesToInclude);

    // retrieve
    PaginationCriteria<BaseRecord> internalPgCrit =
        getPaginationCriteriaForApiSearch(pgCrit, BaseRecord.class);

    RecordTypeFilter filter = generateRecordFilter(typesToInclude, isMediaFolder);
    Folder folderToList = folderSupplier.get();
    ISearchResults<BaseRecord> results =
        recordManager.listFolderRecords(folderToList.getId(), internalPgCrit, filter);

    // process results
    ApiRecordTreeItemListing apiRecordTreeItemListing = new ApiRecordTreeItemListing();

    List<RecordTreeItemInfo> fileList = new ArrayList<>();
    convertISearchResults(
        pgCrit,
        null,
        user,
        results,
        apiRecordTreeItemListing,
        fileList,
        file -> new RecordTreeItemInfo(file, user),
        info -> buildAndAddSelfLink(calculateSelfLink(info), info));
    if (folderToList.hasSingleParent()) {
      apiRecordTreeItemListing.setParentId(folderToList.getParent().getId());
    }
    return apiRecordTreeItemListing;
  }

  private void validateTypesToInclude(Set<String> typesToInclude) {
    if (!isEmpty(typesToInclude)
        && !CollectionUtils.containsAll(ACCEPTABLE_TYPES, typesToInclude)) {
      throw new IllegalArgumentException(
          "typesToInclude terms must be one of: " + StringUtils.join(ACCEPTABLE_TYPES, ","));
    }
  }

  private String calculateSelfLink(RecordTreeItemInfo info) {
    switch (info.getType()) {
      case NOTEBOOK:
      case FOLDER:
        return BaseApiController.FOLDERS_ENDPOINT;
      case DOCUMENT:
        return BaseApiController.DOCUMENTS_ENDPOINT;
      case MEDIA:
        return BaseApiController.FILES_ENDPOINT;

      default:
        throw new IllegalStateException("document type cannot be null");
    }
  }

  RecordTypeFilter generateRecordFilter(Set<String> typesToInclude, boolean isMediaFolder) {
    RecordTypeFilter rcFilter = null;
    // add exclusions, only if there is a filter in the request
    if (!isEmpty(typesToInclude)) {

      Collection<RecordType> toExcludeEnums = new HashSet<>();
      if (!typesToInclude.contains("notebook")) {
        toExcludeEnums.add(RecordType.NOTEBOOK);
      }
      if (!typesToInclude.contains("folder")) {
        toExcludeEnums.add(RecordType.FOLDER);
      }
      if (!typesToInclude.contains("document")) {
        toExcludeEnums.add(RecordType.MEDIA_FILE);
        toExcludeEnums.add(RecordType.NORMAL);
      }
      toExcludeEnums.remove(RecordType.ROOT_MEDIA); // enable Gallery to be listed in root search
      rcFilter = new RecordTypeFilter(EnumSet.copyOf(toExcludeEnums), false);
    } else {
      // don't restrict by type.
      rcFilter = new RecordTypeFilter(EnumSet.noneOf(RecordType.class), false);
    }
    return rcFilter;
  }
}
