package com.researchspace.export.pdf;

import static org.junit.Assert.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.files.service.InternalFileStore;
import com.researchspace.linkedelements.RichTextUpdater;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.ImageBlob;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.RSMath;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.AuditManager;
import com.researchspace.service.EcatImageAnnotationManager;
import com.researchspace.service.MediaManager;
import com.researchspace.service.RSChemElementManager;
import com.researchspace.service.RSMathManager;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.VelocityTestUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.ResourceLoader;

public class ImageRetrieverHelperTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock private ResourceLoader resource;
  @Mock RSChemElementManager chemMgr;
  @Mock EcatImageAnnotationManager imageMgr;
  @Mock RSMathManager mathMgr;

  @Mock MediaManager mediaMgr;
  @Mock AuditManager auditMgr;
  @Mock InternalFileStore fileStore;
  @Mock IPermissionUtils permissions;

  private RichTextUpdater textupdater;
  @InjectMocks private ImageRetrieverHelperTSS imgRetriever;
  private ExportToFileConfig config;
  private User exporter;

  static byte[] fallback = new byte[] {1, 2};

  static class ImageRetrieverHelperTSS extends ImageRetrieverHelperImpl {
    byte[] getUnknown() {
      return fallback;
    }
  }

  @InjectMocks private ImageRetrieverHelperTSS imgRetrieverTSS;

  @Before
  public void setUp() {
    textupdater = new RichTextUpdater();
    exporter = TestFactory.createAnyUser("any");
    config = new ExportToFileConfig();
    config.setExporter(exporter);
    VelocityEngine vel = VelocityTestUtils.setupVelocity("src/main/resources/velocityTemplates");
    textupdater.setVelocity(vel);
  }

  @Test
  public void testIncludeExternalUrlFallback() throws IOException {
    String brokenURL = "https://nonexistent.researchspace.com/image.png";
    assertArrayEquals(fallback, imgRetrieverTSS.getImageBytesFromImgSrc(brokenURL, config));
  }

  @Test
  public void testIncludeStaticResource() throws IOException {
    String imagePath = "/images/icons/mainLogoN2.png";
    File iconIs = RSpaceTestUtils.getResource("mainLogoN2.png");
    FileSystemResource res = new FileSystemResource(iconIs);
    when(resource.getResource(imagePath)).thenReturn(res);
    assertNotNull(imgRetriever.getImageBytesFromImgSrc(imagePath, config));

    // include non-existent resource fails
    File nonExistent = new File("xxaresdsdas.png");
    imagePath = "/images/icons/xxaresdsdas.png";
    res = new FileSystemResource(nonExistent);
    when(resource.getResource(imagePath)).thenReturn(res);
    assertArrayEquals(fallback, imgRetriever.getImageBytesFromImgSrc(imagePath, config));
  }

  @Test
  public void testIncludeChemImageResource() throws IOException {
    RSChemElement chemElement = setUpChemElement();
    setPermissionsToReturn(chemElement, true);
    when(chemMgr.get(2L, config.getExporter())).thenReturn(chemElement);
    String chemLink = textupdater.generateURLStringForRSChemElementLink(2L, 1L, 50, 50);
    assertNotNull(imgRetriever.getImageBytesFromImgSrc(chemLink, config));
  }

  @Test
  public void testIncludeNoneExistentChemImageResource() throws IOException {
    when(chemMgr.get(2L, null)).thenReturn(null);
    String chemLink = textupdater.generateURLStringForRSChemElementLink(2L, 1L, 50, 50);
    assertArrayEquals(fallback, imgRetriever.getImageBytesFromImgSrc(chemLink, config));
  }

  @Test
  public void testIncludeMathEquationResource() throws IOException {
    byte[] simpleSvgBytes = SvgToPngConverterTest.SIMPLEST_SVG.getBytes();
    RSMath math = new RSMath(simpleSvgBytes, "x", null);
    when(mathMgr.get(2L, null, config.getExporter(), true)).thenReturn(math);
    String mathLink = "<img src=\"/svg/2\" />";
    byte[] mathPngBytes = imgRetriever.getImageBytesFromImgSrc(mathLink, config);
    assertNotNull(mathPngBytes);
    assertTrue(SvgToPngConverterTest.SIMPLEST_SVG_CONVERTED_LENGTH <= mathPngBytes.length);
    assertTrue(SvgToPngConverterTest.SIMPLEST_SVG_CONVERTED_LENGTH <= mathPngBytes.length);
  }

  @Test
  public void testIncludeSketchImageEvenIfAnnotationsNotConfigured() throws IOException {
    // happy case
    EcatImageAnnotation sketch = TestFactory.createSketch(1L, 2L);
    setPermissionsToReturn(sketch, true);
    sketch.setData(getAnyPngImage());
    when(imageMgr.get(2L, config.getExporter())).thenReturn(sketch);
    String sketchLink = textupdater.generateImgLinkForSketch(sketch);
    assertNotNull(imgRetriever.getImageBytesFromImgSrc(sketchLink, config));
    // pdf config ignores annotations, but we still want sketches
    config.setAnnotations(false);

    assertNotNull(imgRetriever.getImageBytesFromImgSrc(sketchLink, config));

    // now ensure null returned if sketch doesn't exxist
    when(imageMgr.get(2L, config.getExporter())).thenReturn(null);
    assertArrayEquals(fallback, imgRetriever.getImageBytesFromImgSrc(sketchLink, config));

    // nothing returned if permissions return false
    when(imageMgr.get(2L, config.getExporter())).thenReturn(sketch);
    setPermissionsToReturn(sketch, false);
    assertArrayEquals(fallback, imgRetriever.getImageBytesFromImgSrc(sketchLink, config));
  }

  @Test
  public void testIncludeAnnotationImageIsConfigurable() throws IOException {
    // happy case when annotation is selected for
    EcatImageAnnotation annotation = TestFactory.createImageAnnoatation(1L, 2l);
    annotation.setData(getAnyPngImage());
    when(imageMgr.get(2L, config.getExporter())).thenReturn(annotation);
    setPermissionsToReturn(annotation, true);
    String annotationLink = textupdater.generateAnnotatedImageElement(annotation, 1L + "");
    assertNotNull(imgRetriever.getImageBytesFromImgSrc(annotationLink, config));

    // pdf config ignores annotations, so we want original image or working image...
    // here is working mage
    EcatImage rawimg = TestFactory.createEcatImage(5L);
    setPermissionsToReturn(rawimg, true);
    annotation.setImageId(rawimg.getId());
    rawimg.setWorkingImage(new ImageBlob(getAnyPngImage()));
    config.setAnnotations(false);
    when(mediaMgr.getImage(rawimg.getId(), config.getExporter(), true)).thenReturn(rawimg);
    assertNotNull(imgRetriever.getImageBytesFromImgSrc(annotationLink, config));
    verify(mediaMgr).getImage(rawimg.getId(), config.getExporter(), true);

    // now we set working image to null and set URI to real image.
    rawimg.setWorkingImage(null);
    FileProperty fp = new FileProperty();
    // fp.setFileUri(ServiceTestUtils.getResource("Picture1.png").getAbsolutePath());
    rawimg.setFileProperty(fp);
    File mockFSFile = RSpaceTestUtils.getResource("Picture1.png");
    when(fileStore.retrieve(any(FileProperty.class)))
        .thenReturn(Optional.of(new FileInputStream(mockFSFile)));
    assertNotNull(imgRetriever.getImageBytesFromImgSrc(annotationLink, config));

    // nothing returned if permissions return false
    setPermissionsToReturn(annotation, false);
    assertArrayEquals(fallback, imgRetriever.getImageBytesFromImgSrc(annotationLink, config));
    // throwing auth exception handled as well.
    when(mediaMgr.getImage(rawimg.getId(), config.getExporter(), true))
        .thenThrow(AuthorizationException.class);
    assertArrayEquals(fallback, imgRetriever.getImageBytesFromImgSrc(annotationLink, config));
  }

  void setPermissionsToReturn(IFieldLinkableElement element, boolean allow) {
    when(permissions.isPermittedViaMediaLinksToRecords(
            element, PermissionType.READ, config.getExporter()))
        .thenReturn(allow);
  }

  @Test
  public void testWorkingFPImageIncluded() throws IOException {
    EcatImage rawimg = TestFactory.createEcatImage(5L);
    rawimg.setOwner(exporter);
    String thumbnailLink = textupdater.generateRawImageElement(rawimg, 3L + "");
    File workingImgFile = RSpaceTestUtils.getResource("mainLogoN2.png");
    final Long EXPECTED_LENGTH = workingImgFile.length();
    FileProperty workingImgFP =
        TestFactory.createAFileProperty(workingImgFile, exporter, new FileStoreRoot("/somewhere"));
    rawimg.setWorkingImageFP(workingImgFP);
    when(mediaMgr.getImage(rawimg.getId(), config.getExporter(), true)).thenReturn(rawimg);
    when(fileStore.retrieve(workingImgFP))
        .thenReturn(Optional.of(new FileInputStream(workingImgFile)));
    byte[] retrieved = imgRetriever.getImageBytesFromImgSrc(thumbnailLink, config);

    assertEquals(EXPECTED_LENGTH.intValue(), retrieved.length);
  }

  @Test
  public void testThumbnailImageIncluded() throws IOException {
    EcatImage rawimg = TestFactory.createEcatImage(5L);
    rawimg.setWorkingImage(new ImageBlob(getAnyPngImage()));
    setPermissionsToReturn(rawimg, true);
    String thumbnailLink = textupdater.generateRawImageElement(rawimg, 3L + "");
    // simulate no image annotation
    when(imageMgr.getByParentIdAndImageId(3L, rawimg.getId(), null)).thenReturn(null);
    when(mediaMgr.getImage(rawimg.getId(), config.getExporter(), true)).thenReturn(rawimg);
    assertNotNull(imgRetriever.getImageBytesFromImgSrc(thumbnailLink, config));

    when(mediaMgr.getImage(rawimg.getId(), config.getExporter(), true)).thenReturn(null);
    assertArrayEquals(fallback, imgRetriever.getImageBytesFromImgSrc(thumbnailLink, config));
    when(mediaMgr.getImage(rawimg.getId(), config.getExporter(), true))
        .thenThrow(AuthorizationException.class);
    assertArrayEquals(fallback, imgRetriever.getImageBytesFromImgSrc(thumbnailLink, config));

    // simulate revisioned image link (e.g. from signed document)
    String elementHtml = textupdater.generateRawImageElement(rawimg, 4L + "");
    StructuredDocument doc = TestFactory.createAnySDWithText(elementHtml);
    Field field = doc.getFields().get(0);
    field.setFieldData(elementHtml);
    textupdater.updateLinksWithRevisions(field, 23);
    String revisionedLink = field.getFieldData();
    when(auditMgr.getObjectForRevision(EcatImage.class, rawimg.getId(), 23L))
        .thenReturn(new AuditedEntity<EcatImage>(rawimg, 23));
    assertNotNull(imgRetriever.getImageBytesFromImgSrc(revisionedLink, config));
  }

  RSChemElement setUpChemElement() throws IOException {
    RSChemElement example = TestFactory.createChemElement(1L, 2L);
    // any image is fine here
    example.setDataImage(getAnyPngImage());
    return example;
  }

  byte[] getAnyPngImage() throws IOException {
    return RSpaceTestUtils.getResourceAsByteArray("mainLogoN2.png");
  }
}
