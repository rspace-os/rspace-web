package com.axiope.userimport;

import com.researchspace.model.User;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Null implementation for dev and test environments that merely logs that it was called. */
@Component("nullOpPostUserCreate")
@Profile(value = {"dev,run"})
public class NullOpPostUserCreate implements IPostUserCreationSetUp {

  Logger log = LoggerFactory.getLogger(NullOpPostUserCreate.class);

  @Override
  public void postUserCreate(User created, HttpServletRequest req, String origPwd) {
    log.info(" No-op post user create operation called");
  }
}
