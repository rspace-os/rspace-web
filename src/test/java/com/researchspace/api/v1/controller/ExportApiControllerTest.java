package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;

import com.researchspace.api.v1.controller.ExportApiController.ExportApiConfig;
import com.researchspace.api.v1.controller.ExportApiController.ExportRetrievalConfig;
import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.api.v1.model.ApiLinkItem;
import com.researchspace.api.v1.service.ExportApiHandler;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.archive.ExportImport;
import com.researchspace.service.archive.export.ExportFailureException;
import java.util.Optional;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

public class ExportApiControllerTest {
  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock ExportApiHandler handler;
  @Mock IPropertyHolder props;
  @Mock ExportImport exporterService;
  @InjectMocks ExportApiController controller;
  User exporter = TestFactory.createAnyUser("any");
  MockHttpServletResponse response;

  @Before
  public void setUp() throws Exception {
    response = new MockHttpServletResponse();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testOKSubmission() throws BindException {
    ExportApiConfig cfg = new ExportApiConfig("html", "user");
    cfg.setIncludeRevisionHistory(true);
    ApiJob expected = createApiJob();
    String url = "http://com.r.c";
    Mockito.when(handler.export(cfg, exporter)).thenReturn(Optional.of(expected));
    Mockito.when(props.getServerUrl()).thenReturn(url);
    ApiJob job = controller.export(cfg, new BeanPropertyBindingResult(cfg, "bean"), exporter);

    assertEquals(expected, job);
    assertEquals(url + "/api/v1/jobs/1", expected.getLinks().get(0).getLink());
    assertEquals(ApiLinkItem.SELF_REL, expected.getLinks().get(0).getRel());
  }

  @Test(expected = ExportFailureException.class)
  public void testExportFailureExecptionTrhownIfJobNotlaunched() throws BindException {
    ExportApiConfig cfg = new ExportApiConfig("html", "user");
    Mockito.when(handler.export(cfg, exporter)).thenReturn(Optional.empty());
    controller.export(cfg, new BeanPropertyBindingResult(cfg, "bean"), exporter);
  }

  @Test
  public void testOKGet() throws BindException {
    ExportRetrievalConfig cfg = new ExportRetrievalConfig("file.zip");
    controller.getExport(cfg, new BeanPropertyBindingResult(cfg, "bean"), response);
  }

  private ApiJob createApiJob() {
    return new ApiJob(1L, "STARTED");
  }
}
