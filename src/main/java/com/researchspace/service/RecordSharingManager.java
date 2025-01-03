package com.researchspace.service;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.RSPath;
import com.researchspace.model.views.ServiceOperationResult;
import java.util.Collection;
import java.util.List;

/** Services for sharing records with other users/ groups. */
public interface RecordSharingManager {

  RecordGroupSharing get(Long id);

  RecordGroupSharing getByPublicLink(String publicLink);

  /**
   * Lists all the records that the specified user has shared.
   *
   * @param u
   * @return
   */
  List<RecordGroupSharing> getSharedRecordsForUser(User u);

  /**
   * Gets a paginated list of records the user has shared, according to the supplied pagination
   * criteria
   *
   * @param u
   * @param pcg
   * @return
   */
  ISearchResults<RecordGroupSharing> listSharedRecordsForUser(
      User u, PaginationCriteria<RecordGroupSharing> pcg);

  /**
   * Return all (distinct) records a) published by the user AND b) belonging to the user and
   * published AND c) published by a member of a group in which the user is the PI
   */
  ISearchResults<RecordGroupSharing>
      listPublishedRecordsOwnedByUserOrGroupMembersPlusRecordsPublishedByUser(
          User u, PaginationCriteria<RecordGroupSharing> pcg);

  ISearchResults<RecordGroupSharing> listAllPublishedRecords(
      PaginationCriteria<RecordGroupSharing> pcg);

  ISearchResults<RecordGroupSharing> listAllRecordsPublishedByMembersOfAdminsCommunities(
      PaginationCriteria<RecordGroupSharing> pcg, User communityAdmin);

  /** Get info about records shared by user with a group. */
  List<RecordGroupSharing> getSharedRecordsForUserAndGroup(User user, Group group);

  /**
   * Returns records shared with user, limited to templates.
   *
   * @param u
   * @return
   */
  List<BaseRecord> getTemplatesSharedWithUser(User u);

  /**
   * Gets sharings list of a given record
   *
   * @return
   */
  List<RecordGroupSharing> getRecordSharingInfo(Long recordId);

  /**
   * Shares a record under the control of <code>sharing</code> with 1 or more users or groups.<br>
   * This user should have permission to share the record. This method will:
   *
   * <ul>
   *   <li>Add the record to the list of shared records in {@link RecordGroupSharing}
   *   <li>Add the record to the 'Shared' folder of recipients of sharing.
   * </ul>
   *
   * @param sharing The user who has permission to share the record. This will usually be the
   *     subject, but might be someone who is accepting an invitation to share.
   * @param recordToShareId
   * @param sharingConfigs An array of ShareConfigElement
   * @return result of the share operation with created RecordGroupSharing entities
   * @throws IllegalAddChildOperation, AuthorizationException if an attempt is made by non-owner to
   *     share, or if the recordToShareId is not a notebook or document.
   */
  ServiceOperationResult<List<RecordGroupSharing>> shareRecord(
      User sharing, Long recordToShareId, ShareConfigElement[] sharingConfigs)
      throws IllegalAddChildOperation;

  /**
   * Unshares a previously shared record under the control of <code>sharing</code> with 1 or more
   * groups.<br>
   *
   * @param unsharing
   * @param recordToUnshareId
   * @param groupIdsToUnshareWith
   * @throws IllegalAddChildOperation
   */
  void unshareRecord(
      User unsharing, Long recordToUnshareId, ShareConfigElement[] groupIdsToUnshareWith)
      throws IllegalAddChildOperation;

  /**
   * ALters permission for a record that is already shared.
   *
   * @param recordGroupSharing The id of the {@link RecordGroupSharing} object
   * @param action The new new permissions
   * @param uname The authenticated user's username
   * @return <code>ErrorList</code>
   */
  ErrorList updatePermissionForRecord(Long recordGroupSharing, String action, String uname);

  /**
   * Given an {@link Collection} of {@link BaseRecord}, will look up whether these records are
   * shared, and set the transient property sharedStatus in BaseRecord.
   *
   * @param records A {@link Collection} of BaseRecords.
   * @param subject The authenticated subject
   */
  void updateSharedStatusOfRecords(Collection<? extends BaseRecord> records, User subject);

  /**
   * This method is used in Community environment, where a user after sending a shared record
   * request, could manage these pending requests from My RSpace -> Shared Documents.
   *
   * @param userId
   * @return
   */
  List<ShareRecordMessageOrRequestDTO> getSharedRecordRequestsByUserId(Long userId);

  /**
   * Facade method to unshare items from shared folder during a deletion operation
   *
   * @param deleting
   * @param toDelete
   * @param path
   */
  void unshareFromSharedFolder(User deleting, BaseRecord toDelete, RSPath path);

  ISearchResults<RecordGroupSharing> listUserRecordsPublished(
      User subject, PaginationCriteria<RecordGroupSharing> pagCrit);

  ISearchResults<RecordGroupSharing> listAllPublishedRecordsForInternet(
      PaginationCriteria<RecordGroupSharing> pagCrit);
}
