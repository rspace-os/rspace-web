package com.researchspace.webapp.controller;

import com.researchspace.offline.MobileAPI;
import com.researchspace.offline.model.OfflineRecord;
import com.researchspace.offline.model.OfflineRecordInfo;
import com.researchspace.offline.service.MobileManager;
import java.security.Principal;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping("/mobile")
public class MobileController implements MobileAPI {

  @Autowired private MobileManager mobileManager;

  private final transient Logger log = LoggerFactory.getLogger(MobileController.class);

  @GetMapping("/status")
  @ResponseBody
  public String getServiceStatus() {
    log.debug("service status request");
    return "OK";
  }

  @GetMapping("/authenticate")
  @ResponseBody
  public String authenticateUser(HttpServletRequest request, Principal p) {
    log.debug("authentication successful for user: " + p.getName());
    return "OK";
  }

  @GetMapping("/offlineRecordList")
  @ResponseBody
  @Override
  public List<OfflineRecordInfo> getOfflineRecordList(Principal p) throws Exception {
    log.debug("get offline record list start");
    return mobileManager.getOfflineRecordList(p.getName());
  }

  @RequestMapping(value = "/record/{recordId}", method = RequestMethod.GET)
  @ResponseBody
  @Override
  public OfflineRecord downloadRecord(@PathVariable("recordId") Long recordId, Principal p)
      throws Exception {
    log.debug("download record start");
    return mobileManager.getRecord(recordId, p.getName());
  }

  @PostMapping("/uploadRecord")
  @ResponseBody
  @Override
  public Long uploadRecord(@RequestBody OfflineRecord record, Principal p) throws Exception {
    log.debug("upload record start: " + record.getName() + "/" + record.getImages().size());
    return mobileManager.uploadRecord(record, p.getName());
  }
}
