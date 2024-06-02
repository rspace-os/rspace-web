package com.researchspace.api.v1.config;

import com.researchspace.api.v1.controller.ApiAccountInitialiser;
import com.researchspace.model.User;
import com.researchspace.service.IContentInitializer;
import org.springframework.beans.factory.annotation.Autowired;

public class AccountInitialiserImpl implements ApiAccountInitialiser {

  private @Autowired IContentInitializer contentInitialiser;

  @Override
  public void initialiseUser(User user) {
    contentInitialiser.init(user.getId());
  }
}
