package com.researchspace.api.v1.service.impl;

import com.researchspace.api.v1.controller.ExportApiController.ExportApiConfig;
import com.researchspace.model.User;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;

class ExportJobParamFactory {

  static JobParameters createJobParams(ExportApiConfig cfg, User user, String jobId) {
    JobParametersBuilder builder = new JobParametersBuilder();
    builder.addString("export.scope", cfg.getScope());
    builder.addString("export.format", cfg.getFormat());
    builder.addString("export.id", jobId, true);
    builder.addString("export.user", user.getUsername());
    builder.addString(
        "export.includeRevisionHistory", Boolean.toString(cfg.isIncludeRevisionHistory()));

    if (cfg.getId() != null) {
      builder.addLong("export.userOrGroupId", cfg.getId());
    }
    return builder.toJobParameters();
  }
}
