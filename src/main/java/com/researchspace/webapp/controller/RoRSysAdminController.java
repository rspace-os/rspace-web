package com.researchspace.webapp.controller;

import static com.researchspace.api.v1.model.ValidRoRID.RSPACE_ROR_FORWARD_SLASH_DELIM;

import com.fasterxml.jackson.databind.JsonNode;
import com.researchspace.api.v1.model.ValidRoR;
import com.researchspace.model.User;
import com.researchspace.service.RoRService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Controller for managing RoR identfiers, restricted to Sysadmin. */
@Controller
@RequestMapping("/system/ror")
@Validated
public class RoRSysAdminController extends BaseController {

  @Autowired private RoRService roRService;

  /** Returns ror page fragment (from JSP - which just loads React app). */
  @GetMapping("/ajax/view")
  public String getRorSystemsView() {
    assertIsSysAdmin();
    return "system/ror";
  }

  @GetMapping("/existingGlobalRoRID")
  public @ResponseBody String getExistingRoR() {
    return roRService.getSystemRoRValue();
  }

  @GetMapping("/existingGlobalRoRName")
  public @ResponseBody String getExistingRoRName() {
    return roRService.getRorNameForSystemRoRValue();
  }

  @GetMapping("/rorForID/{rorID}")
  public @ResponseBody JsonNode getSystemRoRForID(@ValidRoR @PathVariable("rorID") String rorID) {
    String searchTerm =
        rorID.replaceAll(RSPACE_ROR_FORWARD_SLASH_DELIM, "/").replaceAll("https://", "");
    return roRService.getSystemRoRDetailsForID(searchTerm);
  }

  @PostMapping("/rorForID/{rorID}")
  public @ResponseBody AjaxReturnObject<Boolean> updateSystemRoRID(
      @ValidRoR @PathVariable("rorID") String rorID) {
    assertIsSysAdmin();
    User subject = userManager.getAuthenticatedUserInSession();
    String actualRoR = rorID.replaceAll(RSPACE_ROR_FORWARD_SLASH_DELIM, "/");
    roRService.updateSystemRoRValue(actualRoR, subject);
    return new AjaxReturnObject<>(true, null);
  }

  @DeleteMapping("/rorForID")
  public @ResponseBody AjaxReturnObject<Boolean> deleteSystemRoRID() {
    assertIsSysAdmin();
    User subject = userManager.getAuthenticatedUserInSession();
    roRService.updateSystemRoRValue("", subject);
    return new AjaxReturnObject<>(true, null);
  }

  private void assertIsSysAdmin() {
    User subject = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(subject);
  }
}
