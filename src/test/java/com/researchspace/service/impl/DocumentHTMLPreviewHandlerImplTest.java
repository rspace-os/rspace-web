package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.researchspace.export.pdf.ExportProcessorInput;
import com.researchspace.export.pdf.HTMLStringGenerator;
import com.researchspace.export.pdf.StructuredDocumentHTMLViewConfig;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.RecordManager;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DocumentHTMLPreviewHandlerImplTest {

  private User anyUser;
  private @Mock RecordManager recordManager;
  private @Mock HTMLStringGenerator htmlStringGenerator;

  @InjectMocks private DocumentHTMLPreviewHandlerImpl docPreviewer;

  @BeforeEach
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

  private ExportProcessorInput anyInput() {
    return new ExportProcessorInput("<p>content</p>", null, null, null, null);
  }

  private StructuredDocument createADocument() {
    StructuredDocument anyDoc = TestFactory.createAnySD();
    anyDoc.setId(1L);
    anyDoc.setOwner(anyUser);
    return anyDoc;
  }
}
