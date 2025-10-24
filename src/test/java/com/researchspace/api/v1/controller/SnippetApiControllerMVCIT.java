package com.researchspace.api.v1.controller;

import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiSnippet;
import com.researchspace.model.User;
import com.researchspace.model.record.Snippet;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class SnippetApiControllerMVCIT extends API_MVC_TestBase {

  @Test
  public void getSnippetContentSuccess() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);

    String content = "some content";
    Snippet snippet = recordMgr.createSnippet("Test Snippet", content, anyUser);

    mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, apiKey, "/snippet/{id}/content", anyUser, snippet.getId()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
        .andExpect(content().string(content))
        .andReturn();
  }

  @Test
  public void getSnippetContentWhenSnippetNotFoundThenReturn404() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    long nonExistentId = 987654321L;

    mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, apiKey, "/snippet/{id}/content", anyUser, nonExistentId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value(makeNotFoundOrUnauthorizedMessage(nonExistentId)))
        .andReturn();
  }

  @Test
  public void getSnippetContentWhenUserNotAuthorisedThenReturn404() throws Exception {
    User owner = createInitAndLoginAnyUser();
    Snippet snippet = recordMgr.createSnippet("Test Snippet", "some content", owner);

    // different user tries to access it via API
    User otherUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(otherUser);

    mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, apiKey, "/snippet/{id}/content", otherUser, snippet.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value(makeNotFoundOrUnauthorizedMessage(snippet.getId())))
        .andReturn();
  }

  @Test
  public void getSnippetByIdSuccess() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    String name = "Test Snippet";
    String content = "some content";

    Snippet snippet = recordMgr.createSnippet(name, content, anyUser);

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, "/snippet/{id}", anyUser, snippet.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

    ApiSnippet apiSnippet = getFromJsonResponseBody(result, ApiSnippet.class);
    assertEquals(snippet.getId(), apiSnippet.getId());
    assertEquals(name, apiSnippet.getName());
    assertEquals(content, apiSnippet.getContent());
    assertEquals(anyUser.getId(), apiSnippet.getOwner().getId());
  }

  @Test
  public void getSnippetByIdWhenSnippetDoesntExistThenThrows404() throws Exception {
    User anyUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(anyUser);
    long nonExistentId = 987654321L;
    this.mockMvc
        .perform(
            createBuilderForGet(API_VERSION.ONE, apiKey, "/snippet/{id}", anyUser, nonExistentId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value(makeNotFoundOrUnauthorizedMessage(nonExistentId)))
        .andReturn();
  }

  @Test
  public void getSnippetByIdWhenNoPermissionsReturns404() throws Exception {
    User owner = createInitAndLoginAnyUser();
    Snippet snippet = recordMgr.createSnippet("Test", "some content", owner);

    // different user tries to access it via API
    User otherUser = createInitAndLoginAnyUser();
    String apiKey = createNewApiKeyForUser(otherUser);

    this.mockMvc
        .perform(
            createBuilderForGet(
                API_VERSION.ONE, apiKey, "/snippet/{id}", otherUser, snippet.getId()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value(makeNotFoundOrUnauthorizedMessage(snippet.getId())))
        .andReturn();
  }

  private String makeNotFoundOrUnauthorizedMessage(long id) {
    return "Snippet with id ["
        + id
        + "] could not be retrieved - possibly it has been deleted, does not exist, or you do not"
        + " have permission to access it.";
  }
}
