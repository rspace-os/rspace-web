package com.researchspace.service.aws.impl;

import com.researchspace.service.archive.export.ExportFailureException;
import com.researchspace.service.aws.S3Utilities;
import java.io.File;
import java.io.IOException;
import java.net.URI;
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
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
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

  protected void initializeS3ClientWithAwsDefaults(String s3Region, String s3BucketName) {
    initializeS3Client(null, s3Region, s3BucketName, false, null, null);
  }

  /** Initializes s3 client used by this S3Utilities instance */
  protected void initializeS3Client(
      String s3url,
      String s3Region,
      String s3BucketName,
      boolean pathStyleAccessEnabled,
      String accessKeyId,
      String secretAccessKey) {

    Validate.notBlank(s3BucketName, "s3BucketName must be set for initialization");
    Validate.notBlank(s3Region, "s3Region must be set for initialization");
    this.s3BucketName = s3BucketName;

    try {
      S3ClientBuilder s3ClientBuilder = S3Client.builder().region(Region.of(s3Region));
      if (StringUtils.isNotBlank(s3url) && !s3url.startsWith("aws::")) {
        s3ClientBuilder.endpointOverride(URI.create(s3url));
      }
      if (StringUtils.isNotBlank(accessKeyId) && StringUtils.isNotBlank(secretAccessKey)) {
        s3ClientBuilder.credentialsProvider(
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
      }
      if (pathStyleAccessEnabled) {
        s3ClientBuilder.serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(true).build());
      }

      s3Client = s3ClientBuilder.build();
      HeadBucketRequest headBucketRequest =
          HeadBucketRequest.builder().bucket(s3BucketName).build();
      s3Client.headBucket(headBucketRequest);

    } catch (Exception e) {
      s3Client = null;
      log.error("Error building S3 client with region {} and bucket {}", s3Region, s3BucketName, e);
      throw e;
    }
  }

  @Override
  public boolean isFileInS3(String folderPath, String fileName) {
    String fullPath = StringUtils.isBlank(folderPath) ? fileName : folderPath + "/" + fileName;
    try {
      HeadObjectRequest headObjectRequest =
          HeadObjectRequest.builder().bucket(s3BucketName).key(fullPath).build();
      s3Client.headObject(headObjectRequest);
      return true;
    } catch (S3Exception e) {
      if (e.statusCode() == 404) {
        log.warn("Could not find object {} in S3", fullPath);
        return false;
      }
      log.error(
          "Error while making head object request for bucket {} and file {}",
          s3BucketName,
          fullPath);
      throw e;
    } catch (Exception e) {
      log.error(
          "Error while making head object request for bucket {} and file {}",
          s3BucketName,
          fullPath);
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

      List<S3FolderContentItem> items = new ArrayList<>();
      ListObjectsV2Response listResponse;
      do {
        listResponse = s3Client.listObjectsV2(listRequest);

        // Add subfolders (common prefixes)
        for (CommonPrefix commonPrefix : listResponse.commonPrefixes()) {
          String folderName =
              StringUtils.removeEnd(
                  commonPrefix.prefix().substring(folderPrefixToQuery.length()), "/");
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

        if (listResponse.isTruncated()) {
          listRequest =
              listRequest.toBuilder()
                  .continuationToken(listResponse.nextContinuationToken())
                  .build();
        }
      } while (listResponse.isTruncated());

      log.info("Successfully listed {} items in folder {}", items.size(), folderPath);
      return items;
    } catch (Exception e) {
      log.error("Failed to list folder contents for {}", folderPath, e);
      throw e;
    }
  }

  @Override
  public S3FolderContentItem getObjectDetails(String path) {
    if (StringUtils.isBlank(path)) {
      return new S3FolderContentItem("", true, null, null);
    }
    try {
      HeadObjectRequest headObjectRequest =
          HeadObjectRequest.builder().bucket(s3BucketName).key(path).build();
      HeadObjectResponse response = s3Client.headObject(headObjectRequest);
      return new S3FolderContentItem(
          getLeafName(path), false, response.contentLength(), response.lastModified());
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        log.error(
            "Error while getting object details for bucket {} and path {}", s3BucketName, path);
        throw e;
      }
      // Not a file - check if it's a virtual folder (has content with this prefix)
      String folderPrefix = getFolderPrefixToQuery(path);
      ListObjectsV2Request listRequest =
          ListObjectsV2Request.builder()
              .bucket(s3BucketName)
              .prefix(folderPrefix)
              .delimiter("/")
              .maxKeys(1)
              .build();
      ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
      if (!listResponse.commonPrefixes().isEmpty()
          || !listResponse.contents().isEmpty()
          || isFolderPlaceholderExists(path)) {
        return new S3FolderContentItem(getLeafName(path), true, null, null);
      }
      return null;
    } catch (Exception e) {
      log.error("Error while getting object details for bucket {} and path {}", s3BucketName, path);
      throw e;
    }
  }

  private boolean isFolderPlaceholderExists(String path) {
    try {
      String folderKey = path.endsWith("/") ? path : path + "/";
      HeadObjectRequest headObjectRequest =
          HeadObjectRequest.builder().bucket(s3BucketName).key(folderKey).build();
      s3Client.headObject(headObjectRequest);
      return true;
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw e;
      }
      return false;
    }
  }

  private static String getLeafName(String path) {
    String name = StringUtils.substringAfterLast(path, "/");
    return name.isEmpty() ? path : name;
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
    String s3Key = StringUtils.isNotBlank(folderPath) ? folderPath + "/" + fileName : fileName;
    try {
      DeleteObjectRequest deleteObjectRequest =
          DeleteObjectRequest.builder().bucket(s3BucketName).key(s3Key).build();

      return s3Client.deleteObject(deleteObjectRequest);
    } catch (Exception e) {
      log.error("Failed to delete object {} from S3", s3Key, e);
      throw e;
    }
  }
}
