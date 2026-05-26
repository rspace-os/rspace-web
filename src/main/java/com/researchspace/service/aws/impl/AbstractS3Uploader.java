package com.researchspace.service.aws.impl;

import java.io.File;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;

abstract class AbstractS3Uploader {
  S3Client s3Client;
  String s3BucketName;
  String s3ArchivePath;

  AbstractS3Uploader(S3Client s3Client, String s3BucketName, String s3ArchivePath) {
    this.s3Client = s3Client;
    this.s3BucketName = s3BucketName;
    this.s3ArchivePath = s3ArchivePath;
  }

  String buildKeyFromFilePath(File file) {
    return StringUtils.isBlank(s3ArchivePath)
        ? file.getName()
        : s3ArchivePath + "/" + file.getName();
  }
}
