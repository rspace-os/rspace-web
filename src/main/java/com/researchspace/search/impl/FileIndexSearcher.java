package com.researchspace.search.impl;

import com.axiope.search.FileSearchResult;
import com.axiope.search.FileSearchStrategy;
import com.axiope.search.IFileSearcher;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.EcatDocumentFileDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Search indexed files in file store in conjunction with FileIndexer */
@Component("fileIndexSearcher")
@Slf4j
public class FileIndexSearcher implements IFileSearcher {

  public FileSearchStrategy getFileSearchStrategy() {
    return fileSearchStrategy;
  }

  private @Autowired EcatDocumentFileDao docDao;
  private @Autowired FileSearchStrategy fileSearchStrategy;

  /*
   * For testing
   */
  public void setDocDao(EcatDocumentFileDao docDao) {
    this.docDao = docDao;
  }

  public FileIndexSearcher() {}

  public void setFileSearchStrategy(FileSearchStrategy fileSearchStrategy) {
    this.fileSearchStrategy = fileSearchStrategy;
  }

  /* (non-Javadoc)
   * @see com.axiope.search.IFIleSearcher#searchContents(java.lang.String)
   */
  @Override
  public ISearchResults<BaseRecord> searchContents(
      String searchStr, PaginationCriteria<BaseRecord> pgCrit, User subject) throws IOException {
    List<FileSearchResult> files = fileSearchStrategy.searchFiles(searchStr, subject);
    if (!files.isEmpty()) {
      ISearchResults<BaseRecord> records =
          docDao.getEcatDocumentFileByURI(
              pgCrit,
              subject.getUsername(),
              files.stream()
                  .map(FileSearchResult::getRspaceRelativePath)
                  .map(FileIndexSearcher::removeFileSeparators)
                  .collect(Collectors.toList()));
      return records;
    }
    return SearchResultsImpl.emptyResult(pgCrit);
  }

  public static String removeFileSeparators(String pathWithSeparators) {
    return StringUtils.replaceChars(pathWithSeparators, "\\/", "");
  }

  @Override
  public String getIndexFolderPath() {
    log.error("this should not be called");
    return null;
  }
}
