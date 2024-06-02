package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.JobsApi;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.archive.ArchiveUtils;
import com.researchspace.model.User;
import com.researchspace.service.aws.S3Utilities;
import java.net.URL;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;

@ApiController
@Slf4j
public class JobsApiController extends BaseApiController implements JobsApi {

  private @Autowired JobsApiHandler handler;

  @Value("${aws.s3.hasS3Access}")
  boolean hasS3Access; // package-scoped for testing

  private @Autowired S3Utilities s3Utils;

  @Override
  public ApiJob get(
      @PathVariable(value = "id") Long id,
      HttpServletResponse response,
      @RequestAttribute(name = "user") User apiClient) {
    ApiJob job = handler.getJob(id, apiClient);
    if (job.isCompleted()) {
      setDownloadLink(job);
      log.info("Exported file is at {}", job.getResourceLocation());
    }
    return job;
  }

  void setDownloadLink(ApiJob job) {
    if (!hasS3Access)
      job.addEnclosureLink(
          ArchiveUtils.getApiExportDownloadLink(
              properties.getServerUrl(), job.getResourceLocation()));
    else {
      URL s3Link = s3Utils.getPresignedUrlForArchiveDownload(job.getResourceLocation());
      if (s3Link != null) job.addEnclosureLink(s3Link.toString());
      else {
        throw new IllegalStateException(
            "Couldn't find export on S3 for " + job.getResourceLocation());
      }
    }
  }
}
