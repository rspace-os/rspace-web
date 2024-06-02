package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.VIDEO_CLASSNAME;
import static org.junit.Assert.assertEquals;

import com.researchspace.dao.EcatVideoDao;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class VideoConverterTest extends AbstractParserTest {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();

  private @Mock EcatVideoDao ecaVideoDao;

  @InjectMocks private VideoConverter videoConverter;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void parse() {
    final EcatVideo ecatVideo = TestFactory.createEcatVideo(2L);
    String elementHTml = rtu.generateURLString(ecatVideo, 14799L);
    Element toconvert = getElementToConvert(elementHTml, VIDEO_CLASSNAME);
    Mockito.when(ecaVideoDao.getSafeNull(2L)).thenReturn(Optional.of(ecatVideo));
    videoConverter.jsoup2LinkableElement(contents, toconvert);
    assertEquals(ecatVideo, contents.getElements(EcatVideo.class).getElements().get(0));
    assertEquals(1, contents.getElements(EcatVideo.class).getLinks().size());
  }

  @Test
  public void parseRevisionedLink() {
    final EcatVideo ecatVideo = TestFactory.createEcatVideo(2L);
    String elementHtml = rtu.generateURLString(ecatVideo, 14799L);
    StructuredDocument doc = TestFactory.createAnySDWithText(elementHtml);
    Field field = doc.getFields().get(0);
    field.setFieldData(elementHtml);
    rtu.updateLinksWithRevisions(field, 23);
    String revisionedHtml = field.getFieldData();

    Element toconvert = getElementToConvert(revisionedHtml, VIDEO_CLASSNAME);
    Mockito.when(ecaVideoDao.getSafeNull(2L)).thenReturn(Optional.of(ecatVideo));
    Mockito.when(auditDao.getObjectForRevision(EcatVideo.class, 2L, 23L))
        .thenReturn(new AuditedEntity<EcatVideo>(ecatVideo, 23L));
    videoConverter.jsoup2LinkableElement(contents, toconvert);
    assertEquals(1, contents.getElements(EcatVideo.class).getElements().size());
    assertEquals(ecatVideo, contents.getElements(EcatVideo.class).getElements().get(0));
    assertEquals(1, contents.getElements(EcatVideo.class).getLinks().size());
  }
}
