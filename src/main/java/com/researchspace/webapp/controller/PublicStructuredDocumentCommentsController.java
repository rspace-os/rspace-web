package com.researchspace.webapp.controller;

import com.researchspace.model.EcatCommentItem;
import com.researchspace.model.RecordGroupSharing;
import java.security.Principal;
import java.util.List;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Serves the single editor endpoint the published-document public view needs: comment viewing
 * (coreEditor.js requests it under the anonymous /public/publicView prefix). Every other editor
 * endpoint requires authentication and must never be mapped under /public/** (RSDEV-1329).
 */
@Controller
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.NEVER)
@RequestMapping("/public/publicView/workspace/editor/structuredDocument")
public class PublicStructuredDocumentCommentsController extends BaseController {

  private @Autowired StructuredDocumentController structuredDocumentController;

  /**
   * Comment viewing on a published document. The public view logs in the anonymous guest account
   * first, so principal is normally non-null here and a session-less request is the rare case; READ
   * permission is asserted by the delegate.
   *
   * <p>Extends {@link BaseController} so that the {@link AuthorizationException} below, and any
   * raised by the delegate, is resolved by the inherited handler the same way as every other
   * security failure and is written to the security log. The resolved message can still reach the
   * viewer through the ajax error view, so it is externalized rather than hard-coded (RSDEV-1329).
   */
  @GetMapping("/getComments")
  @ResponseBody
  public List<EcatCommentItem> getComments(
      @RequestParam("commentId") Long commentId,
      @RequestParam(value = "revision", required = false) Integer revision,
      Principal principal) {
    if (principal == null) {
      throw new AuthorizationException(
          getText(
              "errors.authorization.failure.viewComments",
              new Object[] {RecordGroupSharing.ANONYMOUS_USER}));
    }
    return structuredDocumentController.getComments(commentId, revision, principal);
  }
}
