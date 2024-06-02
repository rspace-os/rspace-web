package com.researchspace.webapp.controller;

import com.researchspace.service.RoRService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Controller for viewing RoR identfiers, accessible to all users. */
@Controller
@RequestMapping("/global/ror")
public class RoRController extends BaseController {

  @Autowired private RoRService roRService;

  @GetMapping("/existingGlobalRoRID")
  public @ResponseBody String getExistingRoR() {
    return roRService.getSystemRoRValue();
  }

  @GetMapping("/existingGlobalRoRName")
  public @ResponseBody String getExistingRoRName() {
    return roRService.getRorNameForSystemRoRValue();
  }
}
