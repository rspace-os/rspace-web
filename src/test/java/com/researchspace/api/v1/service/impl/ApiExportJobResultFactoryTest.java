package com.researchspace.api.v1.service.impl;

import static com.researchspace.core.util.TransformerUtils.toList;
import static org.junit.jupiter.api.Assertions.*;

import com.researchspace.api.v1.controller.ExportApiController.ExportApiConfig;
import com.researchspace.api.v1.model.ApiExportJobResult;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.archive.ExportImport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.test.MetaDataInstanceFactory;

@ExtendWith(MockitoExtension.class)
class ApiExportJobResultFactoryTest {

  User user = null;

  @Mock ExportImport exporter;
  @InjectMocks ApiExportJobResultFactory fac;
  JobParameters params;
  JobExecution exe;
  Long jobId = 2L;

  @BeforeEach
  void setUp() {
    user = TestFactory.createAnyUser("any");
    params =
        ExportJobParamFactory.createJobParams(new ExportApiConfig("html", "user"), user, "abcde");
    exe = MetaDataInstanceFactory.createJobExecution("exportJob", 1L, jobId, params);
  }

  @Test
  void createResultFailsIfJobNotCompleted() {
    ArchivalCheckSum expectedArchiveMetadata = TestFactory.createAnArchivalChecksum();
    setUpJobExecutionContext(expectedArchiveMetadata);
    ApiJob job = new ApiJob(jobId, BatchStatus.STARTED.toString());
    assertThrows(IllegalStateException.class, () -> fac.createResult(exe, job));
  }

  @Test
  void createResultFailsIfIIncorrectJobType() {
    ArchivalCheckSum expectedArchiveMetadata = TestFactory.createAnArchivalChecksum();
    setUpJobExecutionContext(expectedArchiveMetadata)
        .addStringToContext(ExportTasklet.JOB_TYPE_KEY, "UNKNOWN");
    ApiJob job = new ApiJob(jobId, BatchStatus.STARTED.toString());
    assertThrows(IllegalArgumentException.class, () -> fac.createResult(exe, job));
  }

  @Test
  void createResultOK() {
    ArchivalCheckSum expectedArchiveMetadata = TestFactory.createAnArchivalChecksum();
    setUpJobExecutionContext(expectedArchiveMetadata);
    exe.setStatus(BatchStatus.COMPLETED);
    ApiJob job = new ApiJob(jobId, BatchStatus.COMPLETED.toString());
    Mockito.when(exporter.getCurrentArchiveMeta()).thenReturn(toList(expectedArchiveMetadata));
    ApiExportJobResult result = fac.createResult(exe, job);
    assertNotNull(result);
    assertNotNull(result.getExpiryDate());
    assertEquals(expectedArchiveMetadata.getCheckSum() + "", result.getChecksum());
    assertEquals(expectedArchiveMetadata.getZipSize(), result.getSize().longValue());
  }

  private JobExecutionFacade setUpJobExecutionContext(ArchivalCheckSum expected) {
    JobExecutionFacade jobExeFacade = new JobExecutionFacade(exe);
    jobExeFacade.addStringToContext(ExportTasklet.JOB_TYPE_KEY, ExportTasklet.JOB_TYPE);
    jobExeFacade.addStringToContext(ExportTasklet.EXPORT_ID, expected.getUid() + "");
    jobExeFacade.addStringToContext(ExportTasklet.EXPORT_RESULT_KEY, "export.zip");
    return jobExeFacade;
  }

  @Test
  void testSupportsJobType() {
    assertTrue(fac.supportsJobType(ExportTasklet.JOB_TYPE));
  }
}
