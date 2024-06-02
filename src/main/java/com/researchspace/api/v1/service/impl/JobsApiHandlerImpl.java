package com.researchspace.api.v1.service.impl;

import com.researchspace.api.v1.controller.JobsApiHandler;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.User;
import com.researchspace.service.OperationFailedMessageGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

@Slf4j
public class JobsApiHandlerImpl implements JobsApiHandler {

  private @Autowired JobExplorer jobExplorer;
  private @Autowired OperationFailedMessageGenerator authMsgGen;
  private @Autowired ExportApiStateTracker apiState;
  private List<JobResultFactory<?>> jobResultFactories = new ArrayList<>();

  JobsApiHandlerImpl() {}

  public JobsApiHandlerImpl(List<JobResultFactory<?>> jobResultFactories) {
    super();
    this.jobResultFactories = jobResultFactories;
  }

  /* (non-Javadoc)
   * @see com.researchspace.api.v1.controller.JobsApiHandler#getJob(java.lang.Long, com.researchspace.model.User)
   */
  @Override
  public ApiJob getJob(Long jobId, User apiClient) {
    JobExecution exe = jobExplorer.getJobExecution(jobId);
    checkExecutionPerms(exe, apiClient);

    BatchStatus status = exe.getStatus();
    ApiJob job = new ApiJob(exe.getId(), status.toString());
    String extId = exe.getJobParameters().getString("export.id");

    // if job has completed, this will be null, as the state will have been cleared in ExportTasklet
    ProgressMonitor pm =
        apiState.getProgressMonitorById(extId).orElse(ProgressMonitor.NULL_MONITOR);
    if (pm.getTotalWorkUnits() > 0) {
      job.setPercentComplete(roundTo3sf(pm));
    }

    if (BatchStatus.COMPLETED.equals(status)) {
      job.setCompleted(true);
      job.setPercentComplete(100);
      Object result = createResult(job, exe); // also sets resourceLocation
      job.setResult(result); // may be null if no result could be made
    } else if (BatchStatus.FAILED.equals(status)) {
      ApiError error = createApiErrorObjectForInternalFailure(exe);
      job.setResult(error);
    }

    return job;
  }

  Double roundTo3sf(ProgressMonitor pm) {
    return Double.parseDouble(String.format("%.2f", pm.getPercentComplete()));
  }

  private ApiError createApiErrorObjectForInternalFailure(JobExecution exe) {
    List<String> errorMessages =
        exe.getStepExecutions().stream()
            .map(step -> summarise(step.getExitStatus().getExitDescription()))
            .collect(Collectors.toList());
    ApiError error =
        new ApiError(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ApiErrorCodes.GENERAL_ERROR.getCode(),
            "The job failed with an internal error",
            errorMessages);
    return error;
  }

  private String summarise(String exitDescription) {
    if (StringUtils.isBlank(exitDescription)) {
      return exitDescription;
    } else {
      // split on new in in case stack trace present.
      return exitDescription.split("\\n\\sat\\s")[0];
    }
  }

  private Object createResult(ApiJob job, JobExecution exe) {
    String jobType = exe.getExecutionContext().getString(ExportTasklet.JOB_TYPE_KEY);
    Optional<JobResultFactory<?>> factoryOpt = getJobResultFactoryForType(jobType);
    if (factoryOpt.isPresent()) {
      Object result = factoryOpt.get().createResult(exe, job);
      return result;
    } else {
      log.warn("There is no job factory to create a job result for type {}", jobType);
      return null;
    }
  }

  private Optional<JobResultFactory<?>> getJobResultFactoryForType(String jobType) {
    return jobResultFactories.stream().filter(fac -> fac.supportsJobType(jobType)).findFirst();
  }

  private void checkExecutionPerms(JobExecution exe, User apiClient) {
    if (exe == null) {
      throw new NotFoundException("No job execution with this Id");
    }
    String executorUName = getJobParam(exe);
    if (!apiClient.getUsername().equals(executorUName)) {
      throw new AuthorizationException(
          authMsgGen.getFailedMessage(apiClient, "Retrieving job by id"));
    }
  }

  private String getJobParam(JobExecution exe) {
    return exe.getJobParameters().getString("export.user");
  }

  void setExplorer(JobExplorer explorer) {
    this.jobExplorer = explorer;
  }

  void setAuthMsgGen(OperationFailedMessageGenerator authMsgGen) {
    this.authMsgGen = authMsgGen;
  }

  void setJobResultFactories(List<JobResultFactory<?>> jobResultFactories) {
    this.jobResultFactories = jobResultFactories;
  }

  void setApiState(ExportApiStateTracker apiState) {
    this.apiState = apiState;
  }
}
