package com.axiope.userimport;

import com.researchspace.model.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Null implementation for dev and test environments that merely logs that it was called. */
public class NullOpPostUserCreate implements IPostUserCreationSetUp {

  Logger log = LoggerFactory.getLogger(NullOpPostUserCreate.class);

  @Override
  public void postUserCreate(User created, HttpServletRequest req, String origPwd) {
    log.info(" No-op post user create operation called");
  }
}
