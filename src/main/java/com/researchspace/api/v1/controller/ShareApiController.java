package com.researchspace.api.v1.controller;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.researchspace.api.v1.ShareApi;
import com.researchspace.api.v1.model.ApiShareInfo;
import com.researchspace.api.v1.model.ApiShareSearchResult;
import com.researchspace.api.v1.model.ApiSharingResult;
import com.researchspace.api.v1.model.GroupSharePostItem;
import com.researchspace.api.v1.model.SharePost;
import com.researchspace.api.v1.model.UserSharePostItem;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.FolderDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.dtos.SharedRecordSearchCriteria;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.record.Folder;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.DetailedRecordInformationProvider;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.SharingHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.validation.Valid;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

@ApiController
public class ShareApiController extends BaseApiController implements ShareApi {

  private static final Logger log = LoggerFactory.getLogger(ShareApiController.class);

  private @Autowired SharingHandler recordShareHandler;
  private @Autowired RecordSharingManager recordShareMgr;
  @Autowired private DetailedRecordInformationProvider detailedRecordInformationProvider;
  @Autowired private FolderDao folderDao;
  @Autowired private RecordGroupSharingDao recordGroupSharingDao;

  /** */
  @Override
  public ApiSharingResult shareItems(
      @Valid @RequestBody SharePost shareConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);
    ShareConfigCommand command = convertApiCfgToInternalCfg(shareConfig);
    // these could be null when incoming
    if (shareConfig.getGroupSharePostItems() == null) {
      shareConfig.setGroupSharePostItems(new ArrayList<>());
    }
    if (shareConfig.getUserSharePostItems() == null) {
      shareConfig.setUserSharePostItems(new ArrayList<>());
    }
    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result =
        recordShareHandler.shareRecords(command, user);
    log.info(
        "Success: {}, Failures: {}, Exception :{}",
        result.getResultCount(),
        result.getFailureCount(),
        result.getExceptionCount());

    if (allSharesFailedWithAuthException(result)) {
      throw new AuthorizationException(
          "No permissions to share items with group  - ensure that the sharer is the owner of the"
              + " items to be shared. Only Notebooks and Documents can be shared.");
    }
    ApiSharingResult rc =
        new ApiSharingResult(
            entityToSharedInfo(result.getResults()), failuresToIds(result.getFailures()));

    return rc;
  }

  private boolean allSharesFailedWithAuthException(
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result) {
    return result.getExceptionCount() > 0
        && result.getResultCount() == 0
        && result.getExceptions().stream().allMatch(e -> e instanceof AuthorizationException);
  }

  @Override
  public void deleteShare(@PathVariable("id") Long id, @RequestAttribute(name = "user") User user) {
    try {
      recordShareHandler.unshare(id, user);
    } catch (DataAccessException e) {
      throw new NotFoundException(createNotFoundMessage("Share", id), e);
    }
  }

  @Override
  public ApiSharingResult updateShare(
      @PathVariable("id") Long id,
      @Valid @RequestBody SharePost shareConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {
    throwBindExceptionIfErrors(errors);

    try {
      // Get the existing share
      RecordGroupSharing existingShare = recordShareMgr.get(id);
      if (existingShare == null) {
        throw new NotFoundException(createNotFoundMessage("Share", id));
      }

      return performUpdate(existingShare, shareConfig, user);
    } catch (DataAccessException e) {
      throw new NotFoundException(createNotFoundMessage("Share", id), e);
    }
  }

  private ApiSharingResult performUpdate(
      RecordGroupSharing existingShare, SharePost shareConfig, User user) {

    // For permission updates only
    if (isPermissionOnlyUpdate(existingShare, shareConfig)) {
      return updatePermissionsOnly(existingShare, shareConfig, user);
    }

    // For folder location changes
    if (isFolderLocationUpdate(existingShare, shareConfig)) {
      return updateFolderLocation(existingShare, shareConfig, user);
    }
    return null;
  }

  /** Determines if this is a permission-only update */
  private boolean isPermissionOnlyUpdate(RecordGroupSharing existingShare, SharePost shareConfig) {
    // Check if we're updating exactly one sharee and only the permission changed
    if (!shareConfig.getItemsToShare().contains(existingShare.getShared().getId())) {
      return false;
    }

    if (existingShare.getSharee().isGroup()) {
      GroupSharePostItem groupItem = shareConfig.getGroupSharePostItems().get(0);

      boolean sameId = groupItem.getId().equals(existingShare.getSharee().getId());
      boolean noSharedFolderId = groupItem.getSharedFolderId() == null;
      boolean sharedFolderMatches =
          existingShare.getTargetFolder() != null
              && groupItem.getSharedFolderId().equals(existingShare.getTargetFolder().getId());

      return sameId && (noSharedFolderId || sharedFolderMatches);
    }

    // For user shares
    if (existingShare.getSharee().isUser()) {
      UserSharePostItem userItem = shareConfig.getUserSharePostItems().get(0);
      return userItem.getId().equals(existingShare.getSharee().getId());
    }

    return false;
  }

  private boolean isFolderLocationUpdate(RecordGroupSharing existingShare, SharePost shareConfig) {
    if (!existingShare.getSharee().isGroup()) {
      return false;
    }

    GroupSharePostItem groupItem = shareConfig.getGroupSharePostItems().get(0);
    if (!groupItem.getId().equals(existingShare.getSharee().getId())
        || shareConfig.getItemsToShare().size() != 1
        || !shareConfig.getItemsToShare().contains(existingShare.getShared().getId())) {
      return false;
    }

    // Check if only folder changed
    Long currentFolderId =
        existingShare.getTargetFolder() != null ? existingShare.getTargetFolder().getId() : null;
    return !Objects.equals(currentFolderId, groupItem.getSharedFolderId());
  }

  /** Updates only the permission of an existing share */
  private ApiSharingResult updatePermissionsOnly(
      RecordGroupSharing existingShare, SharePost shareConfig, User user) {

    String newPermission = extractNewPermission(shareConfig, existingShare);

    ErrorList errors =
        recordShareMgr.updatePermissionForRecord(
            existingShare.getId(), newPermission.toUpperCase(), user.getUsername());

    if (errors != null && errors.hasErrorMessages()) {
      throw new IllegalArgumentException(
          "Could not update permission: " + errors.getAllErrorMessagesAsStringsSeparatedBy(", "));
    }

    // Return updated share information
    RecordGroupSharing updatedShare = recordShareMgr.get(existingShare.getId());
    return new ApiSharingResult(List.of(new ApiShareInfo(updatedShare)), List.of());
  }

  /** Updates the folder location for group shares */
  private ApiSharingResult updateFolderLocation(
      RecordGroupSharing existingShare, SharePost shareConfig, User user) {

    GroupSharePostItem groupItem = shareConfig.getGroupSharePostItems().get(0);
    Long newFolderId = groupItem.getSharedFolderId();

    try {
      // Get the new target folder
      Folder newTargetFolder = null;
      if (newFolderId != null) {
        newTargetFolder = folderDao.get(newFolderId);
        if (newTargetFolder == null) {
          throw new IllegalArgumentException("Target folder with id " + newFolderId + " not found");
        }
      }

      // Update the target folder in the sharing record
      existingShare.setTargetFolder(newTargetFolder);
      recordGroupSharingDao.save(existingShare);

      return new ApiSharingResult(List.of(new ApiShareInfo(existingShare)), List.of());

    } catch (Exception e) {
      log.error(
          "Failed to update folder location for share {}: {}",
          existingShare.getId(),
          e.getMessage());
      throw new IllegalArgumentException("Could not update folder location: " + e.getMessage());
    }
  }

  /** Extracts the new permission from the share configuration */
  private String extractNewPermission(SharePost shareConfig, RecordGroupSharing existingShare) {
    if (existingShare.getSharee().isGroup() && !shareConfig.getGroupSharePostItems().isEmpty()) {
      return shareConfig.getGroupSharePostItems().get(0).getPermission();
    } else if (existingShare.getSharee().isUser()
        && !shareConfig.getUserSharePostItems().isEmpty()) {
      return shareConfig.getUserSharePostItems().get(0).getPermission();
    }
    throw new IllegalArgumentException(
        "Could not determine new permission from share configuration");
  }

  @Override
  public ApiShareSearchResult getShares(
      @Valid DocumentApiPaginationCriteria pgCrit,
      @Valid ApiGenericSearchConfig apiSrchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException {

    throwBindExceptionIfErrors(errors);
    PaginationCriteria<RecordGroupSharing> internalPgCrit =
        getPaginationCriteriaForApiSearch(pgCrit, RecordGroupSharing.class);
    configureSearch(apiSrchConfig, internalPgCrit);

    ISearchResults<RecordGroupSharing> internalShares =
        recordShareMgr.listSharedRecordsForUser(user, internalPgCrit);
    ApiShareSearchResult apiShareSearchResults = new ApiShareSearchResult();
    List<ApiShareInfo> shareInfoList = new ArrayList<>();
    convertISearchResults(
        pgCrit,
        apiSrchConfig,
        user,
        internalShares,
        apiShareSearchResults,
        shareInfoList,
        ApiShareInfo::new,
        share -> buildAndAddSelfLink(SHARE_ENDPOINT, share));
    return apiShareSearchResults;
  }

  @Override
  public List<ApiShareInfo> getAllSharesForDoc(Long docId, User user) {
    if (docId == null) {
      throw new IllegalArgumentException("Document id cannot be null");
    }
    List<RecordGroupSharing> shared = recordShareMgr.getRecordSharingInfo(docId);

    for (RecordGroupSharing rgs : shared) {
      if (rgs.getTargetFolder() == null) {
        try {
          Folder targetFolder;
          if (rgs.getSharee().isGroup()) {
            if (rgs.getShared().isSnippet()) {
              targetFolder = folderDao.getSharedSnippetFolderForGroup(rgs.getSharee().asGroup());
            } else {
              targetFolder = folderDao.getSharedFolderForGroup(rgs.getSharee().asGroup());
            }
          } else {
            targetFolder =
                folderDao.getIndividualSharedFolderForUsers(
                    rgs.getSharedBy(), rgs.getSharee().asUser(), rgs.getShared());
          }
          if (targetFolder != null) {
            rgs.setTargetFolder(targetFolder);
          }
        } catch (Exception e) {
          log.warn(
              "Failed to populate target folder for sharing {}: {}", rgs.getId(), e.getMessage());
        }
      }
    }

    return entityToSharedInfo(shared);
  }

  private void configureSearch(
      ApiGenericSearchConfig apiSrchConfig, PaginationCriteria<RecordGroupSharing> internalPgCrit) {
    if (!StringUtils.isEmpty(apiSrchConfig.getQuery())) {
      SharedRecordSearchCriteria searchCrit = new SharedRecordSearchCriteria();
      searchCrit.setAllFields(apiSrchConfig.getQuery());
      internalPgCrit.setSearchCriteria(searchCrit);
    }
  }

  private List<Long> failuresToIds(List<RecordGroupSharing> failures) {
    return failures.stream().map(f -> f.getShared().getId()).collect(toList());
  }

  private ShareConfigCommand convertApiCfgToInternalCfg(SharePost shareConfig) {
    ShareConfigElement[] internalCfg = convertApiConfigToInternalConfig(shareConfig);
    Long[] itemsToShare = new Long[shareConfig.getItemsToShare().size()];
    itemsToShare = shareConfig.getItemsToShare().toArray(itemsToShare);
    ShareConfigCommand command = new ShareConfigCommand(itemsToShare, internalCfg);
    return command;
  }

  private List<ApiShareInfo> entityToSharedInfo(List<RecordGroupSharing> shared) {
    return shared.stream().map(ApiShareInfo::new).collect(toList());
  }

  private ShareConfigElement[] convertApiConfigToInternalConfig(SharePost shareConfig) {
    ShareConfigElement[] targets = new ShareConfigElement[calculateConfigElementSize(shareConfig)];
    List<ShareConfigElement> shares =
        shareConfig.getGroupSharePostItems().stream()
            .map(this::grpSharePostToConfigElement)
            .collect(toList());
    shares.addAll(
        shareConfig.getUserSharePostItems().stream()
            .map(this::userSharePostToConfigElement)
            .collect(toList()));
    return shares.toArray(targets);
  }

  private int calculateConfigElementSize(SharePost shareConfig) {
    return shareConfig.getGroupSharePostItems().size() + shareConfig.getUserSharePostItems().size();
  }

  private ShareConfigElement grpSharePostToConfigElement(GroupSharePostItem grpSharePost) {
    ShareConfigElement el = new ShareConfigElement();
    el.setGroupid(grpSharePost.getId());
    el.setGroupFolderId(grpSharePost.getSharedFolderId());
    el.setOperation(convertPermissionString(grpSharePost.getPermission()));
    return el;
  }

  private String convertPermissionString(String permString) {
    if (isBlank(permString)) {
      return "read";
    }
    return permString.toLowerCase().equals("edit") ? "write" : "read";
  }

  private ShareConfigElement userSharePostToConfigElement(UserSharePostItem userSharePost) {
    ShareConfigElement el = new ShareConfigElement();
    el.setUserId(userSharePost.getId());
    el.setOperation(convertPermissionString(userSharePost.getPermission()));
    return el;
  }
}
