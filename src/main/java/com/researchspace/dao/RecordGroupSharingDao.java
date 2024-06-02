package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.AbstractUserOrGroupImpl;
import com.researchspace.model.Community;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import java.util.Collection;
import java.util.List;

/** Accesses state of records shared within a group. */
public interface RecordGroupSharingDao extends GenericDao<RecordGroupSharing, Long> {

  /**
   * Gets all the shared records in a group.
   *
   * @param groupId
   * @return
   */
  List<BaseRecord> getRecordsSharedByGroup(Long groupId);

  RecordGroupSharing getRecordWithPublicLink(String publicLink);

  /**
   * Removes all shared records within a group
   *
   * @param groupId
   * @return the number of entries removed from the underlying table
   */
  long removeAllForGroup(Group group);

  /**
   * Removes a specific record within a group
   *
   * @param groupId
   * @return the number of entries removed from the underlying table
   */
  long removeRecordFromGroupShare(Long groupId, Long recordId);

  /**
   * Boolean test for whether at least one of the recordIds is currently shared with the group
   * (true) or not (false).
   *
   * @param userOrGroupId
   * @param recordIds one or more record Ids.
   * @return
   */
  boolean isRecordAlreadySharedInGroup(Long userOrGroupId, Long... recordIds);

  /**
   * Returns a list of records, from the supplied list, that are shared with the group.
   *
   * @param userOrGroupId
   * @param recordIds one or more record Ids.
   * @return possibly empty but non-null list of shared base records
   */
  List<BaseRecord> findRecordsSharedWithUserOrGroup(Long groupId, Collection<Long> recordIds);

  /**
   * Gets all records shared by a user, from all groups to which he belongs.
   *
   * @param user
   * @return
   */
  List<RecordGroupSharing> getSharedRecordsForUser(User u);

  /**
   * Gets all records shared with a specific user.
   *
   * @param u user
   * @return
   */
  List<BaseRecord> getSharedRecordsWithUser(User u);

  /**
   * Gets ids of all records shared with a specific user.
   *
   * @param user
   * @return
   */
  List<Long> getSharedRecordIdsWithUser(User user);

  /**
   * Gets all templates shared with a specific user.
   *
   * @param u user
   * @return
   */
  List<BaseRecord> getSharedTemplatesWithUser(User u);

  ISearchResults<RecordGroupSharing> listAllPublishedRecordsForInternet(
      PaginationCriteria<RecordGroupSharing> pcg);

  ISearchResults<RecordGroupSharing> listAllRecordsPublishedByCommunityMembers(
      PaginationCriteria<RecordGroupSharing> pcg, List<Community> community);

  /**
   * Gets info about records shared by a user with a specific group
   *
   * @param user
   * @return
   */
  List<RecordGroupSharing> getRecordsSharedByUserToGroup(User user, Group grp);

  /**
   * Get ids of items shared by user with group
   *
   * @param user
   * @param grp
   * @return
   */
  List<Long> getRecordIdSharedByUserToGroup(User user, Group grp);

  /*
   * return all (distinct) records a) published by the user AND b) belonging to the user and published AND
   * c) published by a member of a group in which the user is the PI
   */
  ISearchResults<RecordGroupSharing>
      listPublishedRecordsOwnedByUserOrGroupMembersPlusRecordsPublishedByUser(
          User u, PaginationCriteria<RecordGroupSharing> pcg, List<Long> membersOfUsersGroups);

  ISearchResults<RecordGroupSharing> listUserRecordsPublished(
      User u, PaginationCriteria<RecordGroupSharing> pcg);

  /**
   * Gets paginated list of shared records in a group.
   *
   * @param groupId
   * @param pcg
   * @return
   */
  ISearchResults<RecordGroupSharing> listSharedRecordsForUser(
      User u, PaginationCriteria<RecordGroupSharing> pcg);

  List<String> getTagsMetaDataForRecordsSharedWithUser(User subject, String tagFilter);

  List<String> getTextDataFromOntologiesSharedWithUser(User subject);

  List<String> getTextDataFromOntologiesSharedWithUserIfSharedWithAGroup(
      User subject, Long[] ontologyIDsSharedWithAGroup);

  List<BaseRecord> getOntologiesFilesSharedWithUser(User subject);

  /**
   * Gets list of users/groups with access to this record.
   *
   * @param recordId
   * @return
   */
  List<AbstractUserOrGroupImpl> getUsersOrGroupsWithRecordAccess(Long recordId);

  /**
   * Given a list of recordIds, returns a sublist of those IDs of records that are shared.
   *
   * @param recordIds A List of {@link BaseRecord} ids.
   * @return A possibly empty but non-null {@link List} of recordIds that are a subset of the
   *     argument ids.
   * @see https://ops.researchspace.com/globalId/SD4540 for query analysis
   */
  List<Long> findSharedRecords(List<Long> recordIds);

  /**
   * Returns information about how the record was shared.
   *
   * @param recordId
   * @return an possibly empty but non-null list
   */
  List<RecordGroupSharing> getRecordGroupSharingsForRecord(Long recordId);

  /**
   * Gets a recordgroupsharing object based on sharer and sharee
   *
   * @param userOrGroupId
   * @param recordId
   * @return
   */
  List<RecordGroupSharing> findByRecordAndUserOrGroup(Long userOrGroupId, Long recordId);

  ISearchResults<RecordGroupSharing> listAllPublishedRecords(
      PaginationCriteria<RecordGroupSharing> pcg);
}
