package com.researchspace.api.v1.service;

import com.researchspace.model.User;
import com.researchspace.model.record.Snippet;

public interface SnippetService {

  Snippet getSnippet(long id, User user);
}
