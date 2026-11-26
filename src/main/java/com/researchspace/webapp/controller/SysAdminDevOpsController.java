package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AnnotatedImageRelink;
import com.researchspace.service.AnnotatedImageRelink.LinkState;
import com.researchspace.service.AnnotatedImageRelink.Outcome;
import com.researchspace.service.DevOpsManager;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
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
   * This is to fix SUPPORT-522 issue i.e. problem with duplication of document shared into somebody
   * else's notebook, where the copy was sometimes ending inside shard notebook.
   *
   * <p>The code locates such problematic copies belonging to the particular user, and moves them
   * out of notebook into user's own Workspace.
   *
   * <p>This code is intended to be triggered from browser, by sysadmin who is logged into RSpace by
   * navigating to the particular endpoint. E.g. to run the code for user with id 4, login as
   * sysadmin and navigate browser to: /system/devops/ajax/support-522/4, then eventually to
   * /system/devops/ajax/support-522/4?update=true
   */
  @GetMapping("/ajax/support-522/{userId}")
  @ResponseBody
  public String getSupportPage(
      @PathVariable Long userId, @RequestParam(value = "update", required = false) boolean update) {
    assertSubjectIsSysadmin();

    User user = userManager.get(userId);
    String result =
        "Starting SUPPORT-552 fix process for user: " + user.getUsername() + ".<br />\n";

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

  @Autowired private DevOpsManager devOpsManager;

  @GetMapping("/ajax/fixRecord/{globalId}")
  @ResponseBody
  public String getFixRecordPage(
      @PathVariable GlobalIdentifier globalId,
      @RequestParam(value = "update", required = false) boolean update) {

    User subject = assertSubjectIsSysadmin();
    String result = devOpsManager.fixRecord(globalId, subject, update);
    result += "<br/>If that doesn't seem right, please contact RSpace Support.";
    return result;
  }

  /**
   * Fixes SUPPORT-536 (RSDEV-1837): before RSDEV-1419, annotating an image with the Sketch tool
   * removed or deleted the link between the text field and its Gallery image, so Gallery "Show
   * linked docs", the image Info panel and the API's document files missed the document.
   *
   * <p>Scans current text fields of non-deleted documents for annotated images without an active
   * link, and lists each with its document, owner, field, Gallery image and status. A link is
   * restored only if the document owner may read the Gallery image; otherwise it is reported as
   * skipped, as are links in templates and to deleted Gallery images. A repeat run after a repair
   * reports nothing left to repair; skipped links are listed on every run. A "document owner cannot
   * read the Gallery image" skip typically means a collaborator inserted their own image and the
   * lost link was the owner's only access to it; such links are left for manual review.
   *
   * <p>Candidates are found by a LIKE scan for <code>data-type="annotation"</code> over the content
   * of all text fields, so expect a full scan of the text field table. Only that markup, written
   * for every annotation since RSpace 1.29, is checked: older annotated-image markup without it is
   * not scanned. The report runs read-only; with <code>update=true</code> only fields with a link
   * to restore are written. An error in one field is reported and the scan continues.
   *
   * <p>To run, log in as sysadmin and navigate to /system/devops/ajax/relinkAnnotatedImages to see
   * the report, then to /system/devops/ajax/relinkAnnotatedImages?update=true to repair. Run the
   * repair at a quiet time: it locks each field it repairs, so a user saving the same document at
   * that moment may see the save fail and need to retry.
   */
  @GetMapping(value = "/ajax/relinkAnnotatedImages", produces = "text/plain;charset=UTF-8")
  @ResponseBody
  public String relinkAnnotatedImages(
      @RequestParam(value = "update", required = false) boolean update) {
    assertSubjectIsSysadmin();

    List<AnnotatedImageRelink> results = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    // one transaction per field, so a large scan does not hold every field in one session
    for (Long fieldId : devOpsManager.findFieldIdsWithAnnotatedImages()) {
      List<AnnotatedImageRelink> reported = List.of();
      try {
        reported = devOpsManager.reportAnnotatedImagesInField(fieldId);
        List<AnnotatedImageRelink> found = reported;
        if (update && found.stream().anyMatch(result -> result.outcome() == Outcome.TO_REPAIR)) {
          found = devOpsManager.relinkAnnotatedImagesInField(fieldId);
        }
        results.addAll(found);
      } catch (RuntimeException e) {
        log.warn("Checking annotated-image links in field {} failed", fieldId, e);
        errors.add(describeError(fieldId, reported, e));
      }
    }

    StringBuilder report = new StringBuilder();
    report.append(update ? "Repairing" : "Reporting").append(" annotated-image links.\n");
    report.append("document\towner\tfield\timage\tstatus\n");
    for (AnnotatedImageRelink result : results) {
      report
          .append(result.documentGlobalId())
          .append('\t')
          .append(result.ownerUsername())
          .append('\t')
          .append(result.fieldId())
          .append('\t')
          .append(result.imageGlobalId())
          .append('\t')
          .append(describe(result))
          .append('\n');
    }
    if (!errors.isEmpty()) {
      report.append("Errors:\n");
      errors.forEach(error -> report.append(error).append('\n'));
    }
    report
        .append("Total: ")
        .append(results.size())
        .append(" (missing rows: ")
        .append(countLinkState(results, LinkState.MISSING_ROW))
        .append(", deleted rows: ")
        .append(countLinkState(results, LinkState.DELETED_ROW))
        .append("); to repair: ")
        .append(countOutcome(results, Outcome.TO_REPAIR))
        .append(", repaired: ")
        .append(countOutcome(results, Outcome.REPAIRED))
        .append(", skipped: ")
        .append(countOutcome(results, Outcome.SKIPPED))
        .append(", errors: ")
        .append(errors.size())
        .append(".\n");
    log.info(
        "Annotated-image relink (update={}): {} links found, {} repaired, {} skipped, {} errors",
        update,
        results.size(),
        countOutcome(results, Outcome.REPAIRED),
        countOutcome(results, Outcome.SKIPPED),
        errors.size());
    if (!update && countOutcome(results, Outcome.TO_REPAIR) > 0) {
      report.append("To repair, call the current URL with '?update=true' suffix.\n");
    }
    return report.toString();
  }

  /* If the repair failed after the report, names the document and images the report found. */
  private static String describeError(
      Long fieldId, List<AnnotatedImageRelink> reported, RuntimeException e) {
    StringBuilder line = new StringBuilder("field ").append(fieldId);
    if (!reported.isEmpty()) {
      line.append(" of ")
          .append(reported.get(0).documentGlobalId())
          .append(" (images ")
          .append(
              String.join(
                  ", ", reported.stream().map(AnnotatedImageRelink::imageGlobalId).toList()))
          .append(')');
    }
    return line.append(": ")
        .append(e.getClass().getSimpleName())
        .append(": ")
        .append(e.getMessage())
        .toString();
  }

  private static String describe(AnnotatedImageRelink result) {
    String state = result.linkState() == LinkState.MISSING_ROW ? "missing row" : "deleted row";
    return switch (result.outcome()) {
      case TO_REPAIR -> state;
      case REPAIRED -> state + ", repaired";
      case SKIPPED -> state + ", skipped: " + result.skipReason();
    };
  }

  private static long countLinkState(List<AnnotatedImageRelink> results, LinkState state) {
    return results.stream().filter(result -> result.linkState() == state).count();
  }

  private static long countOutcome(List<AnnotatedImageRelink> results, Outcome outcome) {
    return results.stream().filter(result -> result.outcome() == outcome).count();
  }

  private User assertSubjectIsSysadmin() {
    User user = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(user);
    return user;
  }
}
