package com.researchspace.service;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import java.io.IOException;

public interface ChatBotFunctionalityHandler {
  /**
   * This performs a search and returns results (returns at most 5 results for chat bot use).
   *
   * @param user user
   * @param searchQuery search query
   * @return
   */
  ISearchResults<BaseRecord> search(User user, String searchQuery) throws IOException;
}
