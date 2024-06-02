package com.researchspace.api.v1.service.impl;

import com.researchspace.api.v1.controller.ExportApiController.ExportApiConfig;
import com.researchspace.model.User;
import java.util.HashMap;
import java.util.Map;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;

class ExportJobParamFactory {

  static JobParameters createJobParams(ExportApiConfig cfg, User user, String jobId) {
    JobParameter scope = new JobParameter(cfg.getScope());
    JobParameter format = new JobParameter(cfg.getFormat());
    JobParameter id = new JobParameter(jobId, true);
    JobParameter subject = new JobParameter(user.getUsername());
    JobParameter includeRevisionHistory =
        new JobParameter(Boolean.toString(cfg.isIncludeRevisionHistory()));

    Map<String, JobParameter> map = new HashMap<>();
    map.put("export.scope", scope);
    map.put("export.format", format);
    map.put("export.id", id);
    map.put("export.user", subject);
    map.put("export.includeRevisionHistory", includeRevisionHistory);

    if (cfg.getId() != null) {
      JobParameter userOrGroupId = new JobParameter(cfg.getId());
      map.put("export.userOrGroupId", userOrGroupId);
    }
    JobParameters params = new JobParameters(map);
    return params;
  }
}
