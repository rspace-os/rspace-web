package com.researchspace.dao;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.dto.DocAttachmentSummaryInfo;
import com.researchspace.model.record.BaseRecord;
import java.util.List;

/** Queries to retrieve EcatDocuments based on absolute file URIs */
public interface EcatDocumentFileDao extends GenericDao<EcatDocumentFile, Long> {

  /**
   * @param pg
   * @param user The subject
   * @param fileURIList an array of absolute file URIs (e.g. retrieved from a Lucene search)
   * @return An {@link ISearchResults}
   */
  ISearchResults<BaseRecord> getEcatDocumentFileByURI(
      PaginationCriteria<BaseRecord> pg, String user, List<String> fileURIList);

  /**
   * Query to get summary information required by attachment links RSPAC-1700
   *
   * @param id
   * @return a DocAttachmentSummaryInfo
   */
  DocAttachmentSummaryInfo getSummaryInfo(Long id);
}
