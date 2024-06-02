package com.researchspace.service.aws.impl;

import com.researchspace.service.aws.S3Utilities;
import java.io.File;
import java.net.URL;
import java.time.Duration;
import java.util.function.Function;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
public class S3UtilitiesImpl implements S3Utilities {

  private static final Integer USE_CHUNKED_UPLOAD_MB_THRESHOLD_DEFAULT = 1000;

  private static final Integer DEFAULT_CHUNK_SIZE_MB = 50;

  // AWS max limit for a PutUpload
  private static final long AWS_PUT_FILE_LIMIT = 5 * FileUtils.ONE_GB;

  @Value("${aws.s3.bucketName}")
  private String s3BucketName;

  @Value(("${archive.folder.storagetime}"))
  private int archiveFolderStorageTime;

  @Value("${aws.s3.archivePath}")
  private String s3ArchivePath;

  @Value("${aws.s3.region}")
  private String region;

  // must be >5Mb
  @Value("${aws.s3.chunk.threshold.mb:1000}")
  private Integer chunkedUploadMbThreshold = USE_CHUNKED_UPLOAD_MB_THRESHOLD_DEFAULT;

  // must be >5Mb
  @Value("${aws.s3.chunk.size.mb:50}")
  private Integer chunkedUploadMbSize = DEFAULT_CHUNK_SIZE_MB;

  /* for testing */
  void setChunkedUploadThreshold(Integer chunkedUploadMbThreshold) {
    this.chunkedUploadMbThreshold = chunkedUploadMbThreshold;
  }

  private S3Client s3Client;

  private S3Presigner s3Presigner;

  /**
   * Post-construct method which builds s3 client objects and also checks whether connection to S3
   * can be made by making a head bucket request to determine if S3 parameters are configured
   * correctly on the RSpace side.
   */
  @PostConstruct
  public void init() {
    try {
      s3Client = S3Client.builder().region(Region.of(region)).build();

      s3Presigner = S3Presigner.builder().region(Region.of(region)).build();
      // Check s3Client is configured correctly by checking if bucket exists
      HeadBucketRequest headBucketRequest =
          HeadBucketRequest.builder().bucket(s3BucketName).build();
      s3Client.headBucket(headBucketRequest);

    } catch (Exception e) {
      log.error("Error building S3 client with region {} and bucket {}", region, s3BucketName, e);
    }
  }

  @Override
  public boolean isArchiveInS3(String fileName) {
    try {
      HeadObjectRequest headObjectRequest =
          HeadObjectRequest.builder()
              .bucket(s3BucketName)
              .key(s3ArchivePath + "/" + fileName)
              .build();
      s3Client.headObject(headObjectRequest);
      return true;
    } catch (NoSuchKeyException e) {
      log.error("Could not find object {} in S3", s3ArchivePath + "/" + fileName, e);
      return false;
    } catch (Exception e) {
      log.error(
          "Error while making head object request for bucket {} and file {}",
          s3BucketName,
          s3ArchivePath + "/" + fileName);
      throw e;
    }
  }

  @Override
  public URL getPresignedUrlForArchiveDownload(String fileName) {
    try {
      if (isArchiveInS3(fileName)) {
        GetObjectRequest getObjectRequest =
            GetObjectRequest.builder()
                .bucket(s3BucketName)
                .key(s3ArchivePath + "/" + fileName)
                .build();

        GetObjectPresignRequest getObjectPresignRequest =
            GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofHours(archiveFolderStorageTime))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(getObjectPresignRequest).url();
      } else {
        return null;
      }

    } catch (Exception e) {
      log.error(
          "Failed to generate pre-signed url for S3 Object: {}", s3ArchivePath + "/" + fileName, e);
      throw e;
    }
  }

  @Override
  public DeleteObjectResponse deleteArchiveFromS3(String fileName) {
    try {
      DeleteObjectRequest deleteObjectRequest =
          DeleteObjectRequest.builder()
              .bucket(s3BucketName)
              .key(s3ArchivePath + "/" + fileName)
              .build();

      return s3Client.deleteObject(deleteObjectRequest);
    } catch (Exception e) {
      log.error("Failed to delete object {} from S3", s3ArchivePath + "/" + fileName, e);
      throw e;
    }
  }

  @Override
  public Function<File, SdkHttpResponse> getS3Uploader(File file) {
    if (file.length() > AWS_PUT_FILE_LIMIT) {
      return new S3MultipartChunkedUploader(
          s3Client, s3BucketName, s3ArchivePath, chunkedUploadMbSize);
    }
    if (chunkedUploadMbThreshold * FileUtils.ONE_MB > file.length()) {
      return new S3PutUploader(s3Client, s3BucketName, s3ArchivePath);
    } else {
      return new S3MultipartChunkedUploader(
          s3Client, s3BucketName, s3ArchivePath, chunkedUploadMbSize);
    }
  }
}
