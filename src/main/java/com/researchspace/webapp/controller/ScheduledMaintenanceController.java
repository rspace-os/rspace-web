package com.researchspace.webapp.controller;

import com.researchspace.maintenance.model.ScheduledMaintenance;
import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

/** Controller for managing scheduled maintenances. */
@Controller
@RequestMapping("/system/maintenance")
public class ScheduledMaintenanceController extends BaseController {

  private static final String OK_RESPONSE = "OK";
  private static final String NO_ACTION_RESPONSE = "NO_ACTION";

  private @Autowired MaintenanceManager maintenanceManager;

  /**
   * Returns maintenance page view (from JSP). Doesn't set any model properties.
   *
   * @return maintenance page view
   */
  @GetMapping("/ajax/view")
  public ModelAndView getMaintenanceView() {
    return new ModelAndView("system/maintenance_ajax");
  }

  /**
   * Returns next scheduled maintenance (may be already active), if there is any.
   *
   * @return {@link ScheduledMaintenance}
   */
  @GetMapping("/ajax/nextMaintenance")
  @ResponseBody
  @IgnoreInLoggingInterceptor(ignoreAll = true)
  public ScheduledMaintenance getNextScheduledMaintenance(HttpServletResponse response) {
    ScheduledMaintenance next = maintenanceManager.getNextScheduledMaintenance();
    // this is returned from cache when there is no scheduled maintenance.
    if (ScheduledMaintenance.NULL.equals(next)) {
      response.setStatus(HttpStatus.NO_CONTENT.value());
      return null;
    }
    return next;
  }

  /**
   * Returns list current or incoming maintenances, ordered by startDate.
   *
   * @return
   */
  @GetMapping("/ajax/list")
  @ResponseBody
  public List<ScheduledMaintenance> getScheduledMaintenanceList() {
    return maintenanceManager.getAllFutureMaintenances();
  }

  /**
   * Returns list of old maintenances, ordered by startDate.
   *
   * @return
   */
  @GetMapping("/ajax/expired/list")
  @ResponseBody
  public List<ScheduledMaintenance> getExpiredMaintenanceList() {
    return maintenanceManager.getOldMaintenances();
  }

  @PostMapping("/ajax/create")
  @ResponseBody
  public Long createScheduledMaintenance(
      @Valid @RequestBody ScheduledMaintenance newMaintenance, BindingResult errors) {
    handleInvalidRequest(errors, "Creating new maintenance failed");
    User user = userManager.getAuthenticatedUserInSession();
    ScheduledMaintenance savedMaintenance =
        maintenanceManager.saveScheduledMaintenance(newMaintenance, user);
    return savedMaintenance.getId();
  }

  private void handleInvalidRequest(BindingResult errors, String messagePart) {
    if (errors.hasErrors()) {
      ErrorList el = new ErrorList();
      inputValidator.populateErrorList(errors, el);
      throw new IllegalArgumentException(
          getText(
              "operation.failed.message",
              new String[] {messagePart, el.getAllErrorMessagesAsStringsSeparatedBy(",")}));
    }
  }

  @PostMapping("/ajax/update")
  @ResponseBody
  public String updateScheduledMaintenance(
      @Valid @RequestBody ScheduledMaintenance updatedMaintenance, BindingResult errors) {
    handleInvalidRequest(errors, "Updating new maintenance failed");
    User user = userManager.getAuthenticatedUserInSession();
    maintenanceManager.saveScheduledMaintenance(updatedMaintenance, user);
    return OK_RESPONSE;
  }

  @PostMapping("/ajax/delete")
  @ResponseBody
  public String removeScheduledMaintenance(@RequestParam("id") Long id) {
    User user = userManager.getAuthenticatedUserInSession();
    maintenanceManager.removeScheduledMaintenance(id, user);
    return OK_RESPONSE;
  }

  @PostMapping("/ajax/stopUserLogin")
  @ResponseBody
  public String stopUserLogin() {
    ScheduledMaintenance scheduledMaintenance = maintenanceManager.getNextScheduledMaintenance();
    if (scheduledMaintenance == null) {
      return NO_ACTION_RESPONSE;
    }

    User user = userManager.getAuthenticatedUserInSession();
    scheduledMaintenance.setStopUserLoginDate(new Date());
    maintenanceManager.saveScheduledMaintenance(scheduledMaintenance, user);
    return OK_RESPONSE;
  }

  @PostMapping("/ajax/finishNow")
  @ResponseBody
  public String finishMaintenance() {
    ScheduledMaintenance scheduledMaintenance = maintenanceManager.getNextScheduledMaintenance();
    if (scheduledMaintenance == null || !scheduledMaintenance.isActiveNow()) {
      return NO_ACTION_RESPONSE;
    }

    User user = userManager.getAuthenticatedUserInSession();
    scheduledMaintenance.setEndDate(new Date());
    maintenanceManager.saveScheduledMaintenance(scheduledMaintenance, user);
    return OK_RESPONSE;
  }
}
