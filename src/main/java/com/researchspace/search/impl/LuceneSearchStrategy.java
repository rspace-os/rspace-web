package com.researchspace.search.impl;

import com.axiope.search.FileSearchResult;
import com.axiope.search.FileSearchStrategy;
import com.axiope.search.SearchQueryParseException;
import com.researchspace.model.User;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/** Performs Lucene search on locally indexed FileStore */
public class LuceneSearchStrategy extends AttachmentSearchBase implements FileSearchStrategy {
  private int defaultReturnDocs;
  private boolean isInitialized = false;

  public LuceneSearchStrategy() {
    super();
    defaultReturnDocs = 100;
  }

  public LuceneSearchStrategy(int defaultReturnDocs) {
    super();
    this.defaultReturnDocs = defaultReturnDocs;
  }

  public int getDefaultReturnDocs() {
    return defaultReturnDocs;
  }

  public void setDefaultReturnDocs(int nbrDocs) {
    defaultReturnDocs = nbrDocs;
  }

  public List<FileSearchResult> searchFiles(String searchStr, User subject) throws IOException {
    if (!isInitialized) {
      setIndexFolder();
    }
    List<FileSearchResult> files;
    try (Directory searchFolder = FSDirectory.open(getIndexFolder().toPath());
        IndexReader indexReader = DirectoryReader.open(searchFolder)) {

      Analyzer analyzer = new StandardAnalyzer();
      // allow parser AND/OR, default tokenize is OR
      QueryParser queryParser = new QueryParser(FileIndexer.FIELD_CONTENTS, analyzer);
      Query query = queryParser.parse(QueryParser.escape(searchStr));

      IndexSearcher luceneSearcher = new IndexSearcher(indexReader);
      TopDocs docs = luceneSearcher.search(query, defaultReturnDocs);
      files = setResultFiles(luceneSearcher, docs, query);
    } catch (ParseException e) {
      throw new SearchQueryParseException(e);
    }
    return files;
  }

  // ------------------support method --------------------
  List<FileSearchResult> setResultFiles(IndexSearcher searcher, TopDocs docs, Query query)
      throws IOException {
    List<FileSearchResult> lst = new ArrayList<>();
    for (ScoreDoc sdc : docs.scoreDocs) {

      Explanation ex = searcher.explain(query, sdc.doc);
      Document dc = searcher.doc(sdc.doc);
      FileSearchResult rf =
          FileSearchResult.builder()
              .fileName(dc.get(FileIndexer.FIELD_NAME))
              .filePath(dc.get(FileIndexer.FIELD_PATH))
              .score(sdc.doc)
              .explain(ex.toString())
              .rspaceRelativePath(absPathToRelPath(dc.get(FileIndexer.FIELD_PATH)))
              .build();
      lst.add(rf);
    }
    return lst;
  }

  public static String absPathToRelPath(String absPath) {
    return absPath.substring(absPath.lastIndexOf("file_store") + 11);
  }

  @Override
  public boolean isLocal() {
    return true;
  }
}
