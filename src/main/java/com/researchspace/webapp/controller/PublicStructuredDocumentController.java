package com.researchspace.webapp.controller;

import static com.researchspace.model.EditStatus.CAN_NEVER_EDIT;

import com.researchspace.model.EditStatus;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.SignatureInfo;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.RecordSigningManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.session.UserSessionTracker;
import java.io.IOException;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/** Displays PUBLIC StructuredDocuments to the anonymous user */
@Slf4j
@Controller
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.NEVER)
@RequestMapping("/public")
public class PublicStructuredDocumentController extends BaseController {

  public static final String STRUCTURED_DOCUMENT_EDITOR_VIEW_NAME =
      "workspace/editor/public_structuredDocument";
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  private @Autowired RecordSigningManager signingManager;
  @Autowired private RecordSharingManager recordSharingManager;

  @Value("${publishing.anonymousGuest.password}")
  private String anonymousPassword;

  /**
   * Opens document for viewing by anonymous user
   *
   * @param model
   * @return
   * @throws RecordAccessDeniedException
   */
  @GetMapping("/publishedView/document/{publicLink}")
  public ModelAndView openDocument(
      @PathVariable("publicLink") String publicLink,
      Model model,
      HttpServletResponse response,
      HttpServletRequest request)
      throws RecordAccessDeniedException, IOException {
    User user = userManager.getAuthenticatedUserInSession();
    RecordGroupSharing rgs = recordSharingManager.getByPublicLink(publicLink);
    if (rgs == null) {
      throw new PublicLinkNotFoundException(publicLink);
    }
    User sharedBy = rgs.getSharedBy();
    if (!systemPropertyPermissionManager.isPropertyAllowed(sharedBy, "public_sharing")) {
      WebUtils.issueRedirect(request, response, "/public/publishIsDisabled");
    }
    if (user == null) {
      user = getUserByUsername(RecordGroupSharing.ANONYMOUS_USER);
      PublicDocumentsUtilities.loginAnonymousUser(anonymousPassword);
    }
    Long recordId = rgs.getShared().getId();
    DocumentEditContext docEditContext = getDocEditContext(recordId, user);
    StructuredDocument structuredDocument = docEditContext.getStructuredDocument();
    model.addAttribute("documentName", structuredDocument.getName());

    prepareDocumentForView(model, structuredDocument);

    SignatureInfo signatureInfo = null;
    if (structuredDocument.isSigned()) {
      signatureInfo = signingManager.getSignatureForRecord(recordId).toSignatureInfo();
    }

    model.addAttribute("structuredDocument", structuredDocument);
    model.addAttribute("editStatus", CAN_NEVER_EDIT);
    model.addAttribute("id", recordId);
    model.addAttribute("signatureInfo", signatureInfo);
    model.addAttribute("user", user);
    model.addAttribute("publicationSummary", rgs.getPublicationSummary());
    model.addAttribute("publishOnInternet", rgs.isPublishOnInternet());
    if (rgs.isDisplayContactDetails()) {
      model.addAttribute("contactDetails", sharedBy.getEmail());
    }
    PublicDocumentsUtilities.addGroupAttributes(model, user, groupManager.listGroupsForUser());
    auditService.notify(new GenericEvent(user, structuredDocument, AuditAction.READ));

    String view = STRUCTURED_DOCUMENT_EDITOR_VIEW_NAME;
    return new ModelAndView(view, model.asMap());
  }

  @GetMapping("/publishedView/publiclink")
  @ResponseBody
  // If record is not published then return the public link of its parent if published else return
  // empty string
  public String getPublicDocForRecordOrParentOfRecord(
      @RequestParam(value = "globalId", required = true) String globalId) {
    String publicLink = getPublicLinkForRecord(new GlobalIdentifier(globalId).getDbId());
    if (publicLink.equals("")) {
      long id = new GlobalIdentifier(globalId).getDbId();
      if (recordManager.exists(id)) {
        Record data = recordManager.get(id);
        if (data.getParent() != null) {
          publicLink = getPublicLinkForRecord(data.getParent().getId());
          if (!publicLink.isEmpty()) {
            publicLink += "?initialRecordToDisplay=" + data.getId();
          }
        }
      }
    }
    return publicLink;
  }

  private String getPublicLinkForRecord(Long recordID) {
    List<RecordGroupSharing> shared = recordSharingManager.getRecordSharingInfo(recordID);
    for (RecordGroupSharing rgs : shared) {
      if (rgs.getPublicLink() != null) {
        return rgs.getPublicLink();
      }
    }
    return "";
  }

  @Getter
  @AllArgsConstructor
  static class DocumentEditContext {
    private EditStatus editStatus;
    private UserSessionTracker userTracker;
    private StructuredDocument structuredDocument;
  }

  private DocumentEditContext getDocEditContext(long recordId, User user)
      throws RecordAccessDeniedException {
    UserSessionTracker users = getCurrentActiveUsers();
    EditStatus res = recordManager.requestRecordView(recordId, user, users);
    assertViewPermission(recordId, res);
    // now we can load full record, after access check
    StructuredDocument structuredDocument =
        recordManager.getRecordWithFields(recordId, user).asStrucDoc();
    return new DocumentEditContext(res, users, structuredDocument);
  }

  private void assertViewPermission(long recordId, EditStatus res)
      throws RecordAccessDeniedException {
    if (EditStatus.ACCESS_DENIED.equals(res)) {
      throw new RecordAccessDeniedException(getResourceNotFoundMessage("Record", recordId));
    }
  }

  private void prepareDocumentForView(Model model, StructuredDocument doc) {
    Record tempRecord = doc == null ? null : doc.getTempRecord();
    boolean hasAutosave = tempRecord != null;
    model.addAttribute(
        "modificationDate",
        hasAutosave ? tempRecord.getModificationDate() : doc.getModificationDate());
  }
}
