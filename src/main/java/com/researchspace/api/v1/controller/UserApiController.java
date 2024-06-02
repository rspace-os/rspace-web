package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.UserApi;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.api.v1.model.ApiUserPost;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.webapp.controller.IgnoreInLoggingInterceptor;
import javax.validation.Valid;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * New, testing API for creating user accounts; should be in 'run' profile so as not to leak into
 * production.
 */
@ApiController
@Profile("run")
public class UserApiController extends BaseApiController implements UserApi {

  private @Autowired UserManager userMgr;

  @Override
  @IgnoreInLoggingInterceptor(ignoreRequestParams = {"password"})
  public ApiUser createNewUser(
      @RequestBody @Valid ApiUserPost userToCreate,
      BindingResult errors,
      @RequestAttribute(name = "user") User sysadmin)
      throws BindException {
    log.info("Incoming user creation for {}", userToCreate.getUsername());
    if (errors.hasErrors()) {
      throw new BindException(errors);
    }
    if (!sysadmin.hasSysadminRole()) {
      throw new AuthorizationException("Creating user accounts requires a sysadmin role");
    }

    // generate API key if requested, return this in the returned User representation.
    return null;
  }
}
