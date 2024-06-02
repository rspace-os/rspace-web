package com.researchspace.api.v1.service.impl;

import com.researchspace.api.v1.model.ApiJob;
import org.springframework.batch.core.JobExecution;

/** Creates instances of objects to serve as the 'result' property of an {@link ApiJob} */
public interface JobResultFactory<T> {

  /**
   * Creates a result object appropriate for the job type
   *
   * @param exe
   * @param job
   * @return
   */
  T createResult(JobExecution exe, ApiJob job);

  /**
   * Boolean test as to whether the factory can create T instances from the given job type. JobTypes
   * are currently defined in {@link ExportTasklet} but should be somewhere more generic as new jobs
   * are added,
   *
   * @param jobType
   * @return
   */
  boolean supportsJobType(String jobType);
}
