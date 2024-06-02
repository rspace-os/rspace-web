package com.researchspace.files.service;

import static com.researchspace.model.record.TestFactory.createAnyUser;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.record.TestFactory;
import com.researchspace.service.UserConnectionManager;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ExternalFileStoreLocatorImplTest {

  public @Rule MockitoRule mockito = MockitoJUnit.rule();

  @Mock UserConnectionManager userConnectionMgr;
  @Mock ExternalFileStore extFileStore;
  ExternalFileStoreLocatorImpl extFileLocator;
  User user;

  @Before
  public void setup() throws IOException {
    user = createAnyUser("user");
    extFileLocator =
        new ExternalFileStoreLocatorImpl(ExternalFileStoreProvider.EGNYTE, extFileStore);
    extFileLocator.setUserConnectionMgr(userConnectionMgr);
  }

  @Test
  public void getExternalFileStoreForUser() throws IOException {
    UserConnection userConnection = getUserConnectionForUser();
    setUpUserConnectionExists(userConnection);
    Optional<ExternalFileStoreWithCredentials> efs =
        extFileLocator.getExternalFileStoreForUser(user.getUsername());
    assertTrue(efs.isPresent());
    assertEquals(userConnection, efs.get().getUserConnection());
    assertEquals(extFileStore, efs.get().getExtFileStore());
  }

  @Test
  public void getExternalFileStoreForUserREturnsEmptyIfNoUserConnection() throws IOException {
    UserConnection uc = getUserConnectionForUser();
    setUpUserConnectionNotExists(uc);
    Optional<ExternalFileStoreWithCredentials> efs =
        extFileLocator.getExternalFileStoreForUser(user.getUsername());
    assertFalse(efs.isPresent());
  }

  private UserConnection getUserConnectionForUser() {
    return TestFactory.createUserConnection(user.getUsername());
  }

  private void setUpUserConnectionNotExists(UserConnection uc) {
    when(userConnectionMgr.findByUserNameProviderName(
            user.getUsername(), ExternalFileStoreProvider.EGNYTE.name()))
        .thenReturn(Optional.empty());
  }

  private void setUpUserConnectionExists(UserConnection uc) {
    when(userConnectionMgr.findByUserNameProviderName(
            user.getUsername(), ExternalFileStoreProvider.EGNYTE.name()))
        .thenReturn(Optional.of(uc));
  }
}
