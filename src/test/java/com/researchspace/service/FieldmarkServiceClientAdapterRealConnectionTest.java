package com.researchspace.service;

import static com.researchspace.service.IntegrationsHandler.FIELDMARK_APP_NAME;
import static org.junit.Assert.assertNotNull;

import com.researchspace.fieldmark.model.FieldmarkNotebook;
import com.researchspace.model.User;
import com.researchspace.model.dtos.fieldmark.FieldmarkNotebookDTO;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.service.fieldmark.FieldmarkServiceClientAdapter;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

@Ignore("We leave the test Disabled so we can potentially run it manually by the bearer token")
public class FieldmarkServiceClientAdapterRealConnectionTest extends SpringTransactionalTest {

  @Autowired private FieldmarkServiceClientAdapter fieldmarkServiceClientAdapter;

  @Autowired private UserConnectionManager userConnectionManager;

  private User user = null;
  private static final String NOTEBOOK_ID = "1726126204618-rspace-igsn-demo";
  private static final String ACCESS_TOKEN = "_______PASTE_TOKEN_HERE________";

  @Before
  public void setUp() throws IllegalAddChildOperation {
    user = createAndSaveRandomUser();
    UserConnection actualConnection = new UserConnection();
    actualConnection.setId(
        new UserConnectionId(user.getUsername(), FIELDMARK_APP_NAME, "ProviderUserIdNotNeeded"));
    actualConnection.setAccessToken(ACCESS_TOKEN);
    actualConnection.setRefreshToken("REFRESH_TOKEN");
    actualConnection.setExpireTime(299L);
    actualConnection.setDisplayName("DMPonline access token");
    userConnectionManager.save(actualConnection);
  }

  @Test
  public void testGetNotebookList() throws MalformedURLException, URISyntaxException {
    List<FieldmarkNotebook> notebookList =
        fieldmarkServiceClientAdapter.getFieldmarkNotebookList(user);
    assertNotNull(notebookList);
  }

  @Test
  public void testImportNotebook() throws IOException {
    FieldmarkNotebookDTO notebookList =
        fieldmarkServiceClientAdapter.getFieldmarkNotebook(user, NOTEBOOK_ID);

    assertNotNull(notebookList);
  }
}
