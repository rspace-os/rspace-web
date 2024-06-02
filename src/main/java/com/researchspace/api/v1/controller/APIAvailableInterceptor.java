package com.researchspace.api.v1.controller;

import com.researchspace.model.views.ServiceOperationResult;
import com.researchspace.service.ApiAvailabilityHandler;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.HandlerInterceptor;

public class APIAvailableInterceptor implements HandlerInterceptor {

  private @Autowired ApiAvailabilityHandler apiHandler;

  void setApiHandler(ApiAvailabilityHandler apiHandler) {
    this.apiHandler = apiHandler;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    ServiceOperationResult<String> res =
        apiHandler.isAvailable(null, request); // don't need user yet.
    if (!res.isSucceeded()) {
      throw new APIUnavailableException(res.getEntity());
    }
    return true;
  }
}
