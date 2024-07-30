package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.UserDetailsSysAdminApi;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestAttribute;

@ApiController
public class UserDetailsSysAdminApiController implements UserDetailsSysAdminApi {
  @Autowired private UserManager userManager;
  @Autowired private UserApiKeyManager apiKeyMgr;

  @Value("${sysadmin.apikey.access}")
  private boolean sysadminApiKeyAccess;

  @Override
  public Map<String, String> getAllApiKeyInfo(@RequestAttribute(name = "user") User user) {
    if (!user.hasSysadminRole()) {
      throw new AuthorizationException("Only sysadmin can use this API");
    }
    if (!sysadminApiKeyAccess) {
      throw new AuthorizationException("Reading apiKeys by sysadmin is not enabled on this server");
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
