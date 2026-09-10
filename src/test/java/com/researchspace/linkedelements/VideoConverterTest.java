package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.VIDEO_CLASSNAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.dao.EcatVideoDao;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.TestFactory;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class VideoConverterTest extends AbstractParserTest {

  private @Mock EcatVideoDao ecaVideoDao;

  @InjectMocks private VideoConverter videoConverter;

  @BeforeEach
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
    assertThat(contents.getElements(EcatVideo.class).getElements()).element(0).isEqualTo(ecatVideo);
    assertThat(contents.getElements(EcatVideo.class).getLinks()).hasSize(1);
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
    assertThat(contents.getElements(EcatVideo.class).getElements()).hasSize(1);
    assertThat(contents.getElements(EcatVideo.class).getElements()).element(0).isEqualTo(ecatVideo);
    assertThat(contents.getElements(EcatVideo.class).getLinks()).hasSize(1);
  }
}
