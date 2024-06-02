package com.researchspace.webapp.controller;

import com.researchspace.model.User;
import com.researchspace.service.SystemPropertyPermissionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/import")
public class ImportController extends BaseController {
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  @GetMapping("/archiveImport")
  public ModelAndView importPage(Model model) {
    User subject = userManager.getAuthenticatedUserInSession();
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(subject, "public_sharing"));
    return new ModelAndView("import/archiveImport");
  }
}
