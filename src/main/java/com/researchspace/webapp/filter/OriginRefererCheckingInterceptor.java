package com.researchspace.webapp.filter;

import com.researchspace.core.util.RequestUtil;
import com.researchspace.model.permissions.SecurityLogger;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/**
 * Interceptor checking Origin and Referer headers against CSRF attacks (RSPAC-1176). Enabled by
 * setting deployment property 'csrf.filters.enabled' to 'true'.
 */
public class OriginRefererCheckingInterceptor extends HandlerInterceptorAdapter {

  protected static final Logger SECURITY_LOG = LoggerFactory.getLogger(SecurityLogger.class);

  private @Autowired OriginRefererChecker checker;

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {

    Optional<String> errMsg = checker.checkOriginReferer(request, response);
    if (!errMsg.isPresent()) {
      return true;

    } else {
      SECURITY_LOG.info(
          "Potential CSRF attempt: {} for request {} from {}",
          errMsg.get(),
          request.getRequestURL(),
          RequestUtil.remoteAddr(request));
      throw new IllegalStateException(
          "Request can't be processed - incorrect Origin/Referer headers:" + errMsg.get());
    }
  }
}
