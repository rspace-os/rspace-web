package com.researchspace.webapp.integrations.jove;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.integrations.jove.client.JoveClient;
import com.researchspace.integrations.jove.model.JoveArticle;
import com.researchspace.integrations.jove.model.JoveSearchRequest;
import com.researchspace.integrations.jove.model.JoveSearchResult;
import com.researchspace.model.User;
import com.researchspace.service.UserManager;
import com.researchspace.testutils.RSpaceTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

class JoveControllerTest {

  @Mock private JoveClient joveClient;
  @Mock private UserManager userMgr;
  @InjectMocks private JoveController joveController;
  @Mock private User testUser;

  ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  public void setup() {
    openMocks(this);
  }

  @Test
  void testJoveSearch() throws Exception {
    JoveSearchRequest request = new JoveSearchRequest("dna", "peter", "harvard", 1L, 20L);
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(testUser);
    JoveSearchResult searchResult =
        objectMapper.readValue(
            RSpaceTestUtils.getResource("/integrations/jove/allParamsSearchResults.json"),
            JoveSearchResult.class);
    when(joveClient.search(testUser, request)).thenReturn(searchResult);
    JoveSearchResult result = joveController.search(request);
    assertNotNull(result);
    assertTrue(result.getCountAll() > 0);
    assertFalse(result.getArticleList().isEmpty());
  }

  @Test
  void testJoveArticle() throws Exception {
    JoveSearchRequest request = new JoveSearchRequest("dna", "peter", "harvard", 1L, 20L);
    when(userMgr.getAuthenticatedUserInSession()).thenReturn(testUser);
    JoveArticle joveArticle =
        objectMapper.readValue(
            RSpaceTestUtils.getResource("/integrations/jove/articleFullAccessResult.json"),
            JoveArticle.class);
    when(joveClient.getArticle(testUser, 1L)).thenReturn(joveArticle);
    JoveArticle result = joveController.article(1L);
    assertNotNull(result);
    assertEquals("full access", result.getAccessType());
    assertNotNull(result.getText());
  }
}
