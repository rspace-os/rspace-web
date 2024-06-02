package com.researchspace.api.v1;

import com.researchspace.api.v1.model.ApiInventorySystemSettings;
import com.researchspace.model.User;
import javax.servlet.ServletRequest;
import javax.validation.Valid;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/** Read and update system settings through API. Currently only supports subset of the settings. */
@RequestMapping("/api")
public interface SystemSettingsApi {

  @GetMapping("/inventory/v1/system/settings")
  ApiInventorySystemSettings getInventorySettings(ServletRequest req, User sysadmin);

  @PutMapping(value = "/inventory/v1/system/settings")
  ApiInventorySystemSettings updateInventorySettings(
      ServletRequest req,
      @RequestBody @Valid ApiInventorySystemSettings newSettings,
      BindingResult errors,
      User sysadmin)
      throws BindException;
}
