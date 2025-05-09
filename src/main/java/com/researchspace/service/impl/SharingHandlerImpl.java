package com.researchspace.service.impl;

import static com.researchspace.service.RecordDeletionManager.MIN_PATH_LENGTH_TOSHARED_ROOT_FOLDER;

import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.ShareRecordAuditEvent;
import com.researchspace.model.dto.SharingResult;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.IdConstraint;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.FolderManager;
import com.researchspace.service.GroupManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.UserManager;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.Validate;
import org.apache.shiro.authz.AuthorizationException;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public class SharingHandlerImpl implements SharingHandler {

  private @Autowired AuditTrailService auditService;
  private @Autowired RecordSharingManager sharingManager;
  private @Autowired UserManager userManager;
  private @Autowired GroupManager groupManager;
  private @Autowired FolderManager folderManager;
  private @Autowired IPermissionUtils permissionUtils;

  @Override
  public ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> shareRecords(
      ShareConfigCommand shareConfig, User sharer) {

    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> rc =
        new ServiceOperationResultCollection<>();
    for (Long id : shareConfig.getIdsToShare()) {
      try {
        ServiceOperationResult<List<RecordGroupSharing>> sharingResult =
            sharingManager.shareRecord(sharer, id, shareConfig.getValues());
        if (sharingResult.isSucceeded()) {
          RecordGroupSharing rgs = sharingResult.getEntity().get(0);
          auditService.notify(
              new ShareRecordAuditEvent(sharer, rgs.getShared(), shareConfig.getValues()));
          rc.addResult(rgs);
        } else {
          if (!sharingResult.getEntity().isEmpty()) {
            rc.addFailure(sharingResult.getEntity().get(0));
          }
        }
      } catch (IllegalAddChildOperation | AuthorizationException | IllegalArgumentException e) {
        log.error(e.getMessage());
        rc.addException(e);
      }
    }

    log.debug(
        "shared with ids= {}, records= {}",
        Arrays.toString(shareConfig.getIdsToShare()),
        Arrays.toString(shareConfig.getIdsToShare()));

    for (ShareConfigElement elem : shareConfig.getValues()) {
      AbstractUserOrGroupImpl userORGroup = null;
      if (elem.getGroupid() != null) {
        userORGroup = groupManager.getGroup(elem.getGroupid());
      } else if (elem.getUserId() != null) {
        userORGroup = userManager.get(elem.getUserId());
      }
      permissionUtils.notifyUserOrGroupToRefreshCache(userORGroup);
    }
    return rc;
  }

  @Override
  public ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing>
      shareIntoSharedFolder(User user, Folder sharedFolder, Long recordId) {
    Optional<Folder> sharedFolderRoot =
        folderManager.getGroupOrIndividualShrdFolderRootFromSharedSubfolder(
            sharedFolder.getId(), user);
    RSPath path = folderManager.getShortestPathToSharedRootFolder(sharedFolder.getId(), user);
    if (path.isEmpty() || path.size() <= MIN_PATH_LENGTH_TOSHARED_ROOT_FOLDER) {
      String msg =
          String.format(
              "The folder '%s' is not a shared subfolder - id [%d] is not in a shared folder!",
              sharedFolder.getName(), sharedFolder.getId());
      throw new IllegalArgumentException(msg);
    }

    Group sharedGroup =
        groupManager.getGroupByCommunalGroupFolderId(sharedFolderRoot.get().getId());
    ShareConfigElement shareConfigElement = new ShareConfigElement(sharedGroup.getId(), "write");
    shareConfigElement.setGroupFolderId(sharedFolder.getId());
    ShareConfigCommand shareConfig =
        new ShareConfigCommand(
            new Long[] {recordId}, new ShareConfigElement[] {shareConfigElement});
    return this.shareRecords(shareConfig, user);
  }

  @Override
  public ServiceOperationResult<RecordGroupSharing> unshare(Long recordGroupShareId, User subject) {
    RecordGroupSharing rgs = sharingManager.get(recordGroupShareId);
    return doUnshare(rgs, subject, true);
  }

  private ServiceOperationResult<RecordGroupSharing> doUnshare(
      RecordGroupSharing rgs, User subject, boolean notify) {
    AbstractUserOrGroupImpl userOrGroup = rgs.getSharee();
    ConstraintBasedPermission toUpdate =
        permissionUtils.findBy(
            userOrGroup.getPermissions(),
            PermissionDomain.RECORD,
            new IdConstraint(rgs.getShared().getId()));

    ShareConfigElement configEl =
        new ShareConfigElement(rgs, toUpdate.getActions().iterator().next().toString());
    configEl.setAutoshare(!notify);
    sharingManager.unshareRecord(
        subject, rgs.getShared().getId(), new ShareConfigElement[] {configEl});
    auditService.notify(new GenericEvent(subject, rgs, AuditAction.UNSHARE));
    return new ServiceOperationResult<>(rgs, true);
  }

  @Override
  public ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing>
      unshareAllWithGroup(User subject, Group group, boolean notify) {

    List<RecordGroupSharing> allShares =
        sharingManager.getSharedRecordsForUserAndGroup(subject, group);
    log.info(
        "Bulk unsharing of {} share items for user {}", allShares.size(), subject.getUsername());
    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> rc =
        new ServiceOperationResultCollection<>();

    for (RecordGroupSharing rgs : allShares) {
      try {
        ServiceOperationResult<RecordGroupSharing> unshared = doUnshare(rgs, subject, notify);
        if (unshared.isSucceeded()) {
          rc.addResult(rgs);
        } else {
          rc.addFailure(rgs);
        }
      } catch (Exception e) {
        log.error("Unexpected exception during bulk unshare: {}", e.getMessage());
        rc.addException(e);
      }
    }

    return rc;
  }

  @Override
  public SharingResult shareRecordsWithResult(ShareConfigCommand shareConfig, User sharer) {
    ErrorList error = new ErrorList();
    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result =
        this.shareRecords(shareConfig, sharer);
    result.getExceptions().forEach(e -> error.addErrorMsg(e.getMessage()));
    result.getFailures().forEach(rgs -> error.addErrorMsg(rgs.getShared().getName()));
    return new SharingResult(buildSharedIdList(result), buildPublicLinkList(result), error);
  }

  @Override
  @Transactional
  public SharingResult moveIntoSharedNotebook(
      Group group, BaseRecord baseRecordToMove, Notebook targetSharedNotebook) {
    Validate.isTrue(
        targetSharedNotebook != null && targetSharedNotebook.isShared(),
        "Notebook must be already shared");

    if (baseRecordToMove.isShared()) {
      RSPath pathToRootSharedFolder =
          folderManager.getShortestPathToSharedRootFolder(
              targetSharedNotebook.getId(), baseRecordToMove.getOwner());
      sharingManager.unshareFromSharedFolder(
          baseRecordToMove.getOwner(), baseRecordToMove, pathToRootSharedFolder);
    }
    ShareConfigCommand shareConfig =
        buildShareCommandForTarget(baseRecordToMove.getId(), group.getId(), targetSharedNotebook);
    SharingResult sharingResult = shareRecordsWithResult(shareConfig, baseRecordToMove.getOwner());
    if (sharingResult.getError().hasErrorMessages()) {
      throw new IllegalStateException(
          "Errors while moving into Shared Notebook: ["
              + sharingResult.getError().getAllErrorMessagesAsStringsSeparatedBy(",")
              + "]");
    }
    return sharingResult;
  }

  @NotNull
  private static ShareConfigCommand buildShareCommandForTarget(
      Long idToMove, Long groupId, Folder target) {
    ShareConfigElement shareElement = new ShareConfigElement(groupId, "read");
    shareElement.setGroupFolderId(target.getId());
    return new ShareConfigCommand(
        new Long[] {idToMove}, new ShareConfigElement[] {shareElement}, false);
  }

  @NotNull
  private static List<Long> buildSharedIdList(
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result) {
    List<Long> sharedIds =
        result.getResults().stream()
            .map(rgs -> rgs.getShared().getId())
            .collect(Collectors.toList());
    return sharedIds;
  }

  @NotNull
  private static List<String> buildPublicLinkList(
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result) {
    List<String> publicLinks =
        result.getResults().stream()
            .map(
                rgs -> {
                  if (rgs.getPublicLink() == null) {
                    return null;
                  }
                  String prefix = "";
                  if (rgs.getShared().isStructuredDocument()) {
                    prefix = "/public/publishedView/document/";
                  } else if (rgs.getShared().isNotebook()) {
                    prefix = "/public/publishedView/notebook/";
                  }
                  return rgs.getShared().getName() + "_&_&_" + prefix + rgs.getPublicLink();
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    return publicLinks;
  }
}
