package com.researchspace.webapp.controller;

import com.ibm.icu.text.ListFormatter;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.record.Snippet;
import com.researchspace.service.ListFormatUtils;
import java.security.Principal;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.DEFAULT)
@RequestMapping("/snippet")
public class SnippetController extends BaseController {

  @ResponseBody
  @PostMapping("/create")
  public SnippetResponse createSnippet(
      @RequestParam("snippetName") String snippetName,
      @RequestParam("content") String content,
      @RequestParam("fieldId") Long fieldId, // not used any more
      Principal principal)
      throws Exception {

    if (StringUtils.isBlank(snippetName)) {
      return new SnippetResponse(null, message("errors.required", getText("label.name")));
    } else if (StringUtils.containsAny(snippetName, "/<>")) {
      return new SnippetResponse(
          null,
          message(
              "errors.invalidChars",
              ListFormatUtils.formatList(List.of("/", ">", "<"), ListFormatter.Type.OR),
              getText("label.nameLowercase")));
    }

    User user = userManager.getUserByUsername(principal.getName());
    Snippet snippet = recordManager.createSnippet(snippetName, content, user);

    if (snippet != null) {
      publisher.publishEvent(createGenericEvent(user, snippet, AuditAction.CREATE));
      return new SnippetResponse(message("gallery.snippet.creation.ok", snippetName), null);
    }

    return new SnippetResponse(null, message("gallery.snippet.creation.failed"));
  }

  private static I18nMessage message(String key, Object... arguments) {
    return new I18nMessage(key, List.of(arguments));
  }

  public record SnippetResponse(I18nMessage data, I18nMessage errorMsg) {}

  public record I18nMessage(String key, List<Object> arguments) {}

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
}
