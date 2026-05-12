package com.researchspace.service.aws;

import com.researchspace.service.archive.export.ExportFailureException;
import java.io.File;
import java.net.URL;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;

/** Interface used by export code to interact with S3 code in AWS SDK. */
public interface S3ExportUtilities {

  /**
   * Checks if an archive object is stored in S3
   *
   * @param fileName the file name to check
   * @return true or false as to whether the object exists in S3
   */
  boolean isArchiveInS3(String fileName);

  SdkHttpResponse uploadArchiveToS3(File file);

  /**
   * Generates a pre-signed url which can be used to download an object from S3, this will only
   * generate a url for an object that currently exists in S3 otherwise it will return null
   *
   * @param fileName the file name to generate the url for
   * @return URL the presigned url which can be used to download the object from S3
   * @throws ExportFailureException if an exception occurs while generating the pre-signed url.
   */
  URL getPresignedUrlForArchiveDownload(String fileName);

  DeleteObjectResponse deleteArchiveFromS3(String fileName);

  /** No-op implementation for when a property 'hasS3Access' is false. */
  S3ExportUtilities NOOP_S3ExportUtilities =
      new S3ExportUtilities() {

        @Override
        public boolean isArchiveInS3(String fileName) {
          return false;
        }

        @Override
        public SdkHttpResponse uploadArchiveToS3(File file) {
          return null;
        }

        @Override
        public URL getPresignedUrlForArchiveDownload(String fileName) {
          return null;
        }

        @Override
        public DeleteObjectResponse deleteArchiveFromS3(String fileName) {
          return null;
        }
        ;
      };
}
