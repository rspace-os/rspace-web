package com.researchspace.api.v1.service;

import com.researchspace.api.v1.controller.ExportApiController.ExportApiConfig;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.core.util.throttling.TooManyRequestsException;
import com.researchspace.model.User;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;

/** Top-level handler for exporting via API */
public interface ExportApiHandler {

  /**
   * Launches an export process
   *
   * @param cfg
   * @param user
   * @return An optional ApiJob if job could be scheduled successfully.
   * @throws AuthorizationException if not permitted to perform the export
   * @throws TooManyRequestsException if there are too many exports running
   */
  Optional<ApiJob> export(ExportApiConfig cfg, User user);
}
