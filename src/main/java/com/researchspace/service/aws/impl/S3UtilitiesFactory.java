package com.researchspace.service.aws.impl;

import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemOption;
import com.researchspace.service.aws.S3Utilities;
import org.springframework.beans.factory.annotation.Value;

public class S3UtilitiesFactory {

  private static final Integer USE_CHUNKED_UPLOAD_MB_THRESHOLD_DEFAULT = 1000;

  @Value("${aws.s3.chunk.threshold.mb:1000}")
  private Integer chunkedUploadMbThreshold = USE_CHUNKED_UPLOAD_MB_THRESHOLD_DEFAULT;

  private static final Integer DEFAULT_CHUNK_SIZE_MB = 50;

  @Value("${aws.s3.chunk.size.mb:50}")
  private Integer chunkedUploadMbSize = DEFAULT_CHUNK_SIZE_MB;

  @Value("${netfilestores.s3.global.credentials.accessKey}")
  private String nfsS3accessKey;

  @Value("${netfilestores.s3.global.credentials.secretKey}")
  private String nfsS3secretKey;

  public S3Utilities createS3UtilitiesForAwsArchiveExport(
      String s3ExportRegion, String s3ExportBucketName) {

    S3UtilitiesImpl s3utils = new S3UtilitiesImpl();
    s3utils.setChunkedUploadMbThreshold(chunkedUploadMbThreshold);
    s3utils.setChunkedUploadMbSize(chunkedUploadMbSize);
    s3utils.initializeS3ClientWithAwsDefaults(s3ExportRegion, s3ExportBucketName);
    return s3utils;
  }

  public S3Utilities createS3UtilitiesForNfsConnector(NfsFileSystem fileSystem) {
    String s3Region = fileSystem.getClientOption(NfsFileSystemOption.S3_REGION);
    String s3BucketName = fileSystem.getClientOption(NfsFileSystemOption.S3_BUCKET_NAME);
    boolean s3PathStyleAccessEnabled =
        "true"
            .equalsIgnoreCase(
                fileSystem.getClientOption(NfsFileSystemOption.S3_PATH_STYLE_ACCESS_ENABLED));

    S3UtilitiesImpl s3utils = new S3UtilitiesImpl();
    s3utils.setChunkedUploadMbThreshold(chunkedUploadMbThreshold);
    s3utils.setChunkedUploadMbSize(chunkedUploadMbSize);
    s3utils.initializeS3Client(
        fileSystem.getUrl(),
        s3Region,
        s3BucketName,
        s3PathStyleAccessEnabled,
        nfsS3accessKey,
        nfsS3secretKey);
    return s3utils;
  }

  /** for testing */
  protected void updateNfsS3Credentials(String nfsS3accessKey, String nfsS3secretKey) {
    this.nfsS3accessKey = nfsS3accessKey;
    this.nfsS3secretKey = nfsS3secretKey;
  }
}
