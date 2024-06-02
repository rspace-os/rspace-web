package com.researchspace.webapp.controller;

import java.util.Collections;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.httpclient.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

/**
 * Manipulates response headers to advise browser on whether to cache the returned page or not, and
 * for how long. <br>
 * This interceptor will act in response to a {@link BrowserCacheAdvice} annotation on the
 * Controller class or method that handled the request. If no such annotation is present, the
 * response is not altered.
 *
 * <p>This interceptor will <em>NOT</em> have any effect on HttpServletResponse objects that are
 * directly returned to the servlet output stream from a controller - in that case, the response
 * headers should be manipulated directly, for example using {@link ResponseUtil}.
 */
@Component
public class BrowserCacheAdviceInterceptor extends HandlerInterceptorAdapter {

  private Set<String> urls = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());

  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws Exception {
    if (handler instanceof HandlerMethod) {
      HandlerMethod handlerNtv = (HandlerMethod) handler;
      EtagBrowserCacheAdvice bca =
          handlerNtv.getMethod().getAnnotation(EtagBrowserCacheAdvice.class);

      if (bca != null && getIfNoneMatchHeader(request) != null) {
        String req = getURLFromString(request);
        if (EtagBrowserCacheAdvice.URL.equals(bca.cacheStrategy())) {
          if (urls.contains(req)) {
            response.setStatus(HttpStatus.SC_NOT_MODIFIED);
            return false;
          }
        }
      }
    }
    return true;
  }

  private String getIfNoneMatchHeader(HttpServletRequest request) {
    return request.getHeader("If-None-Match");
  }

  private String getURLFromString(HttpServletRequest request) {
    String req = request.getRequestURL().toString();
    return req;
  }

  public void postHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView mav)
      throws Exception {
    // if we're a regular handler, set response headers if need be
    if (handler instanceof HandlerMethod) {
      HandlerMethod handlerNtv = (HandlerMethod) handler;
      setCacheAdvice(response, handlerNtv);
      setETagAdvice(request, response, handlerNtv, mav);
    }
  }

  private void setETagAdvice(
      HttpServletRequest request,
      HttpServletResponse response,
      HandlerMethod handler,
      ModelAndView mav) {
    EtagBrowserCacheAdvice bca = handler.getMethod().getAnnotation(EtagBrowserCacheAdvice.class);
    if (bca != null) {
      if (EtagBrowserCacheAdvice.URL.equals(bca.cacheStrategy())) {
        String req = getURLFromString(request);
        urls.add(req);
        generateEtag(req, response);
      }
    }
  }

  private void generateEtag(String value, HttpServletResponse response) {
    response.setHeader("ETag", "\"" + value + "\"");
  }

  private void setCacheAdvice(HttpServletResponse response, HandlerMethod handlerNtv) {
    // get from method i
    BrowserCacheAdvice bca = handlerNtv.getMethod().getAnnotation(BrowserCacheAdvice.class);
    if (bca == null) {
      bca = handlerNtv.getMethod().getDeclaringClass().getAnnotation(BrowserCacheAdvice.class);
    }
    if (bca != null) {
      if (bca.cacheTime() < 0) {
        return; // ignore
      }
      if (bca.cacheTime() == BrowserCacheAdvice.NEVER) {
        // commented out for Safari but seems to work for other browsers too.
        response.setHeader("Cache-Control", "no-store,max-age=0,no-cache,must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", 0L);
      } else {
        response.setHeader("Cache-Control", "max-age=" + bca.cacheTime());
        response.setDateHeader("Last-Modified", new Date().getTime());
        // response.setHeader("Pragma", "no-cache");
        response.setDateHeader("Expires", bca.cacheTime());
      }
    }
  }
}
