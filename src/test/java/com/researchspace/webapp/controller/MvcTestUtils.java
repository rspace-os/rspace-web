package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.researchspace.api.v1.model.NewOAuthTokenResponse;
import com.researchspace.model.EditStatus;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@Component("mvcTestUtils")
public class MvcTestUtils {

  /**
   * Mimics edit lock request, autosave and save, and unlock behaviour from editor via MVC
   * /controller test.
   *
   * @param toSave - the field we're saving.
   * @param newFieldContent - this will replace existing field content.
   * @param subject - logged in user
   * @param mvcTest
   * @throws Exception
   */
  public void doAutosaveAndSaveMVC(
      Field toSave, String newFieldContent, final User subject, MockMvc mvcTest) throws Exception {
    Principal p = requestEditAndAutosave(toSave, newFieldContent, subject, mvcTest);
    mvcTest
        .perform(
            post("/workspace/editor/structuredDocument/ajax/saveStructuredDocument")
                .param("unlock", Boolean.TRUE.toString())
                .param("structuredDocumentId", toSave.getStructuredDocument().getId() + "")
                .principal(p))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
  }

  /**
   * Mimics edit lock request, autosave and save, and unlock behaviour from editor via MVC
   * /controller test.
   *
   * @param toSave - the field we're saving.
   * @param newFieldContent - this will replace existing field content.
   * @param subject - logged in user
   * @param mvcTest
   * @throws Exception
   */
  public void doAutosaveAndCancelMVC(
      Field toSave, String newFieldContent, final User subject, MockMvc mvcTest) throws Exception {
    Principal p = requestEditAndAutosave(toSave, newFieldContent, subject, mvcTest);
    cancelAutosave(toSave.getStructuredDocument(), p, mvcTest);
  }

  /**
   * Performs cancel autosavedEdits operation
   *
   * @param toSave
   * @param mvcTest
   * @param p
   * @throws Exception
   */
  public void cancelAutosave(StructuredDocument toSave, Principal p, MockMvc mvcTest)
      throws Exception {
    mvcTest
        .perform(
            post("/workspace/editor/structuredDocument/ajax/cancelAutosavedEdits")
                .param("structuredDocumentId", toSave.getId() + "")
                .principal(p))
        .andExpect(status().is2xxSuccessful())
        .andReturn();
  }

  public Principal requestEditAndAutosave(
      Field toSave, String newFieldContent, final User subject, MockMvc mvcTest)
      throws Exception, UnsupportedEncodingException {

    Principal principal = subject::getUsername;
    // request edit
    MvcResult result =
        mvcTest
            .perform(
                post("/workspace/editor/structuredDocument/ajax/requestEdit")
                    .param("recordId", toSave.getStructuredDocument().getId() + "")
                    .principal(principal))
            .andReturn();
    assertEquals(
        "\"" + EditStatus.EDIT_MODE.toString() + "\"", result.getResponse().getContentAsString());

    // autosve
    result =
        mvcTest
            .perform(
                post("/workspace/editor/structuredDocument/ajax/autosaveField")
                    .param("dataValue", newFieldContent)
                    .param("fieldId", toSave.getId() + "")
                    .principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    Map json = parseJSONObjectFromResponseStream(result);
    assertTrue(Boolean.parseBoolean(json.get("data").toString()));
    return principal;
  }

  /**
   * For controller methods returning JSON direct to the response stream, parses this response into
   * a map for asserting the data contents
   *
   * @param result
   * @return A Map of key value pairs
   * @throws IOException
   * @throws JsonProcessingException
   * @throws ParseException
   */
  public Map parseJSONObjectFromResponseStream(MvcResult result)
      throws JsonProcessingException, IOException {
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node =
        mapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    Map<String, Object> rc = mapper.convertValue(node, Map.class);
    return rc;
  }

  /**
   * When we're expecting an {@link AuthorizationException} or the more general {@link
   * RecordAccessDeniedException} thrown from a controller method in an MVC test
   *
   * @param result The MvcResult
   */
  public void assertAuthorizationException(MvcResult result) {
    assertTrue(
        "exception was " + result.getResolvedException(),
        (result.getResolvedException() instanceof AuthorizationException)
            || (result.getResolvedException() instanceof RecordAccessDeniedException));
  }

  public void assertException(MvcResult result, Class<? extends Exception> clazz) {
    assertTrue(
        "exception was " + result.getResolvedException(),
        result.getResolvedException().getClass().isAssignableFrom(clazz));
  }

  public <T> T getFromJsonAjaxReturnObject(MvcResult result, Class<T> clazz) throws Exception {
    assertNoServerSideException(result);
    String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(json);
    JsonNode data = node.get("data");
    return (mapper.readValue(mapper.writeValueAsString(data), clazz));
  }

  public ErrorList getErrorListFromAjaxReturnObject(MvcResult result) throws Exception {
    assertNoServerSideException(result);
    String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    ObjectMapper mapper = new ObjectMapper();
    JsonNode node = mapper.readTree(json);
    JsonNode error = node.get("errorMsg");
    if (error == null) {
      return null;
    }
    return (mapper.readValue(mapper.writeValueAsString(error), ErrorList.class));
  }

  public <T> T getFromJsonResponseBody(MvcResult result, Class<T> clazz) throws Exception {
    assertNoServerSideException(result);
    String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    return (new ObjectMapper()).readValue(content, clazz);
  }

  /**
   * Used to extract a list of objects from a JSON list structure
   *
   * @param <T>
   * @param result
   * @param typeRef
   * @return
   * @throws Exception
   */
  public <T> List<T> getFromJsonResponseBodyByTypeRef(
      MvcResult result, TypeReference<List<T>> typeRef) throws Exception {
    assertNoServerSideException(result);
    String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    return (new ObjectMapper()).readValue(content, typeRef);
  }

  public String getAsJsonString(Object object) throws JsonProcessingException {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.writeValueAsString(object);
  }

  void assertNoServerSideException(MvcResult result) throws Exception {
    if (result.getResolvedException() != null) {
      throw result.getResolvedException();
    }
  }

  /**
   * Parses JSON response returned after getting an OAuth token for using the API
   *
   * @param jsonData json response from server
   */
  public static NewOAuthTokenResponse parseOAuthTokenResponse(String jsonData) throws IOException {
    NewOAuthTokenResponse response = new NewOAuthTokenResponse();
    JsonNode root = (new ObjectMapper()).readTree(new JsonFactory().createParser(jsonData));
    Optional.ofNullable(root.get("access_token"))
        .ifPresent(jsonNode -> response.setAccessToken(jsonNode.textValue()));
    Optional.ofNullable(root.get("refresh_token"))
        .ifPresent(jsonNode -> response.setRefreshToken(jsonNode.textValue()));
    Optional.ofNullable(root.get("scope"))
        .ifPresent(jsonNode -> response.setScope(jsonNode.textValue()));
    return response;
  }
}
