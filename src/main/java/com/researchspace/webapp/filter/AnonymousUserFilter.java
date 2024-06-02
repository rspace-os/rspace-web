package com.researchspace.webapp.filter;

import com.researchspace.model.User;
import com.researchspace.session.SessionAttributeUtils;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.session.Session;
import org.apache.shiro.web.util.WebUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter to ensure the anonymous user may only use the Urls defined in web.xml
 * `urlsAllowedForAnonymousAccess`
 *
 * <p>Also redirects anonymous user to the /login page if they attempt to access a url in the
 * 'typicalRootUrls' (assumption that this is an RSPace user (with no credentials in the browser)
 * who has authenticated as anonymous by viewing a public doc FIRST and THEN wants to view RSpace).
 */
@Slf4j
public class AnonymousUserFilter extends OncePerRequestFilter {
  // these must not include any urls which public documents need to use (eg '/gallery' or
  // '/userprofile').
  private String[] typicalRootUrls =
      new String[] {"/workspace", "/dashboard", "/apps", "/groups", "/system"};

  public void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    List<String> urlsAllowedForAnonymousAccess =
        Arrays.asList(
            getFilterConfig().getInitParameter("urlsAllowedForAnonymousAccess").split(","));
    String requestUrl = "";
    boolean urlNotAllowedForAnonymous = true;
    requestUrl = request.getRequestURI();
    for (String allowedUrl : urlsAllowedForAnonymousAccess) {
      if (requestUrl.startsWith(allowedUrl)) {
        urlNotAllowedForAnonymous = false;
        break;
      }
    }
    if (urlNotAllowedForAnonymous) {
      Session session = SecurityUtils.getSubject().getSession();
      User user = session != null ? (User) session.getAttribute(SessionAttributeUtils.USER) : null;
      if (user != null && user.isAnonymousGuestAccount()) {
        // current jquery manipulation of the public page still results in requests to the non
        // public urls
        // being made, as well. Its not an error, so logging at debug to avoid swamping the logs.
        log.debug("ANONYMOUS user tried to access: " + requestUrl + " and was blocked");
        for (String rootUrl : typicalRootUrls) {
          if ((requestUrl.startsWith(rootUrl) || requestUrl.equals("/"))
              && !requestUrl.contains("/poll")
              && !requestUrl.contains("/ajax/nextMaintenance")) {
            // its a user who has been locked out of RSpace by using a public doc and then trying to
            // access their workspace
            // this will happen if they were not logged in to RSpace before viewing a public doc
            String loginUrl = "/login";
            log.info("ANONYMOUS user redirected to login page from: " + requestUrl);
            WebUtils.issueRedirect(request, response, loginUrl);
            break;
          }
        }
        // returning here stops the anonymous user from viewing the url
        return;
      }
    }
    chain.doFilter(request, response);
  }
}
