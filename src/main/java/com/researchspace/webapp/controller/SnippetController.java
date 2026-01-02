package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.record.Snippet;
import java.security.Principal;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Deprecated // GET content endpoint also implemented in SnippetsApiController REST API. Existing
// method left here while old gallery code is still using it. Other endpoints will be
// migrated when the document editor is migrated to react.
@Controller
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.DEFAULT)
@RequestMapping("/snippet")
public class SnippetController extends BaseController {

  @ResponseBody
  @PostMapping("/create")
  public AjaxReturnObject<String> createSnippet(
      @RequestParam("snippetName") String snippetName,
      @RequestParam("content") String content,
      @RequestParam("fieldId") Long fieldId, // not used any more
      Principal principal)
      throws Exception {

    ErrorList errors = new ErrorList();
    if (StringUtils.isBlank(snippetName)) {
      errors = getErrorListFromMessageCode("errors.required", "Name");
      return new AjaxReturnObject<>(null, errors);
    } else if (StringUtils.containsAny(snippetName, "/<>")) {
      errors = getErrorListFromMessageCode("errors.invalidchars", "/,> or <", "name");
      return new AjaxReturnObject<>(null, errors);
    }

    User user = userManager.getUserByUsername(principal.getName());
    Snippet snippet = recordManager.createSnippet(snippetName, content, user);

    if (snippet != null) {
      publisher.publishEvent(createGenericEvent(user, snippet, AuditAction.CREATE));
      return new AjaxReturnObject<>(
          getText("snippet.creation.ok", new String[] {snippetName}), null);
    }

    errors.addErrorMsg(getText("snippet.creation.failed"));
    return new AjaxReturnObject<>(null, errors);
  }

  @ResponseBody
  @PostMapping("/insertIntoField")
  public String insertSnippetIntoField(
      @RequestParam("snippetId") Long snippetId,
      @RequestParam("fieldId") Long fieldId,
      Principal principal) {
    User user = userManager.getUserByUsername(principal.getName());
    String updatedSnippetContent = recordManager.copySnippetIntoField(snippetId, fieldId, user);
    return updatedSnippetContent;
  }

  @ResponseBody
  @GetMapping("/content/{id}")
  public String getSnippetContent(
      @PathVariable("id") Long id, Principal principal, HttpServletResponse response) {
    Snippet snippet = recordManager.getAsSubclass(id, Snippet.class);
    return snippet.getContent();
  }
}
