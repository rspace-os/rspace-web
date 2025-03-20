package com.researchspace.webapp.integrations.snapgene;

import static com.researchspace.core.testutil.CoreTestUtils.assertExceptionThrown;
import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static com.researchspace.model.preference.HierarchicalPermission.ALLOWED;
import static com.researchspace.model.preference.HierarchicalPermission.DENIED;
import static com.researchspace.model.preference.HierarchicalPermission.DENIED_BY_DEFAULT;
import static com.researchspace.model.record.TestFactory.createAFileProperty;
import static com.researchspace.service.SystemPropertyName.SNAPGENE_AVAILABLE;
import static org.junit.Assert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.apiutils.ApiError;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.files.service.FileStore;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.FileStoreRoot;
import com.researchspace.model.User;
import com.researchspace.model.permissions.IPermissionUtils;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.system.SystemPropertyValue;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordManager;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.UserManager;
import com.researchspace.snapgene.wclient.SnapgeneWSClient;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.zmq.snapgene.requests.GeneratePngMapConfig;
import io.vavr.collection.List;
import io.vavr.control.Either;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.apache.shiro.authz.AuthorizationException;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class DNAViewerControllerTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock FileStore fileStore;
  @Mock UserManager userManager;
  @Mock SnapgeneWSClient wsClient;
  @Mock RecordManager rcdMgr;
  @Mock IPermissionUtils perms;
  @Mock MessageSourceUtils messages;
  @Mock SystemPropertyManager systemPropertyManagerImpl;
  @Mock SystemPropertyValue isSnapgeneAllowed;
  @InjectMocks DNAViewerController dnaController;

  File someGenbankFile = RSpaceTestUtils.getAnyGenbankFile();
  User user = TestFactory.createAnyUser("any");
  EcatDocumentFile edf = TestFactory.createEcatDocument(1L, user);
  FileProperty fp = createAFileProperty(someGenbankFile, user, new FileStoreRoot("/"));

  @Before
  public void before() {
    edf.setExtension("gb");
    when(systemPropertyManagerImpl.findByName(SNAPGENE_AVAILABLE)).thenReturn(isSnapgeneAllowed);
    when(isSnapgeneAllowed.getValue()).thenReturn(ALLOWED.name());
  }

  @Test
  public void successScenario() throws IOException {
    setupMocks();
    mockSuccessfulSnapgeneCall();
    ResponseEntity<byte[]> bytes =
        dnaController.getPngView(1L, GeneratePngMapConfig.builder().build());
    assertEquals(3, bytes.getBody().length);
    assertEquals(200, bytes.getStatusCodeValue());
  }

  @Test
  public void snapGeneFailsScenario() throws IOException {
    setupMocks();
    mockErrorSnapgeneCall(HttpStatus.BAD_REQUEST);
    ResponseEntity<byte[]> bytes =
        dnaController.getPngView(1L, GeneratePngMapConfig.builder().build());
    assertThat(
        new String(bytes.getBody(), "UTF-8"),
        Matchers.startsWith("Snapgene webservice call failed"));
    assertEquals(HttpStatus.BAD_REQUEST.value(), bytes.getStatusCodeValue());
  }

  @Test
  public void statusFails() throws IOException {
    mockErrorSnapgeneStatus(HttpStatus.NOT_FOUND);
    ResponseEntity<String> bytes = dnaController.status();
    assertThat(bytes.getBody(), Matchers.startsWith("Snapgene webservice call failed"));
    assertEquals(HttpStatus.NOT_FOUND.value(), bytes.getStatusCodeValue());
  }

  @Test
  public void statusSucceeds() throws IOException {
    mockSuccessfulSnapgeneStatus(HttpStatus.OK);
    ResponseEntity<String> bytes = dnaController.status();
    assertEquals("{\"json\": \"someData\"}", bytes.getBody());
    assertEquals(200, bytes.getStatusCodeValue());
  }

  @Test
  public void testEmptyErrorApiSucceedsWithoutThrowingException() {
    when(wsClient.status()).thenReturn(Either.left(null));
    ResponseEntity<String> bytes = dnaController.status();
    verify(wsClient).status();
    assertEquals("Snapgene webservice call failed", bytes.getBody());
    assertEquals(HttpStatus.FAILED_DEPENDENCY.value(), bytes.getStatusCodeValue());
  }

  @Test
  public void testWhenSnapgeneDeniedDoesNotAskForStatus() {
    when(isSnapgeneAllowed.getValue()).thenReturn(DENIED.name());
    ResponseEntity<String> bytes = dnaController.status();
    verifyNoInteractions(wsClient);
    assertEquals("Snapgene not allowed", bytes.getBody());
    assertEquals(200, bytes.getStatusCodeValue());
  }

  @Test
  public void testWhenSnapgeneDeniedByDefaultDoesNotAskForStatus() {
    when(isSnapgeneAllowed.getValue()).thenReturn(DENIED_BY_DEFAULT.name());
    ResponseEntity<String> bytes = dnaController.status();
    verifyNoInteractions(wsClient);
    assertEquals("Snapgene not allowed", bytes.getBody());
    assertEquals(HttpStatus.OK.value(), bytes.getStatusCodeValue());
  }

  @Test
  public void permissionFailureOccursBeforeWSCall() throws Exception {
    setupMocks();
    when(perms.isRecordAccessPermitted(user, edf, PermissionType.READ)).thenReturn(false);
    assertExceptionThrown(
        () -> dnaController.getPngView(1L, GeneratePngMapConfig.builder().build()),
        AuthorizationException.class);
    verifyNoInteractions(wsClient);
  }

  @Test
  public void rejectTooBigFileBeforeWsCall() throws Exception {
    setupMocks();
    edf.setSize(DNAViewerController.MAX_SNAPGENE_FILE_SIZE + 1);
    CoreTestUtils.assertIllegalArgumentException(
        () -> dnaController.getPngView(1L, GeneratePngMapConfig.builder().build()));
    verifyNoInteractions(wsClient);
  }

  @Test
  public void rejectUnsupportedFileTypeBeforeWsCall() throws Exception {
    setupMocks();
    edf.setExtension("xyzz");
    assertIllegalArgumentException(
        () -> dnaController.getPngView(1L, GeneratePngMapConfig.builder().build()));
    verifyNoInteractions(wsClient);
  }

  private void setupMocks() throws IOException {
    edf.setFileProperty(fp);
    when(userManager.getAuthenticatedUserInSession()).thenReturn(user);
    when(fileStore.findFile(Mockito.eq(fp))).thenReturn(someGenbankFile);
    when(rcdMgr.get(1L)).thenReturn(edf);
    when(perms.isRecordAccessPermitted(user, edf, PermissionType.READ)).thenReturn(true);
  }

  private void mockSuccessfulSnapgeneCall() throws FileNotFoundException, IOException {
    when(wsClient.uploadAndDownloadPng(
            Mockito.eq(someGenbankFile), Mockito.any(GeneratePngMapConfig.class)))
        .thenReturn(Either.right(new byte[] {1, 2, 3}));
  }

  private void mockErrorSnapgeneCall(HttpStatus failure) throws FileNotFoundException, IOException {
    when(wsClient.uploadAndDownloadPng(
            Mockito.eq(someGenbankFile), Mockito.any(GeneratePngMapConfig.class)))
        .thenReturn(
            Either.left(new ApiError(failure, 1, "error", List.of("exceptions").toJavaList())));
  }

  private void mockErrorSnapgeneStatus(HttpStatus failure)
      throws FileNotFoundException, IOException {
    when(wsClient.status())
        .thenReturn(
            Either.left(new ApiError(failure, 1, "error", List.of("exceptions").toJavaList())));
  }

  private void mockSuccessfulSnapgeneStatus(HttpStatus failure)
      throws FileNotFoundException, IOException {
    when(wsClient.status()).thenReturn(Either.right("{\"json\": \"someData\"}"));
  }
}
