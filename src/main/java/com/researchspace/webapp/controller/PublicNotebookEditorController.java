package com.researchspace.webapp.controller;

import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RecordSharingManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/public/publishedView/notebook")
public class PublicNotebookEditorController extends BaseController {

  @Autowired private RecordSharingManager recordSharingManager;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  @Value("${publishing.anonymousGuest.password}")
  private String anonymousPassword;

  /**
   * Shows the notebook page and passes the notebook id for the journal plugin to use.
   *
   * @return A model and view to the notebookEDitor JSP
   * @throws AuthorizationException
   */
  @GetMapping("/{publicLink}")
  public ModelAndView openNotebook(
      @PathVariable("publicLink") String publicLink,
      @RequestParam(value = "initialRecordToDisplay", required = false) Long entryId,
      Model model,
      HttpServletRequest request,
      HttpServletResponse response)
      throws AuthorizationException, IOException {
    User user = userManager.getAuthenticatedUserInSession();
    if (user == null) {
      user = getUserByUsername(RecordGroupSharing.ANONYMOUS_USER);
      PublicDocumentsUtilities.loginAnonymousUser(anonymousPassword);
    }
    RecordGroupSharing rgs = recordSharingManager.getByPublicLink(publicLink);
    if (rgs == null) {
      throw new PublicLinkNotFoundException(publicLink);
    }
    User sharedBy = rgs.getSharedBy();
    if (!systemPropertyPermissionManager.isPropertyAllowed(sharedBy, "public_sharing")) {
      WebUtils.issueRedirect(request, response, "/public/publishIsDisabled");
    }
    Long notebookId = rgs.getShared().getId();
    Notebook notebook = folderManager.getNotebook(notebookId);
    if (entryId != null) {
      StructuredDocument entry = (StructuredDocument) recordManager.get(entryId);
      if (entry.isNotebookEntry() && entry.getParentNotebook().getId().equals(notebookId)) {
        model.addAttribute("initialRecordToDisplay", entryId);
      }
    }

    ActionPermissionsDTO permDTO = new ActionPermissionsDTO();
    // none of these values appear to be used in any JSP, apart from 'createRecord'
    permDTO.setCreateRecord(false);
    permDTO.setDeleteRecord(false);
    permDTO.setCreateFolder(false);
    permDTO.setCreateNotebook(false);
    permDTO.setMove(false);
    permDTO.setRename(false);
    permDTO.setCopy(false);

    model.addAttribute("permDTO", permDTO);
    model.addAttribute("selectedNotebookId", notebookId);
    model.addAttribute("selectedNotebookName", notebook.getName());
    model.addAttribute("notebook", notebook);
    model.addAttribute("entryCount", notebook.getEntryCount());
    String publicationSummary =
        rgs.getPublicationSummary() == null
            ? ""
            : rgs.getPublicationSummary().replace("\n", "").replace("\r", "");
    model.addAttribute("publicationSummary", publicationSummary);
    if (rgs.isDisplayContactDetails()) {
      model.addAttribute("contactDetails", sharedBy.getEmail());
    }
    model.addAttribute("publishOnInternet", rgs.isPublishOnInternet());
    model.addAttribute("user", user);
    PublicDocumentsUtilities.addGroupAttributes(model, user, groupManager.listGroupsForUser());

    model.addAttribute("canSeeNotebook", true);
    model.addAttribute("canEdit", false);

    return new ModelAndView("notebookEditor/public_notebookView", model.asMap());
  }
}
