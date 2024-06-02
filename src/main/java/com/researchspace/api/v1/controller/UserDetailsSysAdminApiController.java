package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.UserDetailsSyadminApi;
import com.researchspace.model.SignupSource;
import com.researchspace.model.User;
import com.researchspace.model.UserApiKey;
import com.researchspace.service.UserApiKeyManager;
import com.researchspace.service.UserManager;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestAttribute;

@ApiController
public class UserDetailsSysAdminApiController implements UserDetailsSyadminApi {
  @Autowired private UserManager userManager;
  @Autowired private UserApiKeyManager apiKeyMgr;

  @Override
  public Map<String, String> getAllApiKeyInfo(@RequestAttribute(name = "user") User user) {
    boolean isSysadmin = user.hasSysadminRole();
    if (!isSysadmin) {
      throw new AuthorizationException("Only sysadmin can use this API");
    }
    Map<String, String> allUserInfo = new HashMap<>();
    for (User aUser : userManager.getUsers()) {
      Optional<UserApiKey> optKey = apiKeyMgr.getKeyForUser(aUser);
      UserApiKey userApiKey = null;
      if (optKey.isPresent()) {
        userApiKey = optKey.get();
      } else if (SignupSource.MANUAL.equals(aUser.getSignupSource())) {
        userApiKey = apiKeyMgr.createKeyForUser(aUser); // we want API keys for all real users
      }
      if (userApiKey != null) {
        allUserInfo.put(aUser.getUsername(), userApiKey.getApiKey());
      }
    }
    return allUserInfo;
  }
}
