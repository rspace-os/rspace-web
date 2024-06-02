package com.researchspace.integrations.jove.client;

import com.researchspace.integrations.jove.model.JoveArticle;
import com.researchspace.integrations.jove.model.JoveSearchRequest;
import com.researchspace.integrations.jove.model.JoveSearchResult;
import com.researchspace.model.User;
import java.net.URISyntaxException;

public interface JoveClient {

  JoveSearchResult search(User user, JoveSearchRequest searchRequest) throws URISyntaxException;

  JoveArticle getArticle(User user, Long articleId) throws URISyntaxException;
}
