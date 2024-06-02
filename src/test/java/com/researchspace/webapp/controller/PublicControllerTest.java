package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.maintenance.service.MaintenanceManager;
import com.researchspace.model.User;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.archive.IExportUtils;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.Principal;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;

public class PublicControllerTest {

  private static final String DEFAULT_LOGO_NAME = "mainLogoN2.png";
  @Rule public MockitoRule mockery = MockitoJUnit.rule();
  @Mock IExportUtils exportUtils;
  @Mock Principal principal;
  @Mock ResourceLoader resourceLoader;
  @Mock MaintenanceManager maintenanceMgr;
  @Mock IPropertyHolder properties;
  @Mock Resource resource;
  final int defaultImageSize = 10591;
  MockHttpServletResponse response;

  class PublicControllerTSS extends PublicController {
    InputStream getIconImageFromFolder(String fileName) {
      try {
        return new FileInputStream(new File("src/test/resources/TestResources/" + fileName));

      } catch (IOException e) {
        e.printStackTrace();
        return null;
      }
    }
  }

  PublicController publicController;

  @Before
  public void setUp() throws Exception {
    response = new MockHttpServletResponse();
    publicController = new PublicControllerTSS();
    publicController.setExportUtils(exportUtils);
    publicController.setProperties(properties);
    publicController.setResourceLoader(resourceLoader);
    publicController.setMaintenanceManager(maintenanceMgr);
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void publishPDF() throws Exception {
    final User user = TestFactory.createAnyUser("any");
    publicController.getExportedPublishedFile("fileStoreName.pdf", response);
    Mockito.verify(exportUtils, Mockito.times(1)).display("fileStoreName.pdf", null, response);
  }

  @Test
  public void publishWORD() throws Exception {
    final User user = TestFactory.createAnyUser("any");
    publicController.getExportedPublishedFile("fileStoreName.doc", response);
    Mockito.verify(exportUtils, Mockito.times(1)).display("fileStoreName.doc", null, response);
  }

  @Test
  public void testBannerWithWrongFileTypeReturnsDefault() throws Exception {
    final String disallowed = "u4c.tif";
    // path is set and  does exist
    setUpHappyCaseExpectationsForImage(disallowed);
    ResponseEntity<byte[]> resp = publicController.getBanner(null);
    // mockery.assertIsSatisfied();
    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertEquals(MediaType.IMAGE_PNG, resp.getHeaders().getContentType());
    assertEquals(defaultImageSize, resp.getBody().length);
    assertEquals(DEFAULT_LOGO_NAME, publicController.bannerImgName());
  }

  void setUpHappyCaseExpectationsForImage(final String imagePath) throws IOException {
    when(properties.getBannerImagePath()).thenReturn(imagePath);
    when(resourceLoader.getResource(imagePath)).thenReturn(resource);
    when(resource.exists()).thenReturn(true);
    when(resource.getInputStream())
        .thenReturn(RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder(imagePath));
    when(resource.getFilename()).thenReturn(imagePath);
  }

  @Test
  public void testBanner() throws Exception {
    final String ptestImagPath = "IS1.jpg";

    final int testImageSize = 12287;
    // path not set, use default
    when(properties.getBannerImagePath()).thenReturn("");

    ResponseEntity<byte[]> resp = publicController.getBanner(null);
    Mockito.verify(properties).getBannerImagePath();
    assertDefaultImage(defaultImageSize, resp);
    // path is set, but file  doesn't exist.
    when(properties.getBannerImagePath()).thenReturn(ptestImagPath);
    when(resourceLoader.getResource(ptestImagPath)).thenReturn(resource);
    when(resource.exists()).thenReturn(false);

    resp = publicController.getBanner(null);
    assertDefaultImage(defaultImageSize, resp);
    assertEquals(DEFAULT_LOGO_NAME, publicController.bannerImgName());
    Mockito.reset(properties);
    setUpHappyCaseExpectationsForImage(ptestImagPath);

    resp = publicController.getBanner(null);
    // mockery.assertIsSatisfied();
    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertEquals(MediaType.IMAGE_JPEG, resp.getHeaders().getContentType());
    assertEquals(testImageSize, resp.getBody().length);
    assertEquals("IS1.jpg", publicController.bannerImgName());

    // now we test getting another default image ( for use with RSpace logos)
    when(properties.getBannerImagePath()).thenReturn("");
    resp = publicController.getBanner("biggerLogo.png");
    // mockery.assertIsSatisfied();
    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertEquals(MediaType.IMAGE_PNG, resp.getHeaders().getContentType());
    assertEquals(17333, resp.getBody().length);
    assertEquals("biggerLogo.png", publicController.bannerImgName());
  }

  private void assertDefaultImage(final int defaultImageSize, ResponseEntity<byte[]> resp) {
    assertEquals(HttpStatus.OK, resp.getStatusCode());
    assertEquals(MediaType.IMAGE_PNG, resp.getHeaders().getContentType());
    assertEquals(defaultImageSize, resp.getBody().length);
  }

  @Test
  public void testMaintenanceMessageStatus() throws Exception {
    assertEquals("No maintenance", publicController.maintenanceStatus());
  }
}
