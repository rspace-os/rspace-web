package com.researchspace.webapp.controller;

import static com.researchspace.session.UserSessionTracker.USERS_KEY;

import com.researchspace.model.User;
import com.researchspace.model.record.Record;
import com.researchspace.offline.service.OfflineManager;
import com.researchspace.session.UserSessionTracker;
import java.security.Principal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

/** General controller for offline activities shared across web/mobile/desktop apps */
@Controller
@RequestMapping("/offlineWork")
public class OfflineWorkController extends BaseController {

  @Autowired private OfflineManager offlineManager;

  /* leaving @RequestMapping, as switching to @GetMapping cause MVC test failure */
  @RequestMapping(value = "/selectForOffline", method = RequestMethod.POST)
  @ResponseBody
  public String selectForOffline(@RequestParam("recordIds[]") Long[] recordIds, Principal principal)
      throws Exception {

    UserSessionTracker activeUsers =
        (UserSessionTracker) getServletContext().getAttribute(USERS_KEY);
    User user = userManager.getUserByUsername(principal.getName());

    StringBuffer buf = new StringBuffer();
    buf.append("Done.\n");

    for (Long id : recordIds) {
      try {
        Record record = recordManager.get(id);
        offlineManager.addRecordForOfflineWork(record, user, activeUsers);
        buf.append("Record " + id + " marked for offline work.");
      } catch (Exception e) {
        log.debug(e.getMessage());
        buf.append("Exception on adding offline work with record " + id + ".");
      }
    }

    return buf.toString();
  }

  /* leaving @RequestMapping, as switching to @GetMapping cause MVC test failure */
  @RequestMapping(value = "/removeFromOffline", method = RequestMethod.POST)
  @ResponseBody
  public String removeFromOffline(
      @RequestParam("recordIds[]") Long[] recordIds, Principal principal) throws Exception {

    User user = userManager.getUserByUsername(principal.getName());

    StringBuffer buf = new StringBuffer();
    buf.append("Done.\n");
    for (Long id : recordIds) {
      try {
        offlineManager.removeRecordFromOfflineWork(id, user);
        buf.append("Finished offline work for record " + id + ".");
      } catch (Exception e) {
        log.debug(e.getMessage());
        buf.append("Exception on finishing offline work with record " + id + ".");
      }
    }
    return buf.toString();
  }
}
