package com.researchspace.linkedelements;

import static com.researchspace.core.util.FieldParserConstants.ATTACHMENT_CLASSNAME;
import static org.junit.Assert.assertEquals;

import com.researchspace.dao.EcatDocumentFileDao;
import com.researchspace.model.EcatDocumentFile;
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

public class AttchmentConverterTest extends AbstractParserTest {

  @Rule public MockitoRule mockery = MockitoJUnit.rule();

  private @Mock EcatDocumentFileDao docDao;

  @InjectMocks AttachmentConverter attachmentConverter;

  @Before
  public void setUp() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void parse() {
    final EcatDocumentFile attachment = TestFactory.createEcatDocument(3L, anyUser);
    String elementHTml = rtu.generateURLString(attachment);
    Element toconvert = getElementToConvert(elementHTml, ATTACHMENT_CLASSNAME);
    Mockito.when(docDao.getSafeNull(3L)).thenReturn(Optional.of(attachment));
    attachmentConverter.jsoup2LinkableElement(contents, toconvert);
    assertEquals(attachment, contents.getElements(EcatDocumentFile.class).getElements().get(0));
    assertEquals(1, contents.getElements(EcatDocumentFile.class).getLinks().size());
    assertEquals(1, contents.getElements(EcatDocumentFile.class).getPairs().size());
  }

  @Test
  public void parseRevisionedLink() {
    final EcatDocumentFile attachment = TestFactory.createEcatDocument(3L, anyUser);
    String elementHtml = rtu.generateURLString(attachment);
    StructuredDocument doc = TestFactory.createAnySDWithText(elementHtml);
    Field field = doc.getFields().get(0);
    field.setFieldData(elementHtml);
    rtu.updateLinksWithRevisions(field, 24);
    String revisionedHtml = field.getFieldData();

    Element toconvert = getElementToConvert(revisionedHtml, ATTACHMENT_CLASSNAME);
    Mockito.when(docDao.getSafeNull(3L)).thenReturn(Optional.of(attachment));
    Mockito.when(auditDao.getObjectForRevision(EcatDocumentFile.class, 3L, 24L))
        .thenReturn(new AuditedEntity<EcatDocumentFile>(attachment, 24L));
    attachmentConverter.jsoup2LinkableElement(contents, toconvert);
    assertEquals(1, contents.getElements(EcatDocumentFile.class).getElements().size());
    assertEquals(attachment, contents.getElements(EcatDocumentFile.class).getElements().get(0));
    assertEquals(1, contents.getElements(EcatDocumentFile.class).getLinks().size());
  }
}
