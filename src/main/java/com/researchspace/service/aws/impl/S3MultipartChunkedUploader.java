package com.researchspace.service.aws.impl;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jsoup.helper.Validate;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

@Slf4j
public class S3MultipartChunkedUploader extends AbstractS3Uploader
    implements Function<File, SdkHttpResponse> {
  private int chunkSizeMb;

  S3MultipartChunkedUploader(
      S3Client s3Client, String s3BucketName, String s3ArchivePath, int chunkSizeMb) {
    super(s3Client, s3BucketName, s3ArchivePath);
    Validate.isTrue(
        chunkSizeMb * FileUtils.ONE_MB >= AWS_MIN_CHUNK_SIZE_BYTES,
        "Chunking threshold must be >= 5Mb");
    this.chunkSizeMb = chunkSizeMb;
  }

  private static final int S3_UPLOAD_FAILED_CODE = 500;

  // aws standard min chunksize
  private static final long AWS_MIN_CHUNK_SIZE_BYTES = FileUtils.ONE_MB * 5;

  // define a Retry if a part upload was not successful
  RetryConfig retryCfg =
      RetryConfig.custom()
          .maxAttempts(3)
          .retryOnResult(resp2 -> !((UploadPartResponse) resp2).sdkHttpResponse().isSuccessful())
          .build();

  // for testing
  void setChunkSizeMb(int chunkSizeMb) {
    this.chunkSizeMb = chunkSizeMb;
  }

  @Override
  public SdkHttpResponse apply(File fToUpload) {
    if (fToUpload.length() < AWS_MIN_CHUNK_SIZE_BYTES) {
      throw new IllegalArgumentException(
          "Files must be at least " + AWS_MIN_CHUNK_SIZE_BYTES + "to use chunked upload");
    }
    String key = buildKeyFromFilePath(fToUpload);

    // First create a multipart upload and get the upload id
    CreateMultipartUploadRequest createMultipartUploadRequest =
        CreateMultipartUploadRequest.builder().bucket(s3BucketName).key(key).build();

    CreateMultipartUploadResponse response =
        s3Client.createMultipartUpload(createMultipartUploadRequest);
    String uploadId = response.uploadId();
    log.info("Created upload with ID {}", uploadId);

    // prepare an abort request just in case.
    AbortMultipartUploadRequest abortRequest =
        AbortMultipartUploadRequest.builder()
            .bucket(s3BucketName)
            .key(key)
            .uploadId(uploadId)
            .build();
    Retry rt = Retry.of("s3FileUpload", retryCfg);
    rt.getEventPublisher()
        .onRetry(ev -> log.warn("Upload failed: {}, retrying", ev.getEventType()));

    // this will get called after retry limit is reached
    rt.getEventPublisher().onError(ev -> abort(abortRequest));

    FileChunker chunker = new FileChunker(fToUpload, Long.valueOf(chunkSizeMb * FileUtils.ONE_MB));
    List<CompletedPart> completed = new ArrayList<>();

    log.info("There are {} parts to upload", chunker.getNumParts());
    for (FilePart bb : chunker.getParts()) {
      log.info("uploading part number {}", bb.getPartNo());
      // Upload all the different parts of the object
      UploadPartRequest uploadPartRequest1 =
          UploadPartRequest.builder()
              .bucket(s3BucketName)
              .key(key)
              .uploadId(uploadId)
              .partNumber(bb.getPartNo())
              .build();

      Callable<UploadPartResponse> up =
          () ->
              s3Client.uploadPart(
                  uploadPartRequest1,
                  RequestBody.fromByteBuffer(
                      bb.getByteBufferSupplier().get().orElseThrow(() -> new IOException())));

      up = Retry.decorateCallable(rt, up);
      UploadPartResponse uploadPartResponse = null;
      try {
        uploadPartResponse = up.call();
        String etag = uploadPartResponse.eTag();
        CompletedPart completedPart =
            CompletedPart.builder().partNumber(bb.getPartNo()).eTag(etag).build();
        log.info("Completed part is {}", completedPart.partNumber());
        completed.add(completedPart);
        bb.setByteBufferSupplier(null); // reset just in case of possibility of memory leaks
      } catch (Exception e) {
        // this gets called on any exception thrown out by retry mechanism, e.g. after 3 failures.
        log.error("Error uploading file chunk of file {}", fToUpload.getName());
        return s3UploadFailed();
      }
    }

    // Finally call completeMultipartUpload operation to tell S3 to merge all
    // uploaded
    // parts and finish the multipart operation.
    CompletedMultipartUpload completedMultipartUpload =
        CompletedMultipartUpload.builder().parts(completed).build();

    CompleteMultipartUploadRequest completeMultipartUploadRequest =
        CompleteMultipartUploadRequest.builder()
            .bucket(s3BucketName)
            .key(key)
            .uploadId(uploadId)
            .multipartUpload(completedMultipartUpload)
            .build();

    try {
      // throws an S3 exception if something wrong.
      CompleteMultipartUploadResponse resp =
          s3Client.completeMultipartUpload(completeMultipartUploadRequest);
      return resp.sdkHttpResponse();

    } catch (Exception e) {
      log.error("Aborting:  {}", e.getMessage());

      // whether or not abort failed, the upload still failed. In this catch block, there is no
      // CompleteMultipartUploadResponse
      // e generated. We

      return abort(abortRequest);
    }
  }

  SdkHttpResponse abort(AbortMultipartUploadRequest abortReq) {
    AbortMultipartUploadResponse abortResponse = s3Client.abortMultipartUpload(abortReq);
    if (abortResponse.sdkHttpResponse().isSuccessful()) {
      log.info("Abort {} successful", abortReq.key());
      return s3UploadFailed();
    } else {
      log.warn("Aborting the upload failed");
      return abortResponse.sdkHttpResponse();
    }
  }

  private SdkHttpResponse s3UploadFailed() {
    return SdkHttpResponse.builder().statusCode(S3_UPLOAD_FAILED_CODE).build();
  }
}
