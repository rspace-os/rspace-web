package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.IMAGE_DROPPED_CLASS_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.researchspace.dao.EcatImageAnnotationDao;
import com.researchspace.dao.EcatImageDao;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class ImageConverterTest extends AbstractParserTest {

  private @Mock EcatImageDao imageDao;

  private @Mock EcatImageAnnotationDao imageAnnotationDao;

  @InjectMocks private ImageConverter imageConverter;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void handleAnnotationElementWithNullId() {
    EcatImageAnnotation annotation = new EcatImageAnnotation();
    String annotationLink = rtu.generateAnnotatedImageElement(annotation, "null");
    Element jsoup = createJSoupForAnnotationLink(annotationLink);
    EcatImageAnnotation convertedAnnotation = imageConverter.getImageAnnotationFromElement(jsoup);
    assertNull(convertedAnnotation);

    // try link without data-id part (goes through different code path)
    String annotationLinkNoDataId = annotationLink.replace("data-id", "data-noid");
    Element jsoup2 = createJSoupForAnnotationLink(annotationLinkNoDataId);
    EcatImageAnnotation convertedAnnotation2 = imageConverter.getImageAnnotationFromElement(jsoup2);
    assertNull(convertedAnnotation2);
  }

  @Test
  public void missingAnnotationShouldReturnNull() {
    EcatImageAnnotation annotation = new EcatImageAnnotation();
    annotation.setId(1L);
    String annotationLink = rtu.generateAnnotatedImageElement(annotation, "12345");
    Element jsoup = createJSoupForAnnotationLink(annotationLink);
    Mockito.when(imageAnnotationDao.getSafeNull(1L)).thenReturn(Optional.empty());
    EcatImageAnnotation convertedAnnotation = imageConverter.getImageAnnotationFromElement(jsoup);
    assertNull(convertedAnnotation);
  }

  private Element createJSoupForAnnotationLink(String link) {
    return Jsoup.parse("<html>" + link + "</html>").select("img").first();
  }

  @Test
  public void parseRevisionedLink() {
    final EcatImage image = TestFactory.createEcatImage(2L);
    String elementHtml = rtu.generateURLStringForEcatImageLink(image, "12345");
    StructuredDocument doc = TestFactory.createAnySDWithText(elementHtml);
    Field field = doc.getFields().get(0);
    field.setFieldData(elementHtml);
    rtu.updateLinksWithRevisions(field, 23);
    String revisionedHtml = field.getFieldData();

    Element toconvert = getElementToConvert(revisionedHtml, IMAGE_DROPPED_CLASS_NAME);
    Mockito.when(imageDao.getSafeNull(2L)).thenReturn(Optional.of(image));
    Mockito.when(auditDao.getObjectForRevision(EcatImage.class, 2L, 23L))
        .thenReturn(new AuditedEntity<EcatImage>(image, 23L));
    imageConverter.jsoup2LinkableElement(contents, toconvert);
    assertEquals(1, contents.getElements(EcatImage.class).getElements().size());
    assertEquals(image, contents.getElements(EcatImage.class).getElements().get(0));
    assertEquals(1, contents.getElements(EcatImage.class).getLinks().size());
  }
}
