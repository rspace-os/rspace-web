package com.researchspace.webapp.controller;

import com.researchspace.maintenance.model.WhiteListedSysAdminIPAddress;
import com.researchspace.maintenance.service.WhiteListedIPAddressManager;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.SystemPropertyPermissionManager;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/** Controller for System/Config page */
@Controller
@RequestMapping("/system/config")
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.NEVER)
public class SysAdminConfigController extends BaseController {

  protected static final String SYSTEM_CONFIG_PAGE = "system/config";

  @Autowired private WhiteListedIPAddressManager ipManager;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  @GetMapping
  public ModelAndView getSysAdminConfigurationPage(Model model) {
    User userInSession = userManager.getAuthenticatedUserInSession();
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(userInSession, "public_sharing"));

    return new ModelAndView();
  }

  @GetMapping("/ajax/ipAddresses")
  public @ResponseBody AjaxReturnObject<List<WhiteListedSysAdminIPAddress>> getIpAddresses() {
    User user = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(user);
    List<WhiteListedSysAdminIPAddress> ipAddresses = ipManager.getAll();
    return new AjaxReturnObject<List<WhiteListedSysAdminIPAddress>>(ipAddresses, null);
  }

  /**
   * @param ipAddress
   * @param description
   * @param id
   */
  @PostMapping("/ajax/updateIpAddress")
  public @ResponseBody AjaxReturnObject<WhiteListedSysAdminIPAddress> updateIpAddress(
      @RequestParam String ipAddress, @RequestParam String description, @RequestParam Long id) {
    AjaxReturnObject<WhiteListedSysAdminIPAddress> validated = validate(ipAddress, description);
    if (validated != null) {
      return validated;
    }
    ipAddress = ipAddress.trim();
    User user = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(user);
    WhiteListedSysAdminIPAddress ipaddress = ipManager.get(id);
    ipaddress.setDescription(description);
    ipaddress.setIpAddress(ipAddress);
    WhiteListedSysAdminIPAddress saved = ipManager.save(ipaddress);
    return new AjaxReturnObject<WhiteListedSysAdminIPAddress>(saved, null);
  }

  private AjaxReturnObject<WhiteListedSysAdminIPAddress> validate(
      String ipAddress, String description) {
    if (StringUtils.isEmpty(ipAddress)) {
      ErrorList msg = ErrorList.of(getText("errors.required", new String[] {"ipAddress"}));
      return new AjaxReturnObject<WhiteListedSysAdminIPAddress>(null, msg);
    }
    if (StringUtils.isEmpty(description)) {
      ErrorList msg = ErrorList.of(getText("errors.required", new String[] {"description"}));
      return new AjaxReturnObject<WhiteListedSysAdminIPAddress>(null, msg);
    }
    return null;
  }

  @PostMapping("/ajax/addIpAddress")
  public @ResponseBody AjaxReturnObject<WhiteListedSysAdminIPAddress> addIpAddress(
      @RequestParam String ipAddress, @RequestParam String description) {
    AjaxReturnObject<WhiteListedSysAdminIPAddress> validated = validate(ipAddress, description);
    if (validated != null) {
      return validated;
    }
    ipAddress = ipAddress.trim();
    User user = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(user);
    WhiteListedSysAdminIPAddress saved =
        ipManager.save(new WhiteListedSysAdminIPAddress(ipAddress, description));
    return new AjaxReturnObject<WhiteListedSysAdminIPAddress>(saved, null);
  }

  @PostMapping("/ajax/removeIpAddress/{id}")
  public @ResponseBody AjaxReturnObject<Boolean> removeIpAddress(@PathVariable Long id) {
    User user = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(user);
    ipManager.remove(id);
    return new AjaxReturnObject<Boolean>(true, null);
  }
}
