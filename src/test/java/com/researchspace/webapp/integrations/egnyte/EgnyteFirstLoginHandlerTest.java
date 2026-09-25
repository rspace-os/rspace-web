package com.researchspace.webapp.integrations.egnyte;

import static com.researchspace.testutils.TestFactory.createAnyUser;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.researchspace.files.service.ExternalFileStoreProvider;
import com.researchspace.model.User;
import com.researchspace.properties.IPropertyHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;

@ExtendWith(MockitoExtension.class)
public class EgnyteFirstLoginHandlerTest {

  static class EgnyteAuthConnectorImplTSS extends EgnyteAuthConnectorImpl {

    boolean isWorking = false;

    public boolean isEgnyteConnectionSetupAndWorking(String username) {
      return isWorking;
    }
  }

  private @Mock IPropertyHolder propHolder;
  @InjectMocks private EgnyteAuthConnectorImplTSS egnyteConnector;
  private MockHttpSession mockSession;
  User any;

  @BeforeEach
  public void setUp() throws Exception {
    Mockito.when(propHolder.getFileStoreType()).thenReturn("LOCAL");
    mockSession = new MockHttpSession();
    any = createAnyUser("any");
    any.setId(1L);
  }

  @Test
  public void firstLoginInvocationRequiresEgnyteFileStoreDefined() throws Exception {
    assertFalse(any.isContentInitialized());

    assertNull(egnyteConnector.onFirstLoginBeforeContentInitialisation(any, mockSession));
    assertNull(egnyteConnector.onFirstLoginAfterContentInitialisation(any, mockSession, null));
  }

  @Test
  public void firstLoginAndEgnyteRedirect() throws Exception {
    // let's mock prop holder to show Egnyte used as filestore
    mockEgnyteFileStore();

    // let's create a new user, not initalized yet
    assertFalse(any.isContentInitialized());

    // first call
    String egnyteNotConfiguredRedirect =
        egnyteConnector.onFirstLoginBeforeContentInitialisation(any, mockSession);
    assertEquals("/egnyte/egnyteConnectionSetup", egnyteNotConfiguredRedirect);

    // second call in a session should no longer redirect to setup page, just
    // continue
    String secondCallRedirect =
        egnyteConnector.onFirstLoginBeforeContentInitialisation(any, mockSession);
    assertNull(secondCallRedirect);
  }

  @Test
  public void noRedirectIfConnectionAlreadySetup() throws Exception {
    egnyteConnector.isWorking = true;
    mockEgnyteFileStore();
    assertNull(egnyteConnector.onFirstLoginBeforeContentInitialisation(any, mockSession));
  }

  private void mockEgnyteFileStore() {
    when(propHolder.getFileStoreType()).thenReturn(ExternalFileStoreProvider.EGNYTE.name());
  }
}
