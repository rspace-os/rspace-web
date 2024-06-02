package com.researchspace.dao;

import com.researchspace.model.InternalLink;
import java.util.List;

/** Data Access Object for internal links */
public interface InternalLinkDao {

  /**
   * @return internal links from non-deleted documents to target record
   */
  List<InternalLink> getLinksPointingToRecord(long targetRecordId);

  /**
   * @return internal links originating from particular record
   */
  List<InternalLink> getLinksFromRecordContent(long sourceRecordId);

  /**
   * @return true if the link was created
   */
  boolean saveInternalLink(long sourceRecordId, long targetRecordId);

  void deleteInternalLink(long sourceRecordId, long targetRecordId);
}
