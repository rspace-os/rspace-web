package com.researchspace.service.aws.impl;

import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.model.netfiles.NfsFileSystemOption;
import com.researchspace.service.aws.S3Utilities;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class S3UtilitiesFactory {

  private static final Integer USE_CHUNKED_UPLOAD_MB_THRESHOLD_DEFAULT = 1000;

  @Value("${aws.s3.chunk.threshold.mb:1000}")
  private Integer chunkedUploadMbThreshold = USE_CHUNKED_UPLOAD_MB_THRESHOLD_DEFAULT;

  private static final Integer DEFAULT_CHUNK_SIZE_MB = 50;

  @Value("${aws.s3.chunk.size.mb:50}")
  private Integer chunkedUploadMbSize = DEFAULT_CHUNK_SIZE_MB;

  public S3Utilities createS3Utilities(String s3ExportRegion, String s3ExportBucketName) {
    S3UtilitiesImpl s3utils = new S3UtilitiesImpl();
    s3utils.setChunkedUploadMbThreshold(chunkedUploadMbThreshold);
    s3utils.setChunkedUploadMbSize(chunkedUploadMbSize);

    s3utils.initializeS3Client(s3ExportRegion, s3ExportBucketName);
    return s3utils;
  }

  public S3Utilities createS3Utilities(NfsFileSystem fileSystem) {
    String s3Region = fileSystem.getClientOption(NfsFileSystemOption.S3_REGION);
    String s3BucketName = fileSystem.getClientOption(NfsFileSystemOption.S3_BUCKET_NAME);
    return createS3Utilities(s3Region, s3BucketName);
  }
}
