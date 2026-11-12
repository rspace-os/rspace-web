package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.json.ApiV2JsonBody;
import java.lang.reflect.Type;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/**
 * Retains the presence of a body version for infrastructure errors raised at commit. Features
 * extend this advice with their own controller package, as they do {@link ApiV2ControllerAdvice}.
 */
@ControllerAdvice(basePackageClasses = ApiV2ControllerAdvice.class)
public class ApiV2VersionPreconditionAdvice extends RequestBodyAdviceAdapter {
  /** Request attribute set to {@code true} when the request body carries a non-null version. */
  public static final String VERSION_PRECONDITION_ATTRIBUTE = "api.v2.versionPrecondition";

  private static final String VERSION = "version";

  @Override
  public boolean supports(
      MethodParameter parameter, Type type, Class<? extends HttpMessageConverter<?>> converter) {
    return true;
  }

  @Override
  public Object afterBodyRead(
      Object body,
      HttpInputMessage input,
      MethodParameter parameter,
      Type type,
      Class<? extends HttpMessageConverter<?>> converter) {
    boolean versioned =
        body instanceof ApiV2JsonBody json
            ? json.node().hasNonNull(VERSION)
            : hasVersionProperty(body);
    if (versioned
        && RequestContextHolder.getRequestAttributes()
            instanceof ServletRequestAttributes attributes) {
      attributes.getRequest().setAttribute(VERSION_PRECONDITION_ATTRIBUTE, true);
    }
    return body;
  }

  private static boolean hasVersionProperty(Object body) {
    var bean = new BeanWrapperImpl(body);
    return bean.isReadableProperty(VERSION) && bean.getPropertyValue(VERSION) != null;
  }
}
