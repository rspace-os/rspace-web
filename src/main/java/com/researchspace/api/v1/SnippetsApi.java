package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiSnippet;
import com.researchspace.model.User;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

public interface SnippetsApi {
  @GetMapping(value = "/api/v1/snippet/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
  ApiSnippet getSnippetById(@PathVariable long id, User user);

  @GetMapping(value = "/api/v1/snippet/{id}/content", produces = MediaType.TEXT_PLAIN_VALUE)
  String getSnippetContentById(@PathVariable long id, User user);

}
