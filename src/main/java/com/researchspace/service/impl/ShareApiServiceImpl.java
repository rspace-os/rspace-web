package com.researchspace.service.impl;

import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import com.researchspace.api.v1.controller.ApiGenericSearchConfig;
import com.researchspace.api.v1.controller.BaseApiController;
import com.researchspace.api.v1.controller.DocumentApiPaginationCriteria;
import com.researchspace.api.v1.model.ApiShareInfo;
import com.researchspace.api.v1.model.ApiShareSearchResult;
import com.researchspace.api.v1.model.ApiSharingResult;
import com.researchspace.api.v1.model.DocumentShares;
import com.researchspace.api.v1.model.GroupSharePostItem;
import com.researchspace.api.v1.model.SharePermissionUpdate;
import com.researchspace.api.v1.model.SharePost;
import com.researchspace.api.v1.model.UserSharePostItem;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.FolderDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.dtos.SharedRecordSearchCriteria;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RecordInfoSharingInfo;
import com.researchspace.model.record.RecordToFolder;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.DetailedRecordInformationProvider;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.ShareApiService;
import com.researchspace.service.SharingHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindException;

@Service
@Transactional
public class ShareApiServiceImpl extends BaseApiController implements ShareApiService {

  private static final Logger log = LoggerFactory.getLogger(ShareApiServiceImpl.class);
  private static final String SHARE_ENDPOINT = "/share";

  @Autowired private SharingHandler recordShareHandler;
  @Autowired private RecordSharingManager recordShareMgr;
  @Autowired private FolderDao folderDao;
  @Autowired private DetailedRecordInformationProvider recordInformationProvider;
  @Autowired private DetailedRecordInformationProvider detailedRecordInformationProvider;
  @Autowired private RecordManager recordManager;

  @Override
  public ApiSharingResult shareItems(SharePost shareConfig, User user) throws BindException {
    ShareConfigCommand command = convertApiCfgToInternalCfg(shareConfig);
    initializeNullCollections(shareConfig);

    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result =
        recordShareHandler.shareRecords(command, user);

    log.info(
        "Success: {}, Failures: {}, Exception :{}",
        result.getResultCount(),
        result.getFailureCount(),
        result.getExceptionCount());

    validateSharingResult(result);

    return new ApiSharingResult(
        entityToSharedInfo(result.getResults()), failuresToIds(result.getFailures()));
  }

  @Override
  public void deleteShare(Long id, User user) {
    try {
      recordShareHandler.unshare(id, user);
    } catch (DataAccessException e) {
      throw new NotFoundException(createNotFoundMessage("Share", id), e);
    }
  }

  @Override
  public void updateShare(SharePermissionUpdate permissionUpdate, User user) throws BindException {
    Long id = permissionUpdate.getShareId();

    ErrorList errors =
        recordShareMgr.updatePermissionForRecord(
            id, permissionUpdate.getPermission().toUpperCase(), user.getUsername());

    if (errors != null && errors.hasErrorMessages()) {
      throw new IllegalArgumentException(
          "Could not update permission: " + errors.getAllErrorMessagesAsStringsSeparatedBy(", "));
    }
  }

  @Override
  public ApiShareSearchResult getShares(
      DocumentApiPaginationCriteria pgCrit, ApiGenericSearchConfig apiSrchConfig, User user)
      throws BindException {
    PaginationCriteria<RecordGroupSharing> internalPgCrit =
        createInternalPaginationCriteria(pgCrit);
    configureSearch(apiSrchConfig, internalPgCrit);

    ISearchResults<RecordGroupSharing> internalShares =
        recordShareMgr.listSharedRecordsForUser(user, internalPgCrit);

    return buildApiShareSearchResult(pgCrit, apiSrchConfig, user, internalShares);
  }

  @Override
  public DocumentShares getAllSharesForDoc(Long docId, User user) {
    if (docId == null) {
      throw new IllegalArgumentException("Document id cannot be null");
    }
    BaseRecord record = recordManager.get(docId);
    RecordInfoSharingInfo sharingInfo =
        detailedRecordInformationProvider.getRecordSharingInfo(record);
    return buildDocumentShares(record, sharingInfo);
  }

  private DocumentShares buildDocumentShares(BaseRecord record, RecordInfoSharingInfo sharingInfo) {
    return DocumentShares.builder()
        .sharedDocId(record.getId())
        .sharedDocName(record.getName())
        .directShares(buildShares(record, sharingInfo.getDirectShares()))
        .notebookShares(buildShares(record, sharingInfo.getImplicitShares()))
        .build();
  }

  private List<DocumentShares.Share> buildShares(
      BaseRecord record, List<RecordGroupSharing> shares) {
    return shares.stream()
        .map(
            share -> {
              boolean isUserShare = share.getSharee().isUser();
              BaseRecord sharedLocation = getSharedLocation(share, record);
              Long locationId = sharedLocation != null ? sharedLocation.getId() : 0L;
              String locationName = sharedLocation != null ? sharedLocation.getName() : null;
              return DocumentShares.Share.builder()
                  .shareId(share.getId())
                  .sharerId(share.getSharedBy().getId())
                  .sharerName(share.getSharedBy().getDisplayName())
                  .recipientName(share.getSharee().getDisplayName())
                  .recipientType(
                      isUserShare
                          ? DocumentShares.RecipientType.USER
                          : DocumentShares.RecipientType.GROUP)
                  .permission(
                      PermissionType.WRITE.equals(share.getPermType())
                          ? DocumentShares.PermissionType.EDIT
                          : DocumentShares.PermissionType.READ)
                  .locationId(locationId)
                  .locationName(locationName)
                  .build();
            })
        .collect(Collectors.toList());
  }

  private BaseRecord getSharedLocation(RecordGroupSharing rgs, BaseRecord record) {
    if (rgs.getShared().isNotebook()) {
      List<RecordToFolder> parentNotebooks =
          record.getParents().stream()
              .filter(r -> r.getFolder().isNotebook())
              .collect(Collectors.toList());
      return parentNotebooks.isEmpty() ? null : parentNotebooks.get(0).getFolder();
    }

    List<RecordToFolder> parentSharedFolders =
        record.getParents().stream()
            .filter(r -> r.getFolder().isSharedFolder())
            .collect(Collectors.toList());

    if (rgs.getSharee().isGroup()) {
      Folder groupRoot = folderDao.getSharedFolderForGroup(rgs.getSharee().asGroup());
        return rgs.getShared().getParents().stream()
            .map(RecordToFolder::getFolder)
            .filter(folder -> folder.getAllAncestors().contains(groupRoot) || folder.equals(groupRoot))
            .findFirst()
            .orElse(null);
    }

    for (RecordToFolder rtf : parentSharedFolders) {
      if (isSharedFolderBetweenSharerAndRecipient(rgs, rtf)) {
        return rtf.getFolder();
      }
    }
    return null;
  }

  /**
   * Checks if the given RecordToFolder represents a shared folder between the sharer and recipient,
   * by checking it's a shared folder named in the format "user1-user2"
   *
   * @param rgs The RecordGroupSharing object.
   * @param rtf The RecordToFolder object.
   * @return True if the folder is shared between the sharer and recipient, false otherwise.
   */
  private static boolean isSharedFolderBetweenSharerAndRecipient(
      RecordGroupSharing rgs, RecordToFolder rtf) {
    String sharerUsername = rgs.getSharedBy().getUsername();
    String recipientUsername = rgs.getSharee().asUser().getUsername();
    if (!rtf.getFolder().isSharedFolder() || !rtf.getFolder().getName().contains("-")) {
      return false;
    }

    String[] parts = rtf.getFolder().getName().split("-", 2);
    if (parts.length != 2) {
      return false;
    }

    Set<String> folderUsers = Set.of(parts[0], parts[1]);
    return folderUsers.contains(sharerUsername) && folderUsers.contains(recipientUsername);
  }

  private void initializeNullCollections(SharePost shareConfig) {
    if (shareConfig.getGroupSharePostItems() == null) {
      shareConfig.setGroupSharePostItems(new ArrayList<>());
    }
    if (shareConfig.getUserSharePostItems() == null) {
      shareConfig.setUserSharePostItems(new ArrayList<>());
    }
  }

  private void validateSharingResult(
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result) {
    if (allSharesFailedWithAuthException(result)) {
      throw new AuthorizationException(
          "No permissions to share items with group  - ensure that the sharer is the owner of the"
              + " items to be shared. Only Notebooks and Documents can be shared.");
    }
  }

  private boolean allSharesFailedWithAuthException(
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result) {
    return result.getExceptionCount() > 0
        && result.getResultCount() == 0
        && result.getExceptions().stream().allMatch(e -> e instanceof AuthorizationException);
  }

  private PaginationCriteria<RecordGroupSharing> createInternalPaginationCriteria(
      DocumentApiPaginationCriteria pgCrit) {
    return getPaginationCriteriaForApiSearch(pgCrit, RecordGroupSharing.class);
  }

  private void configureSearch(
      ApiGenericSearchConfig apiSrchConfig, PaginationCriteria<RecordGroupSharing> internalPgCrit) {
    if (!StringUtils.isEmpty(apiSrchConfig.getQuery())) {
      SharedRecordSearchCriteria searchCrit = new SharedRecordSearchCriteria();
      searchCrit.setAllFields(apiSrchConfig.getQuery());
      internalPgCrit.setSearchCriteria(searchCrit);
    }
  }

  private ApiShareSearchResult buildApiShareSearchResult(
      DocumentApiPaginationCriteria pgCrit,
      ApiGenericSearchConfig apiSrchConfig,
      User user,
      ISearchResults<RecordGroupSharing> internalShares) {

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

  private void populateTargetFolders(List<RecordGroupSharing> shared) {
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
