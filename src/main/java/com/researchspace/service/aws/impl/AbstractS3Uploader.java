package com.researchspace.service.aws.impl;

import java.io.File;
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
    return s3ArchivePath + "/" + file.getName();
  }
}
