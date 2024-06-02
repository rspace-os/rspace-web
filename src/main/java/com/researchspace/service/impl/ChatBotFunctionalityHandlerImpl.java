package com.researchspace.service.impl;

import com.axiope.search.SearchManager;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.model.User;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.service.ChatBotFunctionalityHandler;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChatBotFunctionalityHandlerImpl implements ChatBotFunctionalityHandler {

  @Autowired private SearchManager searchManager;

  @Override
  public ISearchResults<BaseRecord> search(User user, String searchQuery) throws IOException {
    // Do the actual search
    return searchManager.searchUserRecordsWithSimpleQuery(user, searchQuery, 5);
  }
}
