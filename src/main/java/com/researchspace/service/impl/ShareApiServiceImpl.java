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
import com.researchspace.auth.PermissionUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.dtos.SharedRecordSearchCriteria;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RecordInfoSharingInfo;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.DetailedRecordInformationProvider;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.ShareApiService;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.mapping.DocumentSharesBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindException;

@Service
public class ShareApiServiceImpl extends BaseApiController implements ShareApiService {

  private static final Logger log = LoggerFactory.getLogger(ShareApiServiceImpl.class);
  private static final String SHARE_ENDPOINT = "/share";

  private final SharingHandler recordShareHandler;
  private final RecordSharingManager recordShareMgr;
  private final DetailedRecordInformationProvider detailedRecordInformationProvider;
  private final RecordManager recordManager;
  private final DocumentSharesBuilder documentSharesBuilder;
  private final PermissionUtils permissionUtils;

  public ShareApiServiceImpl(
      SharingHandler recordShareHandler,
      RecordSharingManager recordShareMgr,
      DetailedRecordInformationProvider detailedRecordInformationProvider,
      RecordManager recordManager,
      DocumentSharesBuilder documentSharesBuilder,
      PermissionUtils permissionUtils) {
    this.recordShareHandler = recordShareHandler;
    this.recordShareMgr = recordShareMgr;
    this.detailedRecordInformationProvider = detailedRecordInformationProvider;
    this.recordManager = recordManager;
    this.documentSharesBuilder = documentSharesBuilder;
    this.permissionUtils = permissionUtils;
  }

  @Override
  public ApiSharingResult shareItems(SharePost shareConfig, User user) {
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
    assertUserHasSharePermission(id, user);
    try {
      recordShareHandler.unshare(id, user);
    } catch (DataAccessException e) {
      throw new NotFoundException(createNotFoundMessage("Share", id), e);
    }
  }

  private void assertUserHasSharePermission(Long id, User user) {
    RecordGroupSharing rgs;
    try {
      rgs = recordShareMgr.get(id);
      permissionUtils.assertIsPermitted(rgs.getShared(), PermissionType.SHARE, user, "unshare doc");
    } catch (ObjectRetrievalFailureException | AuthorizationException e) {
      throw new NotFoundException(createNotFoundMessage("Share", id), e);
    }
  }

  @Override
  public void updateShare(SharePermissionUpdate permissionUpdate, User user) throws BindException {
    Long id = permissionUpdate.getShareId();
    assertUserHasSharePermission(id, user);

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
    if (record == null
        || record.getOwner() == null
        || !record.getOwner().getId().equals(user.getId())) {
      throw new NotFoundException(createNotFoundMessage("Record", docId));
    }
    RecordInfoSharingInfo sharingInfo =
        detailedRecordInformationProvider.getRecordSharingInfo(record);
    return documentSharesBuilder.assemble(record, sharingInfo);
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

    if (result.getExceptionCount() > 0 && result.getResultCount() == 0) {
      String exceptionMessages =
          result.getExceptions().stream().map(Throwable::getMessage).collect(Collectors.joining());
      if (result.getExceptions().stream().anyMatch(e -> e instanceof IllegalAddChildOperation)) {
        throw new IllegalArgumentException("Problem sharing: " + exceptionMessages);
      } else {
        throw new RuntimeException("Problem sharing: " + exceptionMessages);
      }
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
    return permString.equalsIgnoreCase("edit") ? "write" : "read";
  }

  private ShareConfigElement userSharePostToConfigElement(UserSharePostItem userSharePost) {
    ShareConfigElement el = new ShareConfigElement();
    el.setUserId(userSharePost.getId());
    el.setOperation(convertPermissionString(userSharePost.getPermission()));
    return el;
  }
}
