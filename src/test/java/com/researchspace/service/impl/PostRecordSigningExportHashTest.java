package com.researchspace.service.impl;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.archive.ArchiveResult;
import com.researchspace.dao.SignatureDao;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Signature;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.SigningResult;
import com.researchspace.service.CommunicationManager;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.service.archive.ExportImport;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.scheduling.annotation.AsyncResult;

public class PostRecordSigningExportHashTest {

  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock CommunicationManager comms;
  @Mock FileStore fStore;
  @Mock SignatureDao sigDao;
  @Mock ExportImport exportImport;
  @InjectMocks PostRecordSigningExportHash postSigner;
  User any;
  StructuredDocument doc = null;
  ArchiveResult result = null;
  Signature sig = null;
  SigningResult signatureResult = null;
  File mockPdfExportFile = null;

  @Before
  public void setUp() throws Exception {
    postSigner.setServerURLPrefix("http://anywhere.com");
    any = TestFactory.createAnyUser("any");
    doc = TestFactory.createAnySD();
    doc.setOwner(any);
    doc.setId(1L);
    result = new ArchiveResult();
    sig = TestFactory.createASignature(doc, any);
    signatureResult = new SigningResult(doc, "OK", sig);
    mockPdfExportFile = File.createTempFile("pdf", ".pdf");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testBasicExportAndSigning() throws IOException {
    result.setExportFile(File.createTempFile("test", "html"));
    when(performMockArchive()).thenReturn(new AsyncResult<ArchiveResult>(result));
    setUpPerformPdfExportOk();
    postSigner.postRecordSign(signatureResult);
    final int EXPECTED_TOTAL_EXPORT_COUNT = 3;
    verifyNFilesSaved(EXPECTED_TOTAL_EXPORT_COUNT);
    verifyNSigsUpdated(EXPECTED_TOTAL_EXPORT_COUNT);
  }

  @Test
  public void testBasicExportAndSigningContinuesIfAnyExceptionThrown() throws IOException {
    result.setExportFile(File.createTempFile("test", "html"));
    when(performMockArchive()).thenThrow(RuntimeException.class);
    setUpPerformPdfExportOk();
    postSigner.postRecordSign(signatureResult);
    // pdf export works ok even if archiving throws exception
    final int EXPECTED_TOTAL_EXPORT_COUNT = 1;
    verifyNFilesSaved(EXPECTED_TOTAL_EXPORT_COUNT);
    verifyNSigsUpdated(EXPECTED_TOTAL_EXPORT_COUNT);
  }

  private Future<ArchiveResult> performMockArchive() {
    return exportImport.asyncExportSelectionToArchive(
        Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
  }

  private void verifyNSigsUpdated(final int EXPECTED_TOTAL_EXPORT_COUNT) {
    verify(sigDao, times(EXPECTED_TOTAL_EXPORT_COUNT)).save(Mockito.any(Signature.class));
  }

  private void verifyNFilesSaved(final int EXPECTED_TOTAL_EXPORT_COUNT) throws IOException {
    verify(fStore, times(EXPECTED_TOTAL_EXPORT_COUNT))
        .save(
            Mockito.any(FileProperty.class),
            Mockito.any(File.class),
            Mockito.any(FileDuplicateStrategy.class));
  }

  private void setUpPerformPdfExportOk() throws IOException {
    when(exportImport.asyncExportSelectionToPdfForSigning(
            Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any()))
        .thenReturn(new AsyncResult<File>(mockPdfExportFile));
  }
}
