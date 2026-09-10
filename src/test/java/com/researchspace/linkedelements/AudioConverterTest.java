package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.AUDIO_CLASSNAME;
import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.dao.EcatAudioDao;
import com.researchspace.model.EcatAudio;
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
public class AudioConverterTest extends AbstractParserTest {

  private @Mock EcatAudioDao ecatAudioDao;

  @InjectMocks AudioConverter audioConverter;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
  }

  @Test
  public void parse() {
    final EcatAudio ecatAudio = TestFactory.createEcatAudio(3L, anyUser);
    String elementHTml = rtu.generateURLString(ecatAudio, 14799L);
    Element toconvert = getElementToConvert(elementHTml, AUDIO_CLASSNAME);
    Mockito.when(ecatAudioDao.getSafeNull(3L)).thenReturn(Optional.of(ecatAudio));
    audioConverter.jsoup2LinkableElement(contents, toconvert);
    assertThat(contents.getElements(EcatAudio.class).getElements()).element(0).isEqualTo(ecatAudio);
    assertThat(contents.getElements(EcatAudio.class).getLinks()).hasSize(1);
  }

  @Test
  public void parseRevisionedLink() {
    final EcatAudio ecatAudio = TestFactory.createEcatAudio(2L, anyUser);
    String elementHtml = rtu.generateURLString(ecatAudio, 14799L);
    StructuredDocument doc = TestFactory.createAnySDWithText(elementHtml);
    Field field = doc.getFields().get(0);
    field.setFieldData(elementHtml);
    rtu.updateLinksWithRevisions(field, 23);
    String revisionedHtml = field.getFieldData();

    Element toconvert = getElementToConvert(revisionedHtml, AUDIO_CLASSNAME);
    Mockito.when(ecatAudioDao.getSafeNull(2L)).thenReturn(Optional.of(ecatAudio));
    Mockito.when(auditDao.getObjectForRevision(EcatAudio.class, 2L, 23L))
        .thenReturn(new AuditedEntity<EcatAudio>(ecatAudio, 23L));
    audioConverter.jsoup2LinkableElement(contents, toconvert);
    assertThat(contents.getElements(EcatAudio.class).getElements()).hasSize(1);
    assertThat(contents.getElements(EcatAudio.class).getElements()).element(0).isEqualTo(ecatAudio);
    assertThat(contents.getElements(EcatAudio.class).getLinks()).hasSize(1);
  }
}
