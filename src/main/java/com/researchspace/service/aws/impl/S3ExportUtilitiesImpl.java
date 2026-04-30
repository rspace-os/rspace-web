package com.researchspace.service.aws.impl;

import com.researchspace.service.aws.S3ExportUtilities;
import com.researchspace.service.aws.S3Utilities;
import java.io.File;
import java.net.URL;
import java.time.Duration;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
public class S3ExportUtilitiesImpl implements S3ExportUtilities {

  @Value("${aws.s3.region}")
  private String s3ExportRegion;

  @Value("${aws.s3.bucketName}")
  private String s3ExportBucketName;

  @Value(("${archive.folder.storagetime}"))
  private int archiveFolderStorageTime;

  @Value("${aws.s3.archivePath}")
  private String s3ArchivePath;

  @Autowired private S3UtilitiesFactory s3UtilitiesFactory;

  private S3Utilities s3Utilities;

  private S3Presigner s3Presigner;

  /**
   * Post-construct method which builds s3 client objects and also checks whether connection to S3
   * can be made by making a head bucket request to determine if S3 parameters are configured
   * correctly on the RSpace side.
   */
  @PostConstruct
  public void init() {
    try {
      s3Utilities =
          s3UtilitiesFactory.createS3UtilitiesForAwsArchiveExport(
              s3ExportRegion, s3ExportBucketName);
      s3Presigner = S3Presigner.builder().region(Region.of(s3ExportRegion)).build();
    } catch (Exception e) {
      log.error(
          "Error building S3 client with region {} and bucket {}",
          s3ExportRegion,
          s3ExportBucketName,
          e);
    }
  }

  @Override
  public boolean isArchiveInS3(String fileName) {
    if (s3Utilities == null) {
      throw new IllegalStateException("S3Utilities not initialized");
    }
    return s3Utilities.isFileInS3(s3ArchivePath, fileName);
  }

  @Override
  public SdkHttpResponse uploadArchiveToS3(File file) {
    if (s3Utilities == null) {
      throw new IllegalStateException("S3Utilities not initialized");
    }
    return s3Utilities.uploadToS3(s3ArchivePath, file);
  }

  @Override
  public URL getPresignedUrlForArchiveDownload(String fileName) {
    if (s3Utilities == null) {
      throw new IllegalStateException("S3Utilities not initialized");
    }
    try {
      if (isArchiveInS3(fileName)) {
        GetObjectRequest getObjectRequest =
            GetObjectRequest.builder()
                .bucket(s3ExportBucketName)
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
    if (s3Utilities == null) {
      throw new IllegalStateException("S3Utilities not initialized");
    }
    return s3Utilities.deleteFromS3(s3ArchivePath, fileName);
  }
}
