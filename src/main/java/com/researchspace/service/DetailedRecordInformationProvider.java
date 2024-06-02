package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.DetailedRecordInformation;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.session.UserSessionTracker;
import java.util.List;
import java.util.Map;

public interface DetailedRecordInformationProvider {

  /**
   * Gets DetailedRecordInformation for any type of BaseRecord
   *
   * @param recordId
   * @param subject
   * @param revision optional, for requesting info on particular revision
   * @param userVersion optional, for requesting info on particular version of a document
   * @return
   */
  DetailedRecordInformation getDetailedRecordInformation(
      Long recordId, UserSessionTracker tracker, User subject, Long revision, Long userVersion);

  void addSharingInfo(Long recordId, BaseRecord baseRecord, DetailedRecordInformation detailedInfo);

  /**
   * Gets 'linked by' record information for getInfo. Checks whether current user have permissions
   * to see the linking record, if not then only owner's info is populated in2 returned list
   * element.
   *
   * @param targetRecordId The document for whose links we will retrieve summary information
   * @return A possibly empty but non-null list of RecordInformation
   */
  List<RecordInformation> getLinkedByRecords(Long targetRecordId, User subject);

  /**
   * Gets basic info to support Attachment View mode summary info. The 2 argument arrays must be
   * non-null & the same length. <br>
   * If a record information cannot be retrieved for an ID then the value is null.
   *
   * @param ids An array of ids
   * @param revisions An array of revision numbers, elements can be null.
   * @param user The subject
   * @return
   */
  Map<Long, RecordInformation> getRecordInformation(Long[] ids, Long[] revisions, User user);
}
