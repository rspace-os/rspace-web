package com.researchspace.webapp.integrations.jove;

import static com.researchspace.service.IntegrationsHandler.JOVE_APP_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.Constants;
import com.researchspace.integrations.jove.client.JoveClient;
import com.researchspace.integrations.jove.model.JoveArticle;
import com.researchspace.integrations.jove.model.JoveSearchRequest;
import com.researchspace.integrations.jove.model.JoveSearchResult;
import com.researchspace.model.User;
import com.researchspace.model.oauth.UserConnection;
import com.researchspace.model.oauth.UserConnectionId;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserConnectionManager;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.webapp.controller.MVCTestBase;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;

@WebAppConfiguration
@RunWith(ConditionalTestRunner.class)
public class JoveClientMVCIT extends MVCTestBase {

  private User testUser;

  @Autowired private JoveClient joveClient;

  @Autowired private UserConnectionManager userConnectionManager;

  @Autowired private IPropertyHolder propertyHolder;

  @Before
  public void startup() {
    testUser = createAndSaveUser(getRandomAlphabeticString("user"), Constants.USER_ROLE);
    initUsers(testUser);
    logoutAndLoginAs(testUser);
    // Set up user connection to generate access tokens for the Jove API
    UserConnection userConnection = new UserConnection();
    userConnection.setDisplayName("JoVE User Token");
    userConnection.setId(
        new UserConnectionId(testUser.getUsername(), JOVE_APP_NAME, JOVE_APP_NAME));
    userConnection.setRank(1);
    userConnection.setAccessToken(propertyHolder.getJoveApiKey());
    userConnection.setExpireTime(0L);
    userConnectionManager.save(userConnection);
  }

  // Test all parameters with search endpoint
  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testAllParametersSetOnSearch() throws URISyntaxException {
    JoveSearchRequest requestAllParams =
        new JoveSearchRequest("dna", "peter", "crick institute", 0L, 20L);
    JoveSearchResult result = joveClient.search(testUser, requestAllParams);
    assertNotNull(result.getArticleList());
    /* is expected to find https://app.jove.com/v/53840/amplification-next-generation-sequencing-and-genomic-dna-mapping-of-retroviral-integration-sites video */
    assertEquals(1, result.getArticleList().size());
  }

  // Test pagination getting multiple pages
  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testSearchPagination() throws URISyntaxException {
    // Get first page of results from search for dna query string, assert results not empty and page
    // size is 20
    JoveSearchRequest requestPageOne = new JoveSearchRequest("dna", "", "", 0L, 20L);
    JoveSearchResult pageOne = joveClient.search(testUser, requestPageOne);
    assertNotNull(pageOne.getArticleList());
    assertTrue(pageOne.getArticleList().size() > 0);
    assertEquals(20, pageOne.getArticleList().size());
    // Get second page of results from search for dna query string, assert results not empty and
    // page size is 20
    JoveSearchRequest requestPageTwo = new JoveSearchRequest("dna", "", "", 1L, 20L);
    JoveSearchResult pageTwo = joveClient.search(testUser, requestPageTwo);
    assertNotNull(pageTwo.getArticleList());
    assertTrue(pageTwo.getArticleList().size() > 0);
    assertEquals(20, pageTwo.getArticleList().size());
    // Assert results of two pages are not the same
    assertNotEquals(pageOne.getArticleList(), pageTwo.getArticleList());
  }

  // Test pagination setting page size
  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testSearchPageSize() throws URISyntaxException {
    // Get first page of results from search for dna query string, assert results not empty and page
    // size is 50
    JoveSearchRequest requestPageOne = new JoveSearchRequest("dna", "", "", 0L, 50L);
    JoveSearchResult pageOne = joveClient.search(testUser, requestPageOne);
    assertNotNull(pageOne.getArticleList());
    assertTrue(pageOne.getArticleList().size() > 0);
    assertEquals(50, pageOne.getArticleList().size());
  }

  // Test get article endpoint
  @Test
  @RunIfSystemPropertyDefined("nightly")
  public void testGetArticle() throws MalformedURLException, URISyntaxException {
    JoveArticle article = joveClient.getArticle(testUser, 60908L);
    assertEquals(60908L, (long) article.getId());
    assertNotNull(article.getEmbedUrl());
  }
}
