package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.SnippetApi;
import com.researchspace.api.v1.model.ApiSnippet;
import com.researchspace.api.v1.service.SnippetService;
import com.researchspace.model.User;
import com.researchspace.model.record.Snippet;
import javax.ws.rs.NotFoundException;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;

@ApiController
public class SnippetApiController extends BaseApiController implements SnippetApi {

  private final SnippetService snippetService;

  public SnippetApiController(SnippetService snippetService) {
    this.snippetService = snippetService;
  }

  @Override
  public ApiSnippet getSnippetById(
      @PathVariable long id, @RequestAttribute(name = "user") User user) {
    try {
      Snippet snippet = snippetService.getSnippet(id, user);
      ApiSnippet apiSnippet = new ApiSnippet(snippet);
      buildAndAddSelfLink("/snippet", apiSnippet);
      return apiSnippet;
    } catch (ObjectRetrievalFailureException | AuthorizationException e) {
      // don't let unauthorised users know whether the snippet exists or not.
      throw new NotFoundException(createNotFoundMessage("Snippet", id));
    }
  }

  @Override
  public String getSnippetContentById(
      @PathVariable long id, @RequestAttribute(name = "user") User user) {
    try {
      Snippet snippet = snippetService.getSnippet(id, user);
      return snippet.getContent();
    } catch (ObjectRetrievalFailureException | AuthorizationException e) {
      // don't let unauthorised users know whether the snippet exists or not.
      throw new NotFoundException(createNotFoundMessage("Snippet", id));
    }
  }
}
