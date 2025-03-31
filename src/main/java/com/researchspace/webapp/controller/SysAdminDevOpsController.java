package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * A controller allowing RSpace Sysadmin or IT Support team to execute some code fixes that should
 * be applied at runtime, rather than on upgrade/startup.
 */
@Controller("sysAdminDevopsController")
@RequestMapping("/system/devops")
public class SysAdminDevOpsController extends BaseController {

  /**
   * This is to fix SUPPORT-522 issue i.e. problem with duplication of document shared into
   * somebody else's notebook, where the copy was sometimes ending inside shard notebook.
   *
   * The code locates such problematic copies belonging to the particular user, and
   * moves them out of notebook into user's own Workspace.
   *
   * This code is intended to be triggered from browser, by sysadmin who is logged into RSpace
   * by navigating to the particular endpoint. E.g. to run the code for user with id 4,
   * login as sysadmin and navigate browser to:
   * <rspace>/system/devops/ajax/support-552/4
   * then
   * <rspace>/system/devops/ajax/support-552/4?update=true
   */
  @GetMapping("/ajax/support-522/{userId}")
  @ResponseBody
  public String getSupportPage(
      @PathVariable Long userId, @RequestParam(value = "update", required = false) boolean update) {
    assertSubjectIsSysadmin();

    User user = userManager.get(userId);
    String result = "Starting SUPPORT-552 fix process for user: " + user.getUsername() + ".<br />\n";

    List<Long> allDocIds = recordManager.getAllNonTemplateNonTemporaryStrucDocIdsOwnedByUser(user);
    result += "Found total of " + allDocIds.size() + " documents owned by the user.<br />\n";

    int foundCount = 0;
    int movedCount = 0;
    for (Long userDocId : allDocIds) {
      StructuredDocument userDoc = recordManager.get(userDocId).asStrucDoc();
      Folder parentFolder = userDoc.getParent();
      if (parentFolder != null && parentFolder.isNotebook()) {
        if (!userDoc.getOwner().equals(parentFolder.getOwner())) {
          result += "Found a problematic doc: " + userDoc.getGlobalIdentifier() + ".<br />\n";
          foundCount++;
          if (update) {
            boolean moved = recordManager.forceMoveDocumentToOwnerWorkspace(userDoc);
            if (moved) {
              movedCount++;
            }
          }
        }
      }
    }

    if (foundCount == 0) {
      result += "No problematic documents found for the user.<br />\n";
    } else {
      if (update) {
        result +=
            "Updated " + movedCount + " docs (out of " + foundCount + " that should be updated)";
      } else {
        result += "To update problematic documents call the current URL with '?update=true' suffix";
      }
    }

    return result;
  }

  private void assertSubjectIsSysadmin() {
    User user = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(user);
  }
}
