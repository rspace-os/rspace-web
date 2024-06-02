package com.axiope.search;

import com.researchspace.model.User;
import java.io.IOException;
import java.util.List;

/** Strategy interface to abstract different searching strategies */
public interface FileSearchStrategy {

  /**
   * @param searchStr The search term. Implementations should specify any restrictions on the search
   *     term.
   * @param subject The user performing the search, can be <code>null</code> if user information is
   *     not needed
   * @return
   * @throws IOException
   */
  public List<FileSearchResult> searchFiles(String searchStr, User subject) throws IOException;

  /**
   * Set the number of search hits to limit search to
   *
   * @param nbrDocs
   */
  void setDefaultReturnDocs(int nbrDocs);

  /**
   * Boolean test as to whether search is local to the RSpace server, or is an external search (e.g
   * with Egnyte)
   *
   * @return <code>true</code> if the search is local (i.e. standard default filestore) or <code>
   *     false</code> otherwise.
   */
  boolean isLocal();
}
