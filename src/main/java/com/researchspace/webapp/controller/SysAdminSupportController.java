package com.researchspace.webapp.controller;

import com.researchspace.admin.service.SysAdminManager;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.licenseserver.model.License;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.service.EmailBroadcast;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.impl.EmailBroadcastImp.EmailContent;
import com.researchspace.service.impl.StrictEmailContentGenerator;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.tools.generic.DateTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/** For Sysadmin 'Maintenance' page */
@Controller("sysAdminSupportController")
@RequestMapping("/system/support")
@BrowserCacheAdvice(cacheTime = BrowserCacheAdvice.NEVER)
public class SysAdminSupportController extends BaseController {
  protected static final String SYSTEM_SUPPORT_PAGE = "system/support";

  private @Autowired SysAdminManager sysMgr;
  private @Autowired StrictEmailContentGenerator strictEmailContentGenerator;

  private EmailBroadcast emailSender;
  @Autowired private SystemPropertyPermissionManager systemPropertyPermissionManager;

  @Autowired
  @Qualifier("emailBroadcast")
  public void setEmailSender(EmailBroadcast emailSender) {
    this.emailSender = emailSender;
  }

  @GetMapping
  public ModelAndView getSupportPage(Model model) {
    User userInSession = userManager.getAuthenticatedUserInSession();
    model.addAttribute(
        "publish_allowed",
        systemPropertyPermissionManager.isPropertyAllowed(userInSession, "public_sharing"));
    return new ModelAndView();
  }

  @PostMapping("/ajax/forceRefreshLicense")
  @ResponseBody
  public AjaxReturnObject<Boolean> forceRefreshLicense() {
    assertSubjectIsSysadmin();
    Boolean updated = licenseService.forceRefreshLicense();
    return new AjaxReturnObject<>(updated, null);
  }

  @GetMapping("/ajax/license")
  @ResponseBody
  public AjaxReturnObject<License> getLicense() {
    assertSubjectIsSysadmin();
    License currentLicense = licenseService.getLicense();
    return new AjaxReturnObject<>(currentLicense, null);
  }

  private void assertSubjectIsSysadmin() {
    User user = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(user);
  }

  @GetMapping("/ajax/viewLog")
  @ResponseBody
  public AjaxReturnObject<List<String>> viewServerErrorLog(
      @RequestParam(value = "numLines", required = false, defaultValue = "1000") Integer numlines) {
    assertSubjectIsSysadmin();
    try {
      List<String> lines = sysMgr.getLastNLinesLogs(numlines);
      return new AjaxReturnObject<>(lines, null);
    } catch (IOException e) {
      ErrorList errs = logAndGetError(e);
      return new AjaxReturnObject<>(null, errs);
    }
  }

  @PostMapping("/ajax/mailLog")
  @ResponseBody
  public AjaxReturnObject<Boolean> mailServerErrorLog(
      @RequestParam(value = "message", required = false) String message,
      @RequestParam(value = "numLines", defaultValue = "500", required = false) int numLines) {
    User user = userManager.getAuthenticatedUserInSession();
    assertUserIsSysAdmin(user);

    try {
      List<String> lines = sysMgr.getLastNLinesLogs(numLines);
      EmailContent content = generateEmailContent(user, lines, message);
      emailSender.sendHtmlEmail(
          getText(
              "system.support.serverlogs.supportEmailTitle",
              new String[] {properties.getServerUrl()}),
          content,
          TransformerUtils.toList(properties.getRSpaceSupportEmail()),
          null);

    } catch (IOException e) {
      ErrorList errs = logAndGetError(e);
      return new AjaxReturnObject<>(null, errs);
    }

    return new AjaxReturnObject<>(true, null);
  }

  private ErrorList logAndGetError(IOException e) {
    ErrorList errs = getErrorListFromMessageCode("system.support.serverlogs.error", e.getMessage());
    log.error(errs.getAllErrorMessagesAsStringsSeparatedBy(","));
    return errs;
  }

  private EmailContent generateEmailContent(User user, List<String> lines, String message) {
    Map<String, Object> config = new HashMap<>();
    config.put("dateOb", new Date());
    config.put("user", user);
    config.put("date", new DateTool());
    if (!StringUtils.isBlank(message)) {
      config.put("message", StringEscapeUtils.escapeHtml(message.trim()));
    }
    config.put("logLines", lines);
    return strictEmailContentGenerator.generatePlainTextAndHtmlContent(
        "supportLogFiles.vm", config);
  }
}
