package com.researchspace.webapp.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/oneDrive")
public class OneDriveController {

  @GetMapping("/redirect")
  @ResponseBody
  public ModelAndView redirect() {
    return new ModelAndView("connect/oneDrive/oneDriveRedirect");
  }

  @GetMapping("/test")
  @ResponseBody
  public ModelAndView test() {
    return new ModelAndView("oneDriveTest");
  }
}
