package com.researchspace.service.impl;

import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.ShareRecordAuditEvent;
import com.researchspace.model.dtos.ShareConfigCommand;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.permissions.ConstraintBasedPermission;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.IdConstraint;
import com.researchspace.model.permissions.PermissionDomain;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.GroupManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.SharingHandler;
import com.researchspace.service.UserManager;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;

@Slf4j
public class SharingHandlerImpl implements SharingHandler {

  private @Autowired AuditTrailService auditService;
  private @Autowired RecordSharingManager sharingManager;
  private @Autowired UserManager userManager;
  private @Autowired GroupManager groupManager;
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
}
