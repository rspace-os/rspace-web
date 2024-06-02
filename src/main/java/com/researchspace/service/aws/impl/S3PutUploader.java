package com.researchspace.service.aws.impl;

import com.researchspace.service.archive.export.ExportFailureException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Function;
import javax.ws.rs.core.EntityTag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.DigestUtils;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

@Slf4j
public class S3PutUploader extends AbstractS3Uploader implements Function<File, SdkHttpResponse> {

  S3PutUploader(S3Client s3Client, String s3BucketName, String s3ArchivePath) {
    super(s3Client, s3BucketName, s3ArchivePath);
  }

  @Override
  public SdkHttpResponse apply(File file) {
    try {
      PutObjectRequest putObjectRequest =
          PutObjectRequest.builder().bucket(s3BucketName).key(buildKeyFromFilePath(file)).build();

      PutObjectResponse putObjectResponse =
          s3Client.putObject(putObjectRequest, file.getAbsoluteFile().toPath());
      // Calculate server side checksum and compare with S3 checksum/etag
      InputStream is = new FileInputStream(file);
      String md5HashServerSide = DigestUtils.md5DigestAsHex(is);
      String md5HashS3 = EntityTag.valueOf(putObjectResponse.eTag()).getValue();
      if (!md5HashServerSide.equals(md5HashS3)) {
        log.error(
            "Hashes do not match file uploaded to S3. Server Side: {} S3: {}",
            md5HashServerSide,
            md5HashS3);
        throw new ExportFailureException("Checksum Mismatch for S3 Export: " + file.getName());
      }

      return putObjectResponse.sdkHttpResponse();

    } catch (IOException e) {
      log.error("Encountered IO Error for file {} during export to S3", file.getName(), e);
      return null;
    } catch (Exception e) {
      log.error("Failed to export file to S3: {}", file.getName(), e);
      throw e;
    }
  }
}
