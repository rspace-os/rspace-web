package com.researchspace.service.aws.impl;

import com.researchspace.service.archive.export.ExportFailureException;
import com.researchspace.service.aws.S3Utilities;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Object;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class S3UtilitiesImpl implements S3Utilities {

  // AWS max limit for a PutUpload
  private static final long AWS_PUT_FILE_LIMIT = 5 * FileUtils.ONE_GB;

  @Setter(AccessLevel.PACKAGE)
  private Integer chunkedUploadMbThreshold;

  @Setter(AccessLevel.PACKAGE)
  private Integer chunkedUploadMbSize;

  private String s3BucketName;

  @Getter private S3Client s3Client;

  /** Initializes s3 client used by this S3Utilities instance */
  protected void initializeS3Client(String s3Region, String s3BucketName) {
    Validate.notBlank(s3BucketName, "s3BucketName must be set for initialization");
    Validate.notBlank(s3Region, "s3Region must be set for initialization");
    this.s3BucketName = s3BucketName;

    try {
      s3Client = S3Client.builder().region(Region.of(s3Region)).build();
      HeadBucketRequest headBucketRequest =
          HeadBucketRequest.builder().bucket(s3BucketName).build();
      s3Client.headBucket(headBucketRequest);

    } catch (Exception e) {
      log.error("Error building S3 client with region {} and bucket {}", s3Region, s3BucketName, e);
    }
  }

  @Override
  public boolean isFileInS3(String folderPath, String fileName) {
    try {
      HeadObjectRequest headObjectRequest =
          HeadObjectRequest.builder().bucket(s3BucketName).key(folderPath + "/" + fileName).build();
      s3Client.headObject(headObjectRequest);
      return true;
    } catch (NoSuchKeyException e) {
      log.error("Could not find object {} in S3", folderPath + "/" + fileName, e);
      return false;
    } catch (Exception e) {
      log.error(
          "Error while making head object request for bucket {} and file {}",
          s3BucketName,
          folderPath + "/" + fileName);
      throw e;
    }
  }

  @Override
  public SdkHttpResponse uploadToS3(String folderPath, File file) {
    return getS3Uploader(folderPath, file).apply(file);
  }

  protected Function<File, SdkHttpResponse> getS3Uploader(String folderPath, File file) {
    if ((file.length() <= AWS_PUT_FILE_LIMIT)
        && (chunkedUploadMbThreshold * FileUtils.ONE_MB > file.length())) {
      return new S3PutUploader(s3Client, s3BucketName, folderPath);
    }
    return new S3MultipartChunkedUploader(s3Client, s3BucketName, folderPath, chunkedUploadMbSize);
  }

  @Override
  public void downloadFromS3(String filePath, File destinationFile) {
    try {
      GetObjectRequest getObjectRequest =
          GetObjectRequest.builder().bucket(s3BucketName).key(filePath).build();
      try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(getObjectRequest)) {
        Files.copy(response, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
      log.info("Successfully downloaded {} from S3 to {}", filePath, destinationFile.getPath());
    } catch (IOException e) {
      log.error("Failed to write downloaded file from S3 to {}", destinationFile.getPath(), e);
      throw new ExportFailureException(
          "Failed to write downloaded file from S3 to " + destinationFile.getPath(), e);
    } catch (Exception e) {
      log.error("Failed to download file {} from S3", filePath, e);
      throw new ExportFailureException("Failed to download file " + filePath + " from S3", e);
    }
  }

  @Override
  public List<S3FolderContentItem> listFolderContents(String folderPath) {

    String folderPrefixToQuery = getFolderPrefixToQuery(folderPath);
    try {
      ListObjectsV2Request listRequest =
          ListObjectsV2Request.builder()
              .bucket(s3BucketName)
              .prefix(folderPrefixToQuery)
              .delimiter("/")
              .build();

      ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
      List<S3FolderContentItem> items = new ArrayList<>();

      // Add subfolders (common prefixes)
      for (CommonPrefix commonPrefix : listResponse.commonPrefixes()) {
        String folderName = commonPrefix.prefix().substring(folderPrefixToQuery.length());
        if (folderName.endsWith("/")) {
          folderName = folderName.substring(0, folderName.length() - 1);
        }
        items.add(new S3FolderContentItem(folderName, true, null, null));
      }

      // Add files (objects)
      for (S3Object s3Object : listResponse.contents()) {
        String key = s3Object.key();
        if (!key.equals(folderPrefixToQuery)) { // Skip the folder itself
          String fileName = key.substring(folderPrefixToQuery.length());
          items.add(
              new S3FolderContentItem(fileName, false, s3Object.size(), s3Object.lastModified()));
        }
      }

      log.info("Successfully listed {} items in folder {}", items.size(), folderPath);
      return items;
    } catch (Exception e) {
      log.error("Failed to list folder contents for {}", folderPath, e);
      throw e;
    }
  }

  @NotNull
  private String getFolderPrefixToQuery(String folderPath) {
    if (!folderPath.isEmpty() && !folderPath.endsWith("/")) {
      return folderPath + "/";
    }
    return folderPath;
  }

  @Data
  public static class S3FolderContentItem {
    private final String name;
    private final boolean isFolder;
    private final Long sizeInBytes;
    private final Instant lastModified;
  }

  @Override
  public DeleteObjectResponse deleteFromS3(String folderPath, String fileName) {
    try {
      DeleteObjectRequest deleteObjectRequest =
          DeleteObjectRequest.builder()
              .bucket(s3BucketName)
              .key(folderPath + "/" + fileName)
              .build();

      return s3Client.deleteObject(deleteObjectRequest);
    } catch (Exception e) {
      log.error("Failed to delete object {} from S3", folderPath + "/" + fileName, e);
      throw e;
    }
  }
}
