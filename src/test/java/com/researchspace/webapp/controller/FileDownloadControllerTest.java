package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.documentconversion.spi.ConversionResult;
import com.researchspace.documentconversion.spi.DocumentConversionService;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.record.BaseRecordAdaptable;
import com.researchspace.model.record.TestFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.BaseRecordManager;
import com.researchspace.service.FileDuplicateStrategy;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSigningManager;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockHttpServletResponse;

public class FileDownloadControllerTest {

  private static final int EXPECTED_FILE_SIZE = 100_000;

  @Rule public MockitoRule mockery = MockitoJUnit.rule();

  @Mock RecordManager rcdMger;
  @Mock UserManager userMgr;
  @Mock IPermissionUtils permUtils;
  @Mock BaseRecordAdaptable adapter;
  StaticMessageSource source;
  @Mock FileStore fileStore;
  @Mock DocumentConversionService converter;
  @Mock RecordSigningManager recordSigner;
  @Mock IPropertyHolder properties;
  @Mock AuditTrailService auditService;
  @Mock BaseRecordManager baseRecordMgr;

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private User user;
  private FileStoreRoot root;

  @InjectMocks private FileDownloadControllerTSS ctrller = new FileDownloadControllerTSS();
  private MockHttpServletResponse resp;

  class FileDownloadControllerTSS extends FileDownloadController {

    FileProperty generatedFileProperty;
    File anyOutfile;

    @Override
    protected FileProperty saveConvertedInFileStore(
        String outputformat, EcatMediaFile input, ConversionResult result, User user)
        throws IOException {
      FileProperty fp = super.saveConvertedInFileStore(outputformat, input, result, user);
      this.generatedFileProperty = fp;
      return fp;
    }

    @Override
    protected File createOutfile(String outputformat, String fName) throws IOException {
      return anyOutfile;
    }
  }

  @Before
  public void setUp() throws Exception {
    user = TestFactory.createAnyUser("any");
    resp = new MockHttpServletResponse();
    root = new FileStoreRoot(tempFolder.getRoot().toURI().toString());

    source = new StaticMessageSource();
    ctrller.setMessageSource(new MessageSourceUtils(source));
  }

  @Test
  public void testGetStreamFile() throws Exception {
    final Long ANYID = 1L;
    final EcatMediaFile mediaFile = getAnyMediaFile();

    File toStream = setupFileToConvert("pdf");
    FileProperty fp = setUpFileProperty(toStream);
    mediaFile.setFileProperty(fp);
    mockMediaFileLookup(mediaFile);

    mockInputStreamFromFileStore(toStream);
    mockGetUserFromSession();
    ctrller.getStreamFileNoName(ANYID, null, null, resp);
    verify(fileStore).retrieve(Mockito.any(FileProperty.class));
    verifyAuditServiceCalled();
  }

  private void mockMediaFileLookup(EcatMediaFile mediaFile) {

    when(baseRecordMgr.retrieveMediaFile(
            Mockito.any(User.class),
            Mockito.anyLong(),
            Mockito.any(),
            Mockito.any(),
            Mockito.any()))
        .thenReturn(mediaFile);
  }

  private void verifyAuditServiceCalled() {
    verify(auditService).notify(Mockito.any(GenericEvent.class));
  }

  private void mockInputStreamFromFileStore(File toStream) throws FileNotFoundException {
    final FileInputStream fis = new FileInputStream(toStream);
    when(fileStore.retrieve(Mockito.any(FileProperty.class))).thenReturn(Optional.of(fis));
  }

  @Test
  public void testGetStreamFileFromContainingDocPermissions() throws Exception {
    final Long ANYID = 1L;

    File toStream = setupFileToConvert("pdf");
    FileProperty fp = setUpFileProperty(toStream);
    final EcatMediaFile mediaFile = getAnyMediaFile();
    mediaFile.setFileProperty(fp);
    mockMediaFileLookup(mediaFile);
    final FileInputStream fis = new FileInputStream(toStream);
    when(fileStore.retrieve(Mockito.notNull(FileProperty.class))).thenReturn(Optional.of(fis));
    mockGetUserFromSession();
    ctrller.getStreamFileNoName(ANYID, null, null, resp);
    verify(fileStore).retrieve(Mockito.notNull(FileProperty.class));
    verifyAuditServiceCalled();
  }

  @Test
  public void testConvertFailureHandling() throws Exception {
    final EcatMediaFile mediaFile = getAnyMediaFile();
    mediaFile.setOwner(user);
    ctrller.anyOutfile = setupFileToConvert("pdf");
    mediaFile.setExtension("doc");
    mediaFile.setFileProperty(setUpFileProperty(ctrller.anyOutfile));
    final ConversionResult error = new ConversionResult("error");
    mockMediaFileLookup(mediaFile);
    when(converter.convert(
            Mockito.eq(new FileDownloadController.FileWrapper(fileStore, mediaFile)),
            Mockito.eq("xxx"),
            Mockito.eq(ctrller.anyOutfile)))
        .thenReturn(error);
    setupmocks();
    File fileToConvert = setupFileToConvert("xxx");
    mockFileInputStreamonFile(fileToConvert);

    AjaxReturnObject<String> rcError = ctrller.convertFile(mediaFile.getId(), "xxx", null, resp);
    assertNull(rcError.getData());
    assertEquals("error", rcError.getErrorMsg().getAllErrorMessagesAsStringsSeparatedBy(""));
  }

  @Test
  public void testNoConversionIfAlreadyExists() throws Exception {
    final EcatMediaFile mediaFile = getAnyMediaFile();
    mediaFile.setOwner(user);
    mediaFile.setExtension("doc");
    FileProperty fp = setUpFileProperty(setupFileToConvert("doc"));
    mediaFile.setFileProperty(fp);

    final File fileToConvert = setupFileToConvert("doc");

    mockGetUserFromSession();
    when(fileStore.exists(Mockito.any(FileProperty.class))).thenReturn(true);
    when(fileStore.findFile(Mockito.any(FileProperty.class))).thenReturn(fileToConvert);
    mockFileInputStreamonFile(fileToConvert);
    mockMediaFileLookup(mediaFile);
    mockEnableAsposeCaching();
    when(fileStore.getCurrentFileStoreRoot()).thenReturn(root);
    AjaxReturnObject<String> rcOk = ctrller.convertFile(mediaFile.getId(), "xxx", null, resp);
    verify(converter, never()).convert(mediaFile, "xxx", ctrller.anyOutfile);
    assertNull(rcOk.getErrorMsg());
    assertTrue(rcOk.getData().equals(fileToConvert.getName()));
  }

  private void mockFileInputStreamonFile(final File fileToConvert) throws FileNotFoundException {
    // some tests call this twice, need a new FIS each time
    when(fileStore.retrieve(Mockito.any(FileProperty.class)))
        .thenReturn(
            Optional.of(new FileInputStream(fileToConvert)),
            Optional.of(new FileInputStream(fileToConvert)));
  }

  private void mockEnableAsposeCaching() {
    when(properties.isAsposeCachingEnabled()).thenReturn(true);
  }

  private void mockGetUserFromSession() {
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(user);
  }

  private FileProperty setUpFileProperty(File file) throws IOException {
    FileProperty fp = new FileProperty();
    fp.setRoot(root);
    fp.setRelPath(file.getName());
    return fp;
  }

  @Test
  public void testInputFileReturnedIfInputEqualsOutput() throws Exception {
    final EcatMediaFile mediaFile = getAnyMediaFile();
    mediaFile.setOwner(user);
    mediaFile.setExtension("pdf");
    File toconvert = setupFileToConvert("pdf");
    FileProperty fp = setUpFileProperty(toconvert);
    mediaFile.setFileProperty(fp);
    mockMediaFileLookup(mediaFile);
    final ConversionResult success = new ConversionResult(toconvert, "application/pdf");
    ctrller.anyOutfile = toconvert;
    mockFileInputStreamonFile(toconvert);
    when(converter.convert(
            Mockito.eq(new FileDownloadController.FileWrapper(fileStore, mediaFile)),
            Mockito.eq("pdf"),
            Mockito.eq(ctrller.anyOutfile)))
        .thenReturn(success);
    setupmocks();

    AjaxReturnObject<String> rcOk = ctrller.convertFile(mediaFile.getId(), "pdf", null, resp);
    assertNull(rcOk.getErrorMsg());
    assertTrue(rcOk.getData().equals(toconvert.getName()));
    verify(fileStore, never())
        .save(
            Mockito.any(FileProperty.class),
            Mockito.any(File.class),
            Mockito.any(FileDuplicateStrategy.class));
    verify(fileStore, never()).findFile(Mockito.any(FileProperty.class));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testConversionExceptionThrowsAjaxError() throws Exception {
    final EcatMediaFile mediaFile = getAnyMediaFile();
    mediaFile.setOwner(user);
    ctrller.anyOutfile = File.createTempFile("any", ".pdf");
    mediaFile.setExtension("doc");

    File fileToConvert = setupFileToConvert("doc");
    FileProperty fp = setUpFileProperty(fileToConvert);
    mediaFile.setFileProperty(fp);
    setupmocks();
    mockMediaFileLookup(mediaFile);
    mockFileInputStreamonFile(fileToConvert);
    when(converter.convert(
            Mockito.eq(new FileDownloadController.FileWrapper(fileStore, mediaFile)),
            Mockito.eq("pdf"),
            Mockito.eq(ctrller.anyOutfile)))
        .thenThrow(new IllegalArgumentException());

    AjaxReturnObject<String> rcOk = ctrller.convertFile(mediaFile.getId(), "pdf", null, resp);
    assertNotNull(rcOk.getErrorMsg());
  }

  private void setupmocks() throws IOException {
    mockGetUserFromSession();
    when(fileStore.exists(Mockito.any(FileProperty.class))).thenReturn(false);
    when(fileStore.getCurrentFileStoreRoot()).thenReturn(root);
    mockEnableAsposeCaching();
  }

  @Test
  public void testConvertSuccessHandling() throws Exception {
    final EcatMediaFile mediaFile = getAnyMediaFile();
    mediaFile.setOwner(user);
    ctrller.anyOutfile = File.createTempFile("any", ".pdf");
    mediaFile.setExtension("doc");

    File fileToConvert = setupFileToConvert("doc");
    FileProperty fp = setUpFileProperty(fileToConvert);
    mediaFile.setFileProperty(fp);
    final ConversionResult success =
        new ConversionResult(File.createTempFile("abc", ".pdf"), "application/pdf");
    setupmocks();
    mockMediaFileLookup(mediaFile);
    mockFileInputStreamonFile(fileToConvert);
    when(converter.convert(
            Mockito.eq(new FileDownloadController.FileWrapper(fileStore, mediaFile)),
            Mockito.eq("pdf"),
            Mockito.eq(ctrller.anyOutfile)))
        .thenReturn(success);

    AjaxReturnObject<String> rcOk = ctrller.convertFile(mediaFile.getId(), "pdf", null, resp);
    assertNull(rcOk.getErrorMsg());
    assertTrue(rcOk.getData(), rcOk.getData().equals("1e50210a0202497fb79bc38b6ade6c34.pdf"));
    verify(fileStore)
        .save(
            Mockito.any(FileProperty.class),
            Mockito.any(File.class),
            Mockito.any(FileDuplicateStrategy.class));
  }

  @Test
  public void streamSignatureFile() throws IOException {
    mockGetUserFromSession();
    File any = RSpaceTestUtils.getAnyPdf();
    mockInputStreamFromFileStore(any);
    FileProperty fp = TestFactory.createAnyTransientFileProperty(user);
    final long SignatureId = 1L;
    final long filePropertyId = 2L;
    when(recordSigner.getSignedExport(SignatureId, user, filePropertyId))
        .thenReturn(Optional.of(fp));
    ctrller.streamFilePropertyDirect(SignatureId, filePropertyId, resp);
    resp.getOutputStream().flush();
    assertTrue(resp.getContentAsByteArray().length > EXPECTED_FILE_SIZE);
  }

  @Test(expected = IllegalStateException.class)
  public void streamSignatureFileThrowsIAEIfNotExists() throws IOException {
    mockGetUserFromSession();
    File any = RSpaceTestUtils.getAnyPdf();
    final long SignatureId = 1L;
    final long filePropertyId = 2L;
    source.addMessage("record.inaccessible", Locale.getDefault(), "any");
    when(recordSigner.getSignedExport(SignatureId, user, filePropertyId))
        .thenReturn(nullOptional());
    ctrller.streamFilePropertyDirect(SignatureId, filePropertyId, resp);
    // file store retreive not called if there is no FileProperty
    verify(fileStore, never()).retrieve(Mockito.any(FileProperty.class));
    assertEquals(0, resp.getContentAsByteArray().length);
  }

  // TODO Add this to base test class
  protected Optional<FileProperty> nullOptional() {
    return Optional.ofNullable(null);
  }

  private File setupFileToConvert(String ext) throws IOException {
    File originalURI = new File(tempFolder.getRoot(), "file." + ext);
    FileUtils.write(originalURI, "some data");
    return originalURI;
  }

  private EcatMediaFile getAnyMediaFile() {
    EcatDocumentFile docFile = new EcatDocumentFile();
    docFile.setContentType("any");
    docFile.setExtension("png");
    docFile.setId(1L);
    return docFile;
  }
}
