package com.researchspace.webapp.integrations.snapgene;

import com.researchspace.apiutils.ApiError;
import com.researchspace.apiutils.ApiErrorCodes;
import com.researchspace.snapgene.wclient.SnapgeneWSClient;
import com.researchspace.zmq.snapgene.requests.ExportDnaFileConfig;
import com.researchspace.zmq.snapgene.requests.GeneratePngMapConfig;
import com.researchspace.zmq.snapgene.requests.GenerateSVGMapConfig;
import com.researchspace.zmq.snapgene.requests.ReportEnzymesConfig;
import com.researchspace.zmq.snapgene.requests.ReportORFsConfig;
import com.researchspace.zmq.snapgene.responses.SnapgeneResponse;
import io.vavr.control.Either;
import io.vavr.control.Try;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;

/** Dummy implementation returning hard-code paths */
@Slf4j
public class SnapgeneDummy implements SnapgeneWSClient {

  @Value("${sample.snapgene.png}")
  private String pngPath;

  @Value("${sample.snapgene.svg}")
  private String svgPath;

  @Value("${sample.snapgene.dna}")
  private String dnaFile;

  @Value("${sample.snapgene.genbank}")
  private String genbankFile;

  @Override
  public Either<ApiError, SnapgeneResponse> convertToSvgFile(
      File file, GenerateSVGMapConfig config) {
    return Either.left(serviceUnavailable());
  }

  @Override
  public Either<ApiError, SnapgeneResponse> convertToPngFile(
      File file, GeneratePngMapConfig config) {
    return Either.left(serviceUnavailable());
  }

  @Override
  public Either<ApiError, String> enzymes(File file, ReportEnzymesConfig config) {
    return Either.left(serviceUnavailable());
  }

  @Override
  public Either<ApiError, SnapgeneResponse> exportDnaFile(File file, ExportDnaFileConfig config) {
    return Either.left(serviceUnavailable());
  }

  @Override
  public Either<ApiError, SnapgeneResponse> importDnaFile(File file) {
    return Either.left(serviceUnavailable());
  }

  @Override
  public Either<ApiError, byte[]> downloadFile(String outputFileName) {
    return Either.left(serviceUnavailable());
  }

  @Override
  public Either<ApiError, byte[]> uploadAndDownloadPng(
      File fileToConvert, GeneratePngMapConfig pngConfig)
      throws FileNotFoundException, IOException {
    File toStreamFile = new File(pngPath);
    if (!toStreamFile.exists()) {
      throw new IllegalArgumentException("File " + pngPath + " doesn't exist");
    }
    return Try.of(() -> FileUtils.readFileToByteArray(toStreamFile))
        .toEither()
        .peekLeft(t -> log.error(t.getMessage()))
        .mapLeft(
            e ->
                new ApiError(
                    HttpStatus.BAD_REQUEST,
                    500_01,
                    "Problem reading dummy png file response",
                    Collections.emptyList()));
  }

  private ApiError serviceUnavailable() {
    return new ApiError(
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiErrorCodes.NO_HANDLER.getCode(),
        "Dummy snapgene server does not support this operation. "
            + "To invoke real snapgene set property 'snapgene.web.url'",
        Collections.emptyList());
  }

  @Override
  public Either<ApiError, String> status() {
    return Either.left(serviceUnavailable());
  }

  @Override
  public Either<ApiError, String> orfs(File file, ReportORFsConfig config) {
    return Either.left(serviceUnavailable());
  }
}
