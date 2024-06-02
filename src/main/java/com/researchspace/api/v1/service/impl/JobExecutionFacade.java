package com.researchspace.api.v1.service.impl;

import lombok.AllArgsConstructor;
import org.springframework.batch.core.JobExecution;

/** Wrapper around SpringBatch {@link JobExecution} to decouple and simplify some calls */
@AllArgsConstructor
public class JobExecutionFacade {

  private JobExecution delegate;

  public JobExecutionFacade addStringToContext(String key, String value) {
    delegate.getExecutionContext().putString(key, value);
    return this;
  }

  public String getStringValueFromContext(String key) {
    return delegate.getExecutionContext().getString(key);
  }

  public String getStringValueFromJobParams(String key) {
    return delegate.getJobParameters().getString(key);
  }
}
