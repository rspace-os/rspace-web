package com.researchspace.api.v1.service.impl;

import static com.researchspace.api.v1.service.impl.ExportTasklet.EXPORT_RESULT_KEY;
import static com.researchspace.api.v1.service.impl.ExportTasklet.JOB_TYPE;
import static com.researchspace.api.v1.service.impl.ExportTasklet.JOB_TYPE_KEY;
import static com.researchspace.testutils.RSpaceTestUtils.getResource;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.junit.jupiter.api.Assertions.*;

import com.researchspace.api.v1.controller.ExportApiController.ExportApiConfig;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.apiutils.ApiError;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.core.util.progress.ProgressMonitorImpl;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.OperationFailedMessageGenerator;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.NotFoundException;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.test.MetaDataInstanceFactory;

@ExtendWith(MockitoExtension.class)
public class JobsApiSpringBatchHandlerTest {

  private @Mock JobExplorer explorer;
  private @Mock OperationFailedMessageGenerator authMsgGen;
  private @Mock JobResultFactory<Object> fac;

  JobsApiHandlerImpl handler;
  User user;
  JobParameters params;
  List<JobResultFactory<?>> facs = new ArrayList<>();

  @BeforeEach
  void setUp() {
    user = TestFactory.createAnyUser("any");
    params =
        ExportJobParamFactory.createJobParams(new ExportApiConfig("html", "user"), user, "abcde");
    handler = new JobsApiHandlerImpl();
    handler.setAuthMsgGen(authMsgGen);
    handler.setExplorer(explorer);
    handler.setApiState(new ExportApiStateTracker());
    facs.add(fac);
    handler.setJobResultFactories(facs);
  }

  @Test
  void testProgressReportingFormatting() {
    ProgressMonitor pm = new ProgressMonitorImpl(7, "test");
    pm.worked(1);
    assertEquals("14.29", handler.roundTo3sf(pm).toString());
    pm.worked(5);
    assertEquals("85.71", handler.roundTo3sf(pm).toString());
    pm.worked(1);
    assertEquals("100.0", handler.roundTo3sf(pm).toString());
  }

  @Test
  void testGetStartingJobOK() {
    Long jobId = 2L;
    JobExecution exe = MetaDataInstanceFactory.createJobExecution("exportJob", 1L, jobId, params);
    Mockito.when(explorer.getJobExecution(jobId)).thenReturn(exe);
    ApiJob job = handler.getJob(jobId, user);
    assertNotNull(job);
    assertFalse(job.isCompleted());
  }

  @Test
  void testGetFailedJobContainsApiErrorResult() throws IOException {
    Long jobId = 2L;
    JobExecution exe = MetaDataInstanceFactory.createJobExecution("exportJob", 1L, jobId, params);
    exe.setStatus(BatchStatus.FAILED);
    String exceptionMsg = readFileToString(getResource("BatchException.txt"), "UTF-8");
    exe.createStepExecution("step1")
        .setExitStatus(ExitStatus.FAILED.addExitDescription(exceptionMsg));
    Mockito.when(explorer.getJobExecution(jobId)).thenReturn(exe);
    ApiJob job = handler.getJob(jobId, user);
    assertNotNull(job);
    assertFalse(job.isCompleted());
    assertTrue(job.getResult() != null);
    assertTrue(job.getResult() instanceof ApiError);
    assertTrue(((ApiError) job.getResult()).getErrors().get(0).contains("ExportFailureException"));
  }

  @Test
  void jobCompletesOK() {
    Long jobId = 2L;

    JobExecution exe = MetaDataInstanceFactory.createJobExecution("exportJob", 1L, jobId, params);
    JobExecutionFacade facade = new JobExecutionFacade(exe);
    exe.setStatus(BatchStatus.COMPLETED);
    final String expectedResourceLocation = "resource.txt";
    facade.addStringToContext(EXPORT_RESULT_KEY, expectedResourceLocation);
    facade.addStringToContext(JOB_TYPE_KEY, JOB_TYPE);
    Mockito.when(explorer.getJobExecution(jobId)).thenReturn(exe);
    Mockito.when(fac.supportsJobType(ExportTasklet.JOB_TYPE)).thenReturn(true);
    Mockito.when(fac.createResult(Mockito.eq(exe), Mockito.any(ApiJob.class)))
        .thenReturn(new Object());
    ApiJob job = handler.getJob(jobId, user);
    assertNotNull(job);
    assertTrue(job.isCompleted());
    assertEquals(100, job.getPercentComplete(), 0.00001);
    assertNotNull(job.getResult());
  }

  @Test
  void testGetJobNotExists() throws Exception {
    Long jobId = 2L;
    Mockito.when(explorer.getJobExecution(2L)).thenReturn(null);
    assertThrows(NotFoundException.class, () -> handler.getJob(jobId, user));
  }

  @Test
  void testGetJobNotOwnedByClient() throws Exception {
    User unauthorizedUser = TestFactory.createAnyUser("other");
    Long jobId = 2L;
    JobExecution exe = MetaDataInstanceFactory.createJobExecution("exportJob", 1L, jobId, params);
    Mockito.when(explorer.getJobExecution(jobId)).thenReturn(exe);
    assertThrows(AuthorizationException.class, () -> handler.getJob(jobId, unauthorizedUser));
  }
}
