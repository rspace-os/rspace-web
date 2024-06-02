package com.researchspace.service.aws.impl;

import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.model.UploadPartResponse;

public class S3MultipartUploaderTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  File largeS3Upload = RSpaceTestUtils.getResource("weather_data2.csv");
  @Mock S3Client client;
  S3MultipartChunkedUploader uploader;

  @Before
  public void before() {
    uploader = new S3MultipartChunkedUploader(client, "MyBucket", "archivePath", 5);
  }

  @Test
  public void rejectChunkedUploadIfFileTooSmall() throws Exception {
    assertIllegalArgumentException(
        () -> uploader.apply(RSpaceTestUtils.getResource("adrenaline.smiles")));
  }

  @Test
  public void s3UploadHappyCase() throws Exception {

    createMultiPartUploadRequest();

    UploadPartResponse resp = createSuccessfulPartUploadResponse();
    setUpUploadCompletionSuccess();

    when(client.uploadPart(Mockito.any(UploadPartRequest.class), Mockito.any(RequestBody.class)))
        .thenReturn(resp);

    uploader.apply(largeS3Upload);
    final int expectedChunkCount = 2;
    assertUploadAttemptCount(expectedChunkCount);
    assertAbortNotCalled();
  }

  private void setUpUploadCompletionSuccess() {
    SdkHttpResponse resp2 = SdkHttpResponse.builder().statusCode(200).build();
    CompleteMultipartUploadResponse finalResp =
        (CompleteMultipartUploadResponse)
            CompleteMultipartUploadResponse.builder().sdkHttpResponse(resp2).build();
    when(client.completeMultipartUpload(Mockito.any(CompleteMultipartUploadRequest.class)))
        .thenReturn(finalResp);
  }

  private UploadPartResponse createSuccessfulPartUploadResponse() {
    SdkHttpResponse httpResp = create200SuccessPartUploadResponse();
    UploadPartResponse resp =
        (UploadPartResponse)
            UploadPartResponse.builder().eTag("etag1").sdkHttpResponse(httpResp).build();
    return resp;
  }

  @Test
  public void s3UploadChunkUploadFailRetry() throws Exception {

    createMultiPartUploadRequest();
    UploadPartRequest partReq = Mockito.any(UploadPartRequest.class);

    // fail every chunk upload, will retry 1st chunk 3 times then abort
    when(client.uploadPart(partReq, Mockito.any(RequestBody.class))).thenThrow(S3Exception.class);

    uploader.apply(largeS3Upload);
    final int expectedChunkCount = 3;
    assertUploadAttemptCount(expectedChunkCount);
    verify(client).abortMultipartUpload(Mockito.any(AbortMultipartUploadRequest.class));
  }

  private void assertUploadAttemptCount(final int expectedChunkCount) {
    verify(client, Mockito.times(expectedChunkCount))
        .uploadPart(Mockito.any(UploadPartRequest.class), Mockito.any(RequestBody.class));
  }

  @Test
  public void s3UploadChunkUploadFailOnceThenSucceed() throws Exception {

    createMultiPartUploadRequest();
    UploadPartResponse resp = createSuccessfulPartUploadResponse();
    setUpUploadCompletionSuccess();

    // succeed 1st, fail 2nd chunk upload once, then succeed
    when(client.uploadPart(Mockito.any(UploadPartRequest.class), Mockito.any(RequestBody.class)))
        .thenReturn(resp)
        .thenThrow(S3Exception.class)
        .thenReturn(resp);

    uploader.apply(largeS3Upload);
    final int expectedChunkUploadCount = 3;
    assertUploadAttemptCount(expectedChunkUploadCount);
    assertAbortNotCalled();
  }

  private void assertAbortNotCalled() {
    verify(client, Mockito.never())
        .abortMultipartUpload(Mockito.any(AbortMultipartUploadRequest.class));
  }

  private void createMultiPartUploadRequest() {
    CreateMultipartUploadResponse mockResponse =
        CreateMultipartUploadResponse.builder().uploadId("UploadId1").build();
    Mockito.when(client.createMultipartUpload(Mockito.any(CreateMultipartUploadRequest.class)))
        .thenReturn(mockResponse);
  }

  private SdkHttpResponse create200SuccessPartUploadResponse() {
    SdkHttpResponse httpResp = SdkHttpResponse.builder().statusCode(200).build();
    return httpResp;
  }
}
