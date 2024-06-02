package com.researchspace.api.v1.service.impl;

import com.researchspace.api.v1.model.ApiExportJobResult;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.core.util.NumberUtils;
import com.researchspace.model.ArchivalCheckSum;
import com.researchspace.service.archive.ExportImport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@Slf4j
public class ApiExportJobResultFactory implements JobResultFactory<ApiExportJobResult> {

  @Value("${archive.folder.storagetime:48}")
  private String storageTimeProperty = "48";

  private @Autowired ExportImport exporter;

  @Override
  public ApiExportJobResult createResult(JobExecution exe, ApiJob job) {
    JobExecutionFacade facade = new JobExecutionFacade(exe);
    String type = facade.getStringValueFromContext(ExportTasklet.JOB_TYPE_KEY);
    validateInput(exe, type);
    String fileName = facade.getStringValueFromContext(ExportTasklet.EXPORT_RESULT_KEY);
    String csumId = facade.getStringValueFromContext(ExportTasklet.EXPORT_ID);

    log.info("Exported file is at {}", fileName);
    ApiExportJobResult exportResult = new ApiExportJobResult();
    setChecksum(csumId, exportResult);

    exportResult.setExpiryDate(calculateExpiryDate());
    job.setResourceLocation(fileName);
    return exportResult;
  }

  // this is generic and can be put in abstract class for future job result factories
  private void validateInput(JobExecution exe, String type) {
    if (!supportsJobType(type)) {
      throw new IllegalArgumentException(
          String.format(
              "This class supports job type '%s', not job type '%s'.",
              ExportTasklet.JOB_TYPE, type));
    }
    if (!BatchStatus.COMPLETED.equals(exe.getStatus())) {
      throw new IllegalStateException(
          String.format("Job is not completed, status is %s", exe.getStatus()));
    }
  }

  private void setChecksum(String csumId, ApiExportJobResult exportResult) {
    Optional<ArchivalCheckSum> optCsum =
        exporter.getCurrentArchiveMeta().stream()
            .filter(cd -> cd.getUid().equals(csumId))
            .findFirst();
    if (optCsum.isPresent()) {
      exportResult.setChecksum(optCsum.get().getCheckSum() + "");
      exportResult.setAlgorithm(optCsum.get().getAlgorithm());
      exportResult.setSize(optCsum.get().getZipSize());
    } else {
      log.warn("No archive metadata could be found for id {}", csumId);
    }
  }

  private Long calculateExpiryDate() {
    Instant now = Instant.now();
    int expiryTimeHours = NumberUtils.stringToInt(storageTimeProperty, 24);
    Instant expiry = now.plus(expiryTimeHours, ChronoUnit.HOURS);
    return expiry.toEpochMilli();
  }

  @Override
  public boolean supportsJobType(String jobType) {
    return ExportTasklet.JOB_TYPE.equals(jobType);
  }
}
