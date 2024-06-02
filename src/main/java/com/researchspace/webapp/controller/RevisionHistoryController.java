package com.researchspace.webapp.controller;

import com.researchspace.core.util.*;
import com.researchspace.model.EditStatus;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.audittrail.RestoreEvent;
import com.researchspace.model.dtos.RevisionSearchCriteria;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.service.FieldManager;
import com.researchspace.service.RestoreDeletedItemResult;
import com.researchspace.session.UserSessionTracker;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

/**
 * Controller for managing document's revision history. Initially just methods moved out of
 * StructuredDocumentController.
 */
@Controller
@RequestMapping("/workspace/revisionHistory")
public class RevisionHistoryController extends BaseController {

  private static final String REVISION_HISTORY_VIEW = "workspace/revisionHistory";

  private static final String REVISION_HISTORY_AJAX_VIEW = "workspace/revisionHistory_ajax";

  @Autowired
  public void setAuditManager(AuditManager auditManager) {
    this.auditManager = auditManager;
  }

  private FieldManager fieldManager;

  @Autowired
  public void setFieldManager(FieldManager fieldManager) {
    this.fieldManager = fieldManager;
  }

  /**
   * Gets a list of revisions of a given document and forwards to a revision listing view
   *
   * @param id
   * @param model
   * @param principal
   * @return
   */
  @GetMapping("/list/{recordId}")
  public ModelAndView getListOfVersions(
      @PathVariable("recordId") long id,
      Model model,
      @RequestParam(value = "settingsKey", required = false) String settingsKey,
      Principal principal,
      PaginationCriteria<AuditedRecord> pgCrit,
      RevisionSearchCriteria srchCrit) {
    doPaginatedRevisionList(id, model, principal, pgCrit, srchCrit, settingsKey);
    return new ModelAndView(REVISION_HISTORY_VIEW);
  }

  @GetMapping("/ajax/list/{recordId}")
  public ModelAndView getListOfVersionsAjax(
      @PathVariable("recordId") long id,
      Model model,
      @RequestParam(value = "settingsKey", required = false) String settingsKey,
      Principal principal,
      PaginationCriteria<AuditedRecord> pgCrit,
      RevisionSearchCriteria srchCrit) {
    doPaginatedRevisionList(id, model, principal, pgCrit, srchCrit, settingsKey);
    return new ModelAndView(REVISION_HISTORY_AJAX_VIEW);
  }

  @GetMapping("/ajax/{recordId}/versions")
  @ResponseBody
  public AjaxReturnObject<List<RecordInformation>> getRevisionsJson(
      @PathVariable("recordId") long recordId, Principal principal) {

    User user = getUserByUsername(principal.getName());
    StructuredDocument currentDoc = getCurrentDocument(recordId, user);
    List<AuditedRecord> revisions = auditManager.getHistory(currentDoc, null);

    List<RecordInformation> docVersionInfos = new ArrayList<>();
    for (AuditedRecord ar : revisions) {
      StructuredDocument docVersion = ar.getRecordAsDocument();
      RecordInformation recordInfo = docVersion.toRecordInfo();
      recordInfo.setRevision(ar.getRevision().longValue());
      docVersionInfos.add(recordInfo);
    }
    return new AjaxReturnObject<>(docVersionInfos, null);
  }

  /**
   * @param recordId
   * @param model
   * @param principal
   * @param pgCrit
   * @param srchCrit
   * @param settingsKey
   * @throws IllegalStateException if recordId is not that of a structured document
   * @throws AuthorizationException if subect doesn't have read permission on the document
   */
  private void doPaginatedRevisionList(
      long recordId,
      Model model,
      Principal principal,
      PaginationCriteria<AuditedRecord> pgCrit,
      RevisionSearchCriteria srchCrit,
      String settingsKey) {

    User user = getUserByUsername(principal.getName());
    StructuredDocument currentDoc = getCurrentDocument(recordId, user);

    pgCrit.setClazz(AuditedRecord.class);
    if (pgCrit.getOrderBy() == null) {
      pgCrit.setOrderBy("revision");
    }
    pgCrit.setSearchCriteria(srchCrit);
    List<AuditedRecord> revisions = auditManager.getHistory(currentDoc, pgCrit);
    Integer total = auditManager.getNumRevisionsForDocument(currentDoc.getId(), srchCrit);
    ISearchResults<AuditedRecord> revisionResults =
        new SearchResultsImpl<>(revisions, pgCrit.getPageNumber().intValue(), total.longValue());

    List<PaginationObject> pgObs =
        PaginationUtil.generatePagination(
            revisionResults.getTotalPages(),
            revisionResults.getPageNumber(),
            new DefaultURLPaginator(
                "/workspace/revisionHistory/ajax/list/" + recordId + "?settingsKey=" + settingsKey,
                pgCrit));
    model.addAttribute("history", revisions);
    model.addAttribute("currentDoc", currentDoc);
    model.addAttribute(PaginationUtil.PAGINATION_LIST_MODEL_ATTR_NAME, pgObs);
    model.addAttribute("searchCriteria", srchCrit);
    model.addAttribute("recordId", currentDoc.getId());
    model.addAttribute("fieldNames", fieldManager.getFieldNamesForRecord(currentDoc.getId()));
    model.addAttribute("isSigned", currentDoc.isSigned());
    model.addAttribute("user", user);
    if (!StringUtils.isEmpty(settingsKey)) {
      model.addAttribute("settingsKey", settingsKey);
    }
  }

  private StructuredDocument getCurrentDocument(long recordId, User user) {
    Record strucDoc = recordManager.get(recordId);
    if (!permissionUtils.isPermitted(strucDoc, PermissionType.READ, user)) {
      throw new AuthorizationException(" Resource is not authorized ");
    }
    if (!strucDoc.isStructuredDocument()) {
      throw new IllegalStateException(
          "Viewing this record's audit history only works with StructuredDocuments!");
    }
    return strucDoc.asStrucDoc();
  }

  /**
   * Restores an audited history to a 'live' version
   *
   * @param recordId
   * @param revision
   * @param deleted
   * @param principal
   * @return A String with a specific syntax:'CAN_EDIT', id, 'Folder:id','Media:id', 'Notebook:id'
   *     which gives the client some idea what it is that's been restored, so that it can redirect
   *     to the appropriate view
   */
  @PostMapping("/restore")
  @ResponseBody
  public AjaxReturnObject<String> restoreVersion(
      @RequestParam("recordId") long recordId,
      @RequestParam("revision") Integer revision,
      @RequestParam(value = "deleted", required = false, defaultValue = "false") boolean deleted,
      Principal principal) {

    User user = getUserByUsername(principal.getName());
    try {
      // we're restoring a revision. This might already be edited so we
      // need to check this
      if (!deleted) {
        UserSessionTracker users = getCurrentActiveUsers();
        EditStatus res = recordManager.requestRecordEdit(recordId, user, users);
        if (EditStatus.EDIT_MODE.equals(res)) {
          Record updated = auditManager.restoreRevisionAsCurrent(revision, recordId);
          auditService.notify(new RestoreEvent(user, updated, revision));
          return new AjaxReturnObject<>(res.name(), null);
        } else {
          ErrorList el = getErrorListFromMessageCode("restore.failure.message", res);
          return new AjaxReturnObject<>(null, el);
        }
        // we're restoring a deleted document. Could be anything
      } else {
        RestoreDeletedItemResult asd =
            auditManager.fullRestore(recordId, getUserByUsername(principal.getName()));
        auditService.notify(new RestoreEvent(user, asd.getItemToRestore()));
        if (asd.getItemToRestore().isStructuredDocument()) {
          return new AjaxReturnObject<>(asd.getItemToRestore().getId() + "", null);
        } else if (asd.getItemToRestore().isNotebook()) {
          return new AjaxReturnObject<>("Notebook:" + asd.getItemToRestore().getId(), null);
          // rspac429
        } else if (asd.getItemToRestore().isMediaRecord()) {
          return new AjaxReturnObject<>("Media:" + asd.getItemToRestore().getId(), null);
        } else {
          // we're restoring a folder
          return new AjaxReturnObject<>("Folder:" + asd.getItemToRestore().getId(), null);
        }
      }
      // unlock record even if exception is thrown
    } finally {
      recordManager.unlockRecord(recordId, user.getUsername());
    }
  }
}
