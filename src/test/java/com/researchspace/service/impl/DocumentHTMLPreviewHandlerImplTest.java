package com.researchspace.service.impl;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.export.pdf.ExportProcesserInput;
import com.researchspace.export.pdf.HTMLStringGenerator;
import com.researchspace.export.pdf.StructuredDocumentHTMLViewConfig;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.RecordManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class DocumentHTMLPreviewHandlerImplTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();

  private User anyUser;
  private @Mock RecordManager recordManager;
  private @Mock HTMLStringGenerator htmlStringGenerator;

  @InjectMocks private DocumentHTMLPreviewHandlerImpl docPreviewer;

  @Before
  public void setUp() throws Exception {
    anyUser = TestFactory.createAnyUser("any");
  }

  @Test
  public void generateHtmlFromDoc() {
    StructuredDocument anyDoc = createADocument();
    when(recordManager.getRecordWithFields(Mockito.eq(1L), Mockito.eq(anyUser))).thenReturn(anyDoc);
    when(htmlStringGenerator.extractHtmlStr(
            Mockito.eq(anyDoc), Mockito.any(StructuredDocumentHTMLViewConfig.class)))
        .thenReturn(anyInput());
    String content = docPreviewer.generateHtmlPreview(1L, anyUser).getHtmlContent();
    assertEquals(anyInput().getDocumentAsHtml(), content);
  }

  private ExportProcesserInput anyInput() {
    return new ExportProcesserInput("<p>content</p>", null, null, null);
  }

  private StructuredDocument createADocument() {
    StructuredDocument anyDoc = TestFactory.createAnySD();
    anyDoc.setId(1L);
    anyDoc.setOwner(anyUser);
    return anyDoc;
  }
}
