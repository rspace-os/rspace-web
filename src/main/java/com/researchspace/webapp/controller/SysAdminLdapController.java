package com.researchspace.webapp.controller;

import com.researchspace.ldap.UserLdapRepo;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.dto.UserPublicInfo;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/** For Sysadmin LDAP operations */
@Controller("sysAdminLdapController")
@RequestMapping("/system/ldap")
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.NEVER)
public class SysAdminLdapController extends BaseController {

  private @Autowired UserLdapRepo userLdapRepo;

  @GetMapping("/ajax/getUserLdapDetails")
  @ResponseBody
  public UserPublicInfo getUserLdapDetails(@RequestParam("username") String username) {
    User ldapUser = userLdapRepo.findUserByUsername(username);
    if (ldapUser != null) {
      return ldapUser.toPublicInfo();
    }
    return null;
  }

  /** Returns ldap settings page fragment (from JSP). */
  @GetMapping("/ajax/ldapSettingsView")
  public ModelAndView getFileSystemsView(Model model) {
    model.addAttribute("ldapAuthenticationEnabled", properties.isLdapAuthenticationEnabled());
    model.addAttribute("ldapSidVerificationEnabled", properties.isLdapSidVerificationEnabled());
    return new ModelAndView("system/ldapSettings_ajax");
  }

  @GetMapping("/ajax/ldapUsersWithoutSID")
  @ResponseBody
  public AjaxReturnObject<List<String>> getLdapUsersWithoutSID() {
    List<User> allUsers =
        userManager.getAll(); // inefficient, but functionality is not expected to be called often
    List<String> ldapUsersWithoutSid =
        allUsers.stream()
            .filter(
                user ->
                    SignupSource.LDAP.equals(user.getSignupSource())
                        && StringUtils.isBlank(user.getSid()))
            .map(u -> u.getUsername())
            .collect(Collectors.toList());

    log.info("Found " + ldapUsersWithoutSid.size() + " LDAP user(s) without SID.");
    return new AjaxReturnObject<List<String>>(ldapUsersWithoutSid, null);
  }

  @PostMapping("/retrieveSidForLdapUser")
  @ResponseBody
  public AjaxReturnObject<String> retrieveSidForLdapUser(
      @RequestParam("username") String username) {
    String sid = userLdapRepo.retrieveSidForLdapUser(username);
    return new AjaxReturnObject<String>(sid, null);
  }
}
