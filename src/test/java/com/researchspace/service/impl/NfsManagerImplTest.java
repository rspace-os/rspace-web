package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.dao.NfsDao;
import com.researchspace.model.User;
import com.researchspace.model.netfiles.NfsAuthenticationType;
import com.researchspace.model.netfiles.NfsClientType;
import com.researchspace.model.netfiles.NfsFileSystem;
import com.researchspace.netfiles.NfsClient;
import com.researchspace.netfiles.NfsFactory;
import com.researchspace.netfiles.NfsRSpaceProvidedAuthentication;
import com.researchspace.netfiles.s3.AwsS3Client;
import com.researchspace.service.aws.S3Utilities;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link NfsManagerImpl}, focusing on auto-login behaviour for S3 file systems.
 *
 * <p>S3 file systems use {@link NfsAuthenticationType#NONE} and should auto-authenticate on the
 * first call without requiring manual login.
 */
public class NfsManagerImplTest {

  @Mock private NfsDao nfsDao;
  @Mock private NfsFactory nfsFactory;

  @InjectMocks private NfsManagerImpl nfsManager;

  private User testUser;
  private NfsFileSystem s3FileSystem;
  private static final Long FILE_SYSTEM_ID = 1L;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    testUser = new User("testUser");

    s3FileSystem = new NfsFileSystem();
    s3FileSystem.setId(FILE_SYSTEM_ID);
    s3FileSystem.setName("Test S3");
    s3FileSystem.setClientType(NfsClientType.S3);
    s3FileSystem.setAuthType(NfsAuthenticationType.NONE);

    when(nfsDao.getNfsFileSystem(FILE_SYSTEM_ID)).thenReturn(s3FileSystem);
  }

  @Test
  public void s3FileSystem_autoAuthenticatesOnFirstCall() {
    S3Utilities s3Utilities = mock(S3Utilities.class);
    // S3 uses global credentials, so username is null → displayed as "(unknown)"
    AwsS3Client s3Client = new AwsS3Client(null, s3Utilities);

    NfsRSpaceProvidedAuthentication auth = mock(NfsRSpaceProvidedAuthentication.class);
    when(auth.validateCredentials(any(), any(), any())).thenReturn(null);
    when(auth.login(any(), any(), eq(s3FileSystem), eq(testUser))).thenReturn(s3Client);

    when(nfsFactory.getNfsAuthentication(s3FileSystem)).thenReturn(auth);

    Map<Long, NfsClient> nfsClients = new HashMap<>();

    // First call — should auto-authenticate and return "(unknown)" for S3 global credentials
    String loggedAs =
        nfsManager.getLoggedAsUsernameIfUserLoggedIn(FILE_SYSTEM_ID, nfsClients, testUser);

    assertNotNull(loggedAs, "S3 should auto-authenticate on first call");
    assertEquals("(unknown)", loggedAs);
    assertNotNull(nfsClients.get(FILE_SYSTEM_ID), "S3 client should be stored in session map");
  }

  @Test
  public void s3FileSystem_returnsNullForDisabledFileSystem() {
    NfsFileSystem disabled = new NfsFileSystem();
    disabled.setId(2L);
    disabled.setDisabled(true);
    when(nfsDao.getNfsFileSystem(2L)).thenReturn(disabled);

    Map<Long, NfsClient> nfsClients = new HashMap<>();

    String loggedAs = nfsManager.getLoggedAsUsernameIfUserLoggedIn(2L, nfsClients, testUser);

    assertNull(loggedAs, "disabled filesystem should return null");
  }

  @Test
  public void s3FileSystem_returnsUsernameOnSubsequentCalls() {
    S3Utilities s3Utilities = mock(S3Utilities.class);
    AwsS3Client s3Client = new AwsS3Client(null, s3Utilities);

    // Pre-populate the session map (simulating a previous successful login)
    Map<Long, NfsClient> nfsClients = new HashMap<>();
    nfsClients.put(FILE_SYSTEM_ID, s3Client);

    String loggedAs =
        nfsManager.getLoggedAsUsernameIfUserLoggedIn(FILE_SYSTEM_ID, nfsClients, testUser);

    assertEquals("(unknown)", loggedAs);
  }
}
