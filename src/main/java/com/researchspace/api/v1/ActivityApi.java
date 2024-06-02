package com.researchspace.api.v1;

import com.researchspace.api.v1.controller.ActivityApiPaginationCriteria;
import com.researchspace.api.v1.controller.ApiActivitySearchResult;
import com.researchspace.api.v1.controller.ApiActivitySrchConfig;
import com.researchspace.model.User;
import javax.validation.Valid;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/activity")
public interface ActivityApi {

  @GetMapping
  public ApiActivitySearchResult search(
      @Valid ActivityApiPaginationCriteria pgCrit,
      @Valid ApiActivitySrchConfig srchConfig,
      BindingResult errors,
      @RequestAttribute(name = "user") User user)
      throws BindException;
}
