package com.axiope.search;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import java.io.IOException;

/**
 * Encapsulates search over files in a file store. Implementations can be provided for different
 * filestore implementations.
 */
public interface IFileSearcher extends Indexable {

  /**
   * Facade method
   *
   * <p>Implementations should:
   *
   * <ul>
   *   <li>Perform the search
   *   <li>Perform mapping back to the EcatMediaFile metadata about the file.
   * </ul>
   *
   * @param searchStr The search string
   * @param pgCrit Pagination of results
   * @param subject The current RSpace user
   * @return ISearchResults<BaseRecord>. In practice these will be EcatMediaFiles but we are using
   *     {@link BaseRecord} here so that results can be merged with other searches performed on
   *     database data.
   * @throws IOException
   */
  ISearchResults<BaseRecord> searchContents(
      String searchStr, PaginationCriteria<BaseRecord> pgCrit, User subject) throws IOException;

  /**
   * Accessor to underlying search strategy to enable any configuration it requires.
   *
   * @return
   */
  FileSearchStrategy getFileSearchStrategy();

  void setFileSearchStrategy(FileSearchStrategy strategy);
}
