package com.researchspace.offline.service;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.offline.service.impl.MobileToRsContentConverter;
import com.researchspace.offline.service.impl.RsToMobileContentConverter;
import com.researchspace.service.EcatImageAnnotationManager;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;

public class MobileContentConverterTest extends SpringTransactionalTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();

  private @Autowired RsToMobileContentConverter rsToMobileConverter;
  private @Autowired MobileToRsContentConverter mobileToRsConverter;
  private @Autowired EcatImageAnnotationManager ecatImageAnnotationManager;

  private String rsFieldContentWithImages =
      "<p>image:&nbsp;</p><p><img id=\"32820-3572\" class=\"imageDropped inlineImageThumbnail\""
          + " src=\"/thumbnail/data?sourceType=IMAGE&amp;sourceId=3572&amp;sourceParentId=32820&amp;width=300&amp;height=340&amp;time=1406822005870\""
          + " alt=\"image \" width=\"300\" height=\"340\" data-size=\"300-340\""
          + " /></p><p>&nbsp;annotation:</p><p><img id=\"32820-3573\" class=\"imageDropped\""
          + " src=\"/image/getImage/32820-3573/1416434355268\" alt=\"image\" width=\"489\""
          + " height=\"307\" /></p><p>&nbsp;sketch:</p><p><img id=\"32769\" class=\"sketch\""
          + " src=\"/image/getImageSketch/32769/1416434370513\" alt=\"user sketch\" width=\"449\""
          + " height=\"430\" /></p><p>&nbsp;</p>";

  private String mobileFieldContentWithImages =
      "<p>image:&nbsp;</p>\n"
          + "<p><img data-type=\"image\" data-id=\"3572\" width=\"300\" height=\"340\"></p>\n"
          + "<p>&nbsp;annotation:</p>\n"
          + "<p><img data-type=\"annotation\" data-id=\"999\" data-imageid=\"3573\" width=\"489\""
          + " height=\"307\"></p>\n"
          + "<p>&nbsp;sketch:</p>\n"
          + "<p><img data-type=\"sketch\" data-id=\"32769\" width=\"449\" height=\"430\"></p>\n"
          + "<p>&nbsp;</p>";

  @Test
  public void convertRsContentToMobileFormat() throws Exception {

    rsToMobileConverter.setEcatImageAnnotationManager(getEcatImageAnnotationManagerMock());

    String mobileContent =
        rsToMobileConverter.convertFieldContent(rsFieldContentWithImages, 32820L);
    assertEquals(mobileFieldContentWithImages, mobileContent);

    rsToMobileConverter.setEcatImageAnnotationManager(ecatImageAnnotationManager);
  }

  private EcatImageAnnotationManager getEcatImageAnnotationManagerMock() {
    final EcatImageAnnotationManager annotationManagerMock =
        Mockito.mock(EcatImageAnnotationManager.class);
    final EcatImageAnnotation testImageAnnotation = new EcatImageAnnotation();
    testImageAnnotation.setId(999L);
    when(annotationManagerMock.getByParentIdAndImageId(32820L, 3573, null))
        .thenReturn(testImageAnnotation);
    return annotationManagerMock;
  }

  // @Test
  public void convertMobileContentToRsFormat() throws Exception {

    String rsContent =
        mobileToRsConverter.convertFieldContent(mobileFieldContentWithImages, 32820L);
    assertEquals(rsFieldContentWithImages, rsContent);
  }
}
