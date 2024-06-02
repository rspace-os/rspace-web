package com.researchspace.service;

import com.researchspace.model.Group;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.model.views.ServiceOperationResultCollection;
import java.util.Set;
import java.util.concurrent.Future;
import org.springframework.scheduling.annotation.Async;

/**
 * There are two ways to enable autosharing - on user and group levels. Autosharing on user level
 * enables autosharing for some user in some group. Autosharing on group level enables autosharing
 * for all non-pi users in some group.
 */
public interface AutoshareManager {

  /**
   * Shares item into autoshare folder, creating the folder if need be.
   *
   * <p>This method should be used to autoshare when a document is created. It shouldn't be used for
   * bulk autosharing (e.g. scanning and updating all shared items)
   */
  ServiceOperationResult<Set<RecordGroupSharing>> shareRecord(BaseRecord toShare, User subject);

  /**
   * Shares all previously unshared notebooks and documents into the user's autoshare target folder.
   * This should not be called for regular sharing, only for retrospectively sharing after enabling
   * autoshare.
   *
   * @throws IllegalArgumentException if toShare is not set up to autoshare with group.
   * @throws IllegalStateException if this method is already in progress.
   */
  ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> bulkShareAllRecords(
      User subject, Group grpToShareWith, Folder destinationFolder);

  /**
   * Unshares all work that has been shared with a group.
   *
   * @throws IllegalStateException if this method is already in progress.
   */
  ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> bulkUnshareAllRecords(
      User subject, Group grpToShareWith);

  /** Create a notification message based on synchronous bulk share/unshare result. */
  String createNotificationMessage(
      Boolean targetAutoshareStatus,
      ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing> result);

  /**
   * Query whether bulk autosharing for a user is in progress at this moment.
   *
   * @return <code>true</code> if a bulk autoshare is already running; <code>false</code> otherwise.
   */
  boolean isBulkShareInProgress(User subject);

  /**
   * Asynchronous bulk share. Send a notification to the user once done.
   *
   * @throws IllegalStateException if this method is already in progress.
   */
  @Async(value = "archiveTaskExecutor")
  Future<ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing>>
      asyncBulkShareAllRecords(User subject, Group grpToShareWith, Folder destinationFolder);

  /** Asynchronous bulk unshare. Send a notification to the user once done. */
  @Async(value = "archiveTaskExecutor")
  Future<ServiceOperationResultCollection<RecordGroupSharing, RecordGroupSharing>>
      asyncBulkUnshareAllRecords(User subject, Group grpToShareWith);

  /** Add a group membership event for autoshare status change */
  void logGroupAutoshareStatusChange(Group group, User subject, Boolean targetAutoshareStatus);
}
