package com.researchspace.api.v1.controller;

import com.researchspace.api.v1.throttling.APIFileUploadStats;
import com.researchspace.api.v1.throttling.APIFileUploadThrottler;
import com.researchspace.core.util.throttling.ThrottleInterval;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

public class APIFileUploadThrottlingInterceptor extends AbstractThrottleInterceptor {

  public static final int BYTES_PER_MB = 1048576;
  public static final String X_UPLOAD_LIMIT_REMAINING = "X-Upload-Limit-Remaining";
  public static final String X_UPLOAD_LIMIT_LIMIT = "X-Upload-Limit-Limit";

  APIFileUploadThrottlingInterceptor() {
    super();
  }

  private APIFileUploadThrottler fileUploadThrottler;

  void setFileUploadThrottler(APIFileUploadThrottler fileUploadThrottler) {
    this.fileUploadThrottler = fileUploadThrottler;
  }

  Logger log = LoggerFactory.getLogger(APIRequestThrottlingInterceptor.class);

  public APIFileUploadThrottlingInterceptor(@Autowired APIFileUploadThrottler fileUploadThrottler) {
    Validate.notNull(fileUploadThrottler, "Throttler cannot be null");
    this.fileUploadThrottler = fileUploadThrottler;
  }

  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
      throws IOException {
    String identifier = assertApiAccess(request);
    // we're only interested in POST method to files/ url
    if (!RequestMethod.POST.name().equals(request.getMethod())) {
      return true;
    }
    if (request instanceof MultipartHttpServletRequest) {
      MultipartHttpServletRequest multiReq = ((MultipartHttpServletRequest) request);
      Collection<List<MultipartFile>> files = multiReq.getMultiFileMap().values();
      long totalFileSize =
          files.stream()
              .mapToLong(
                  listOfMultipart ->
                      listOfMultipart.stream().mapToLong(multiPart -> multiPart.getSize()).sum())
              .sum();
      double totalFileSizeMb = ((double) totalFileSize / BYTES_PER_MB);
      // we update the headers here, before testing throttler, in case exception is thrown.
      // This still counts  as an API call even if it fails.
      boolean ok = true;
      try {
        ok = fileUploadThrottler.proceed(identifier, totalFileSizeMb);
      } finally {
        Optional<APIFileUploadStats> stats =
            fileUploadThrottler.getStats(identifier, ThrottleInterval.HOUR);
        setUsageHeaderStats(response, stats);
      }
      return ok;
    } else {
      return true;
    }
  }

  private void setUsageHeaderStats(
      HttpServletResponse response, Optional<APIFileUploadStats> optionalStats) {
    if (optionalStats.isPresent()) {
      response.addHeader(X_UPLOAD_LIMIT_LIMIT, optionalStats.get().getFileUploadLimit() + "");
      response.addHeader(
          X_UPLOAD_LIMIT_REMAINING, optionalStats.get().getRemainingCapacityInPeriod() + "");
    } else {
      log.warn(
          "Could not obtain API usage stats for throttle with interval {} - is this configured?",
          ThrottleInterval.QUARTER_MIN.name());
    }
  }
}
