package com.researchspace.service.aws.impl;

import static org.junit.Assert.assertTrue;

import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

@Slf4j
public class S3UtilitiesTest {

  @Test
  public void getUploadStrategyForFileSize() {
    S3UtilitiesImpl impl = new S3UtilitiesImpl();
    impl.setChunkedUploadMbThreshold(1);
    File subThreshold = RSpaceTestUtils.getResource("adrenaline.smiles"); // 90bytes
    assertTrue(impl.getS3Uploader(null, subThreshold) instanceof S3PutUploader);

    File requiresChunking = RSpaceTestUtils.getResource("weather_data2.csv"); // 7 Mb
    assertTrue(impl.getS3Uploader(null, requiresChunking) instanceof S3MultipartChunkedUploader);
  }
}
