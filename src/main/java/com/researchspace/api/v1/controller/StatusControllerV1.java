package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.model.Status;
import com.researchspace.model.DeploymentPropertyType;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.properties.PropertyHolder;
import com.researchspace.webapp.controller.DeploymentProperty;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@ApiController("statusController")
@RequestMapping(path = "/api/v1/status*")
@Slf4j
public class StatusControllerV1 {

  private @Autowired ApiAccountInitialiser accountInitialiser;
  private @Autowired PropertyHolder properties;

  Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  @GetMapping()
  public Status status(@RequestAttribute(name = "user") User user) {
    log.info("Getting status for {}", user.getUsername());
    return ok();
  }

  private Status ok() {
    return new Status("OK", properties.getVersionMessage());
  }

  /**
   * Initialises a user account requires Beta-API enablement
   *
   * @param user
   * @return
   */
  @PostMapping("/touch")
  @DeploymentProperty(DeploymentPropertyType.API_BETA_ENABLED)
  public Status touch(@RequestAttribute(name = "user") User user) {
    if (!user.isContentInitialized()) {
      accountInitialiser.initialiseUser(user);
    }
    return ok();
  }
}
