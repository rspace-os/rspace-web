package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.AUDIO_CLASSNAME;
import static org.junit.Assert.assertEquals;

import com.researchspace.dao.EcatAudioDao;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import java.util.Optional;
import org.jsoup.nodes.Element;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class AudioConverterTest extends AbstractParserTest {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();

  private @Mock EcatAudioDao ecatAudioDao;

  @InjectMocks AudioConverter audioConverter;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void parse() {
    final EcatAudio ecatAudio = TestFactory.createEcatAudio(3L, anyUser);
    String elementHTml = rtu.generateURLString(ecatAudio, 14799L);
    Element toconvert = getElementToConvert(elementHTml, AUDIO_CLASSNAME);
    Mockito.when(ecatAudioDao.getSafeNull(3L)).thenReturn(Optional.of(ecatAudio));
    audioConverter.jsoup2LinkableElement(contents, toconvert);
    assertEquals(ecatAudio, contents.getElements(EcatAudio.class).getElements().get(0));
    assertEquals(1, contents.getElements(EcatAudio.class).getLinks().size());
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
    assertEquals(1, contents.getElements(EcatAudio.class).getElements().size());
    assertEquals(ecatAudio, contents.getElements(EcatAudio.class).getElements().get(0));
    assertEquals(1, contents.getElements(EcatAudio.class).getLinks().size());
  }
}
