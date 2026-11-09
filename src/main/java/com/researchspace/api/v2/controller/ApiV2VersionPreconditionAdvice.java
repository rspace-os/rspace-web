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

/** Retains the presence of a body version for infrastructure errors raised at commit. */
@ControllerAdvice(basePackageClasses = ApiV2ControllerAdvice.class)
public class ApiV2VersionPreconditionAdvice extends RequestBodyAdviceAdapter {
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
    var bean = new BeanWrapperImpl(body);
    boolean versioned =
        body instanceof ApiV2JsonBody json
            ? json.node().hasNonNull("version")
            : bean.isReadableProperty("version") && bean.getPropertyValue("version") != null;
    if (versioned
        && RequestContextHolder.getRequestAttributes()
            instanceof ServletRequestAttributes attributes) {
      attributes.getRequest().setAttribute("api.v2.versionPrecondition", true);
    }
    return body;
  }
}
