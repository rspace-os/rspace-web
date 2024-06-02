package com.researchspace.webapp.integrations.wopi;

import com.researchspace.auth.wopi.WopiAuthToken;
import com.researchspace.model.User;
import com.researchspace.model.permissions.SecurityLogger;
import com.researchspace.service.UserManager;
import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * Authenticates an incoming WOPI request based on access token.<br>
 * Adds User to request as 'user' attribute for use in Controllers.
 */
@Component
public class WopiAuthorisationInterceptor implements HandlerInterceptor {

  protected static final String FILE_ID_PATH_VAR_NAME = "fileId";

  @Autowired private WopiAccessTokenHandler accessTokenHandler;

  @Autowired private UserManager userManager;

  protected static final Logger log = LoggerFactory.getLogger(SecurityLogger.class);

  @Override
  public boolean preHandle(
      HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler)
      throws IOException {
    String accessToken = request.getParameter(WopiController.ACCESS_TOKEN_PARAM_NAME);
    Map<String, String> pathVariables =
        (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
    String fileId = pathVariables.get(FILE_ID_PATH_VAR_NAME);

    String username = accessTokenHandler.getUsernameFromAccessToken(accessToken, fileId);
    if (username == null) {
      log.warn("Access token {} not matching any user", accessToken);
      response.setStatus(401);
      return false;
    }

    User user = userManager.getUserByUsername(username);
    SecurityUtils.getSubject().login(new WopiAuthToken(user.getUsername(), accessToken));
    request.setAttribute("user", user);
    return true;
  }

  @Override
  public void postHandle(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      ModelAndView modelAndView)
      throws Exception {
    SecurityUtils.getSubject().logout();
  }
}
