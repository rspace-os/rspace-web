package com.researchspace.service.impl;

import static org.apache.commons.collections4.ListUtils.removeAll;

import com.researchspace.dao.GroupMembershipEventDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.RecordGroupSharingDao;
import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.ShareRecordAuditEvent;
import com.researchspace.model.comms.NotificationType;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.events.GroupEventType;
import com.researchspace.model.events.GroupMembershipEvent;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.service.AutoshareManager;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.SharingHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AutoshareManagerImpl implements AutoshareManager {

  private @Autowired RecordSharingManager recordSharingMgr;
  private @Autowired RecordGroupSharingDao sharingDao;
  private @Autowired GroupMembershipEventDao groupMembershipEventDao;
  private @Autowired RecordDao recordDao;
  private @Autowired AuditTrailService auditService;
  private @Autowired SharingHandler sharingHandler;
  private @Autowired CommunicationManager communicationManager;

  private final Map<Long, Boolean> usersWithBulkShareInProgress;

  public AutoshareManagerImpl() {
    this.usersWithBulkShareInProgress = new ConcurrentHashMap<>();
  }

  @Override
  public boolean isBulkShareInProgress(User subject) {
    return usersWithBulkShareInProgress.containsKey(subject.getId());
  }

  private void setBulkSharingInProgress(User subject) {
    if (isBulkShareInProgress(subject)) {
      throw new IllegalStateException("Cannot launch new bulk share operation; already running");
    }
    usersWithBulkShareInProgress.put(subject.getId(), Boolean.TRUE);
  }

  private void setBulkSharingFinished(User subject) {
    usersWithBulkShareInProgress.remove(subject.getId());
  }

  /**
   * Performs sharing a single record in a new transaction. This is needed in case this is called
   * from a TransactionEventListener to ensure clean separation between a creation event and an
   * autoshare event, and ensure creation occurs OK even if there is a subsequent problem in
   * sharing.
   */
  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ServiceOperationResult<Set<RecordGroupSharing>> shareRecord(
      BaseRecord toShare, User subject) {

    if (!toShare.isAutosharable()) {
      String message =
          String.format(
              "%s (%s) cannot be autoshared: autosharing must be enabled and item must be a"
                  + " document or notebook",
              toShare.getName(), toShare.getGlobalIdentifier());

      return new ServiceOperationResult<>(null, false, message);
    }

    Set<Group> autoShareTargets = toShare.getOwner().getAutoshareGroups();
    List<ShareConfigElement> shareConfigsList = new ArrayList<>();

    for (Group grp : autoShareTargets) {
      // we might be in a shared notebook or already shared, so skip autosharing
      if (toShare.isDocumentInSharedNotebook(grp)) {
        log.info(
            "{} - {} is already in a shared notebook, not autosharing again",
            toShare.getName(),
            toShare.getGlobalIdentifier());
        continue;
      }
      Long target = grp.getUserGroupForUser(subject).getAutoShareFolder().getId();
      ShareConfigElement element = createShareConfig(grp, target);
      shareConfigsList.add(element);
    }
    ServiceOperationResult<Set<RecordGroupSharing>> shareResult;

    // share, if there is something to share
    if (shareConfigsList.size() > 0) {
      ShareConfigElement[] configs = new ShareConfigElement[shareConfigsList.size()];
      shareConfigsList.toArray(configs);
      // let sharing manager decide whether to share or not
      shareResult = recordSharingMgr.shareRecord(subject, toShare.getId(), configs);
      if (shareResult.isSucceeded()) {
        RecordGroupSharing rgs = shareResult.getEntity().iterator().next();
        notifyAuditTrail(subject, configs, rgs);
      }
    } else {
      String msgString =
          String.format(
              "'%s' - (%s) not autoshared - perhaps is already in shared notebook",
              toShare.getName(), toShare.getGlobalIdentifier());
      log.warn(msgString);
      shareResult = new ServiceOperationResult<>(null, false, msgString);
    }
    return shareResult;
  }

  private ShareConfigElement createShareConfig(Group grp, Long target) {
    ShareConfigElement element = new ShareConfigElement(grp.getId(), "read");
    element.setGroupFolderId(target);
    element.setAutoshare(true);
    return element;
  }

  private void notifyAuditTrail(
      User subject, ShareConfigElement[] configs, RecordGroupSharing rgs) {
    auditService.notify(new ShareRecordAuditEvent(subject, rgs.getShared(), configs));
  }

  @Override
  public ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing>
      bulkShareAllRecords(User subject, Group grpToShareWith, Folder destinationFolder) {

    setBulkSharingInProgress(subject);

    try {
      List<Long> notebooksToShareList = findUnsharedNotebooks(subject, grpToShareWith);
      log.info("Sharing {} notebooks for {}", notebooksToShareList.size(), subject.getUsername());
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> notebookResult =
          doSharing(subject, grpToShareWith, notebooksToShareList, destinationFolder);

      List<Long> docsToShareList = findUnsharedDocs(subject, grpToShareWith);
      log.info("Sharing {} documents for {}", docsToShareList.size(), subject.getUsername());
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> docResult =
          doSharing(subject, grpToShareWith, docsToShareList, destinationFolder);

      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> res =
          notebookResult.merge(docResult);

      notifyOtherGroupMembers(subject, grpToShareWith, true, res);

      return res;
    } finally {
      setBulkSharingFinished(subject);
    }
  }

  private List<Long> findUnsharedNotebooks(User subject, Group grpToShareWith) {
    List<Long> allSharedItems = sharingDao.getRecordIdSharedByUserToGroup(subject, grpToShareWith);
    List<Long> allNotebookIds = recordDao.getAllNotebookIdsOwnedByUser(subject);

    return ListUtils.removeAll(allNotebookIds, allSharedItems);
  }

  private List<Long> findUnsharedDocs(User subject, Group grpToShareWith) {
    List<Long> allDocs = recordDao.getAllNonTemplateNonTemporaryStrucDocIdsOwnedByUser(subject);
    List<Long> allSharedItems = sharingDao.getRecordIdSharedByUserToGroup(subject, grpToShareWith);
    List<Long> allDocsInNotebook = recordDao.getAllDocumentIdsInNotebooksForUser(subject);

    // Documents in notebooks are shared by findUnsharedNotebooks(...)
    return removeAll(removeAll(allDocs, allSharedItems), allDocsInNotebook);
  }

  private ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> doSharing(
      User subject, Group grpToShareWith, List<Long> itemsToShare, Folder destinationFolder) {

    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> rc =
        new ServiceOperationResultCollection<>();

    for (Long idLong : itemsToShare) {
      ShareConfigElement element = createShareConfig(grpToShareWith, destinationFolder.getId());
      ShareConfigElement[] configs = new ShareConfigElement[] {element};

      try {
        ServiceOperationResult<Set<RecordGroupSharing>> sharingResult =
            recordSharingMgr.shareRecord(subject, idLong, configs);
        if (sharingResult.isSucceeded()) {
          RecordGroupSharing rgs = sharingResult.getEntity().iterator().next();
          notifyAuditTrail(subject, configs, rgs);
          rc.addResult(rgs);
        } else {
          if (!sharingResult.getEntity().isEmpty()) {
            rc.addFailure(sharingResult.getEntity().iterator().next());
          }
        }
      } catch (Exception e) {
        log.error(e.getMessage());
        rc.addException(e);
      }
    }
    return rc;
  }

  @Override
  public ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing>
      bulkUnshareAllRecords(User subject, Group grpToUnshareFrom) {

    setBulkSharingInProgress(subject);

    try {
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> res =
          sharingHandler.unshareAllWithGroup(subject, grpToUnshareFrom, false);
      notifyOtherGroupMembers(subject, grpToUnshareFrom, false, res);
      return res;
    } finally {
      setBulkSharingFinished(subject);
    }
  }

  private void notifyOtherGroupMembers(
      User subject,
      Group targetGroup,
      Boolean autoshareStatus,
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> res) {

    String enabledOrDisabled = (autoshareStatus) ? "enabled" : "disabled";
    String sharedOrUnshared = (autoshareStatus) ? "shared" : "unshared";

    if (res.isAllSucceeded()) {
      for (User groupMember : targetGroup.getMembers()) {
        if (!groupMember.equals(subject)) {

          sysnotify(
              groupMember,
              String.format(
                  "%s has %s autosharing into group '%s' and %s all their work",
                  subject.getFullName(),
                  enabledOrDisabled,
                  targetGroup.getDisplayName(),
                  sharedOrUnshared));
        }
      }
    }
  }

  private void sysnotify(User subject, String message) {
    communicationManager.systemNotify(
        NotificationType.PROCESS_COMPLETED, message, subject.getUsername(), true);
  }

  @Override
  public Future<ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing>>
      asyncBulkShareAllRecords(User subject, Group grpToShareWith, Folder destinationFolder) {
    return setAsyncBulkShare(true, subject, grpToShareWith, destinationFolder);
  }

  @Override
  public Future<ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing>>
      asyncBulkUnshareAllRecords(User subject, Group grpToShareWith) {
    return setAsyncBulkShare(false, subject, grpToShareWith, null);
  }

  private Future<ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing>>
      setAsyncBulkShare(
          Boolean targetAutoshareStatus,
          User subject,
          Group grpToShareWith,
          Folder destinationFolder) {

    ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result;
    if (targetAutoshareStatus) {
      result = bulkShareAllRecords(subject, grpToShareWith, destinationFolder);
    } else {
      result = bulkUnshareAllRecords(subject, grpToShareWith);
    }

    String notificationMessage = createNotificationMessage(targetAutoshareStatus, result);
    sysnotify(subject, notificationMessage);

    return new AsyncResult<>(result);
  }

  public String createNotificationMessage(
      Boolean targetAutoshareStatus,
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result) {

    String notificationMessage;

    String enabledOrDisabled = targetAutoshareStatus ? "enabled" : "disabled";
    String previousStatus = targetAutoshareStatus ? "unshared" : "shared";
    String currentStatus = targetAutoshareStatus ? "shared" : "unshared";

    if (result.isAllSucceeded()) {
      notificationMessage =
          String.format(
              "Automatic sharing is now %s. All your previously %s work is now %s.",
              enabledOrDisabled, previousStatus, currentStatus);
    } else if (result.getFailureCount() > 0 && result.getExceptionCount() == 0) {
      String failures =
          result.getFailures().stream()
              .map(RecordGroupSharing::getShared)
              .map(br -> br.getName() + " - " + br.getGlobalIdentifier())
              .collect(Collectors.joining(","));
      notificationMessage =
          String.format(
              "Automatic sharing is now %s. There was a problem, some of your previously %s work is"
                  + " still %s - %s.",
              enabledOrDisabled,
              previousStatus,
              previousStatus,
              StringUtils.abbreviate(failures, 255));
    } else {
      String exceptions =
          result.getExceptions().stream()
              .map(Exception::getMessage)
              .collect(Collectors.joining(","));
      notificationMessage =
          String.format(
              "Setting automatic sharing to %s may have failed. Some of your previously %s work is"
                  + " still %s - %s.",
              enabledOrDisabled,
              previousStatus,
              previousStatus,
              StringUtils.abbreviate(exceptions, 255));
    }

    return notificationMessage;
  }

  @Override
  public void logGroupAutoshareStatusChange(
      Group group, User subject, Boolean targetAutoshareStatus) {
    groupMembershipEventDao.save(
        new GroupMembershipEvent(
            subject,
            group,
            (targetAutoshareStatus)
                ? GroupEventType.ENABLED_GROUP_AUTOSHARING
                : GroupEventType.DISABLED_GROUP_AUTOSHARING));
  }
}
