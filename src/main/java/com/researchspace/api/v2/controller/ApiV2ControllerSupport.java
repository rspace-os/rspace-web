package com.researchspace.api.v2.controller;

import com.researchspace.api.v2.auth.ApiV2Caller;
import com.researchspace.api.v2.resource.ApiV2ResourceCatalog;
import com.researchspace.api.v2.resource.ApiV2ResourceRegistration;
import com.researchspace.model.User;
import jakarta.ws.rs.NotFoundException;

final class ApiV2ControllerSupport {

  private ApiV2ControllerSupport() {}

  static User subject(ApiV2Caller caller) {
    return caller == null ? null : caller.subject();
  }

  static ApiV2ResourceRegistration<?, ?> requireResource(
      ApiV2ResourceCatalog resources, String name) {
    return resources.find(name).orElseThrow(NotFoundException::new);
  }
}
