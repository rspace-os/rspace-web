package com.researchspace.webapp.integrations.mendeley;

import com.researchspace.mendeley.api.Document;
import com.researchspace.mendeley.api.Mendeley;
import com.researchspace.mendeley.api.Profile;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
public class MendeleyController {
  Mendeley mendeley;

  @Autowired
  public MendeleyController(Mendeley mendeley) {
    this.mendeley = mendeley;
  }

  Logger log = LoggerFactory.getLogger(MendeleyController.class);

  @RequestMapping(method = RequestMethod.GET, value = "/mendeley/actions")
  public String onSuccess(Model model) {
    log.info("success");
    List<Document> docs = mendeley.getDocuments();
    model.addAttribute("documents", docs);
    return "connect/mendeley/success";
  }

  @RequestMapping(method = RequestMethod.GET, value = "/mendeley/profile")
  public String profile(Model model) {
    Profile profile = mendeley.getMyProfile();
    model.addAttribute("profile", profile);
    return "connect/mendeley/profile";
  }
}
