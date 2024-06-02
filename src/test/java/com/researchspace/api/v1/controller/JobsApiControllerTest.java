package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiExportJobResult;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.aws.S3Utilities;
import java.net.MalformedURLException;
import java.net.URL;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;

public class JobsApiControllerTest {
  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock JobsApiHandler handler;
  @Mock IPropertyHolder props;
  @Mock S3Utilities s3Utils;
  @InjectMocks JobsApiController controller;
  User exporter = TestFactory.createAnyUser("any");
  MockHttpServletResponse response;

  @Before
  public void setUp() throws Exception {
    response = new MockHttpServletResponse();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testS3enabledDownloadLinkSuccess() throws BindException, MalformedURLException {
    ApiJob completed = createCompletedApiJob();
    controller.hasS3Access = true;
    when(handler.getJob(1L, exporter)).thenReturn(completed);
    URL expectedLink = new URL("https://somelink.com");
    when(s3Utils.getPresignedUrlForArchiveDownload(completed.getResourceLocation()))
        .thenReturn(expectedLink);

    ApiJob job = controller.get(1L, response, exporter);
    assertNotNull(job);
    assertTrue(job.isCompleted());
    assertTrue(job.getLinks().iterator().next().getLink().equals(expectedLink.toString()));
    assertEquals(HttpStatus.OK.value(), response.getStatus());
  }

  @Test
  public void testS3enabledDownloadLinkFailure() throws BindException, MalformedURLException {
    ApiJob completed = createCompletedApiJob();
    controller.hasS3Access = true;
    when(handler.getJob(1L, exporter)).thenReturn(completed);
    when(s3Utils.getPresignedUrlForArchiveDownload(completed.getResourceLocation()))
        .thenReturn(null);
    CoreTestUtils.assertIllegalStateExceptionThrown(() -> controller.get(1L, response, exporter));
  }

  @Test
  public void testGetCompleteJob() throws BindException {
    ApiJob completed = createCompletedApiJob();
    Mockito.when(handler.getJob(1L, exporter)).thenReturn(completed);

    ApiJob job = controller.get(1L, response, exporter);
    assertNotNull(job);
    assertTrue(job.isCompleted());

    assertTrue(job.getLinks().iterator().next().getRel().equals(ApiLinkItem.ENCLOSURE_REL));
    assertEquals(HttpStatus.OK.value(), response.getStatus());
  }

  @Test
  public void testGetIncompleteJob() throws BindException {
    ApiJob incompleted = createIncompleteApiJob();
    Mockito.when(handler.getJob(1L, exporter)).thenReturn(incompleted);

    ApiJob job = controller.get(1L, response, exporter);
    assertNotNull(job);
    assertFalse(job.isCompleted());
    assertNull(response.getHeader("Location"));
    assertEquals(HttpStatus.OK.value(), response.getStatus());
  }

  private ApiJob createCompletedApiJob() {
    return new ApiJob(1L, "STARTED", 0.0, new ApiExportJobResult(), true, "something.txt");
  }

  private ApiJob createIncompleteApiJob() {
    return new ApiJob(1L, "FAILED");
  }
}
