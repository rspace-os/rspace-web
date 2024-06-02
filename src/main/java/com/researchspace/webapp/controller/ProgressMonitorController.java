package com.researchspace.webapp.controller;

import com.researchspace.core.util.progress.ProgressMonitor;
import javax.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Gets the progress monitor */
@Controller
@RequestMapping(value = "/progress")
@ResponseBody
public class ProgressMonitorController {

  @GetMapping("/{monitorId}")
  public ProgressMonitor getMonitor(HttpSession session, @PathVariable String monitorId) {
    ProgressMonitor monitor = (ProgressMonitor) session.getAttribute(monitorId);
    if (monitor == null) {
      monitor = ProgressMonitor.NULL_MONITOR; // in case this method is called outwith an operation.
    }
    return monitor;
  }
}
