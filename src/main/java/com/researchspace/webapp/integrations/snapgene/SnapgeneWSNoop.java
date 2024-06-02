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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import org.springframework.http.HttpStatus;

/** Noop implementation that returns ApiError for all methods with 503 (service unavailable) code */
public class SnapgeneWSNoop implements SnapgeneWSClient {

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
    return Either.left(serviceUnavailable());
  }

  private ApiError serviceUnavailable() {
    return new ApiError(
        HttpStatus.SERVICE_UNAVAILABLE,
        ApiErrorCodes.NO_HANDLER.getCode(),
        "Snapgene server is not configured",
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
