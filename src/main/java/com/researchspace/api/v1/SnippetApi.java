package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiSnippet;
import com.researchspace.model.User;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/{id}")
public interface SnippetApi {
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  ApiSnippet getSnippetById(@PathVariable long id, User user);

  @GetMapping(value = "/content", produces = MediaType.TEXT_PLAIN_VALUE)
  String getSnippetContentById(@PathVariable long id, User user);
}
