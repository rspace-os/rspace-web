package com.researchspace.webapp.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.researchspace.api.v1.controller.API_VERSION;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.model.User;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base class for Spring MVC tests, which are the most extensive integration tests, as they cover
 * the full Spring web stack from request parameter handling, through to view resolution. This class
 * contains some utility functions that are reusable across tests.<br>
 * It also abstracts the setup mechanism, making the the mockMVc object available to subclasses.
 *
 * <p>Subclasses just need to call super.setUp() from their setup method.
 */
@WebAppConfiguration
// add this configuration file, which enables configuration of Beans that require a
// WebApplicationContext to run
@ContextConfiguration(locations = "classpath:dispatcher-test-servlet.xml")
public abstract class MVCTestBase extends RealTransactionSpringTestBase {

  protected @Autowired WebApplicationContext wac;

  protected MockMvc mockMvc;

  @Before
  public void setUp() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    super.setUp();
  }

  @Autowired protected MvcTestUtils mvcUtils;

  @Autowired protected SystemPropertyManager sysPropMgr;

  public void disableGlobalApiAccess() {
    sysPropMgr.save(
        SystemPropertyName.API_AVAILABLE, HierarchicalPermission.DENIED, getSysAdminUser());
  }

  public void enableGlobalApiAccess() {
    sysPropMgr.save(
        SystemPropertyName.API_AVAILABLE, HierarchicalPermission.ALLOWED, getSysAdminUser());
  }

  public void disableApiOAuthAuthentication() {
    sysPropMgr.save(
        SystemPropertyName.API_OAUTH_AUTHENTICATION,
        HierarchicalPermission.DENIED,
        getSysAdminUser());
  }

  public void enableApiOAuthAuthentication() {
    sysPropMgr.save(
        SystemPropertyName.API_OAUTH_AUTHENTICATION,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());
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
  protected void doAutosaveAndSaveMVC(Field toSave, String newFieldContent, User subject)
      throws Exception {
    mvcUtils.doAutosaveAndSaveMVC(toSave, newFieldContent, subject, mockMvc);
  }

  /**
   * Mimics edit lock request, autosave and cancel, and unlock behaviour from editor via MVC
   * /controller test.
   *
   * @param toSave - the field we're autosaving then cancelling.
   * @param newFieldContent - this will replace existing field content.
   * @param subject - logged in user
   * @param mvcTest
   * @throws Exception
   */
  protected void doAutosaveAndCancelMVC(Field toSave, String newFieldContent, User subject)
      throws Exception {
    mvcUtils.doAutosaveAndCancelMVC(toSave, newFieldContent, subject, mockMvc);
  }

  protected void cancelAutosave(Field toSave, User subject) throws Exception {
    mvcUtils.cancelAutosave(toSave.getStructuredDocument(), new MockPrincipal(subject), mockMvc);
  }

  /**
   * Adds new content and autosaves
   *
   * @param toSave
   * @param newFieldContent
   * @param subject
   * @throws Exception
   */
  protected void doAutosaveMVC(Field toSave, String newFieldContent, User subject)
      throws Exception {
    mvcUtils.requestEditAndAutosave(toSave, newFieldContent, subject, mockMvc);
  }

  /**
   * For controller methods returning JSON direct to the response stream, parses this response into
   * a map for asserting the data contents
   *
   * @param result
   * @return A Map of key value pairs
   * @throws ParseException
   * @throws IOException
   * @throws JsonProcessingException
   */
  protected Map parseJSONObjectFromResponseStream(MvcResult result)
      throws JsonProcessingException, IOException {
    return mvcUtils.parseJSONObjectFromResponseStream(result);
  }

  /**
   * When we're expecting an {@link AuthorizationException} or the more general {@link
   * RecordAccessDeniedException} thrown from a controller method in an MVC test
   *
   * @param result The MvcResult
   * @throws ParseException
   * @throws UnsupportedEncodingException
   */
  protected void assertAuthorizationException(MvcResult result)
      throws UnsupportedEncodingException {
    mvcUtils.assertAuthorizationException(result);
  }

  /**
   * Assert exception of a particular tupe is thrown
   *
   * @param result
   * @param clazz
   */
  protected void assertException(MvcResult result, Class<? extends Exception> clazz) {
    mvcUtils.assertException(result, clazz);
  }

  /**
   * Given a JSON MvcResult containing AjaxReturnObject, will attempt to parse its <code>data</code>
   * property into a Java object, using Jackson's default {@link ObjectMapper}
   *
   * @param result
   * @param clazz The class you want parsed back
   * @return
   * @throws Exception JSON parsing, or server side exception
   */
  protected <T> T getFromJsonAjaxReturnObject(MvcResult result, Class<T> clazz) throws Exception {
    return mvcUtils.getFromJsonAjaxReturnObject(result, clazz);
  }

  /**
   * Given a json path, queries response. Assumes response is indeed a json request body
   *
   * @see https://support.smartbear.com/alertsite/docs/monitors/api/endpoint/jsonpath.html#filters
   *     for JsonPath syntax
   */
  protected Object getJsonPathValue(MvcResult result, String jsonPath) throws Exception {
    mvcUtils.assertNoServerSideException(result);
    String json = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    DocumentContext context = JsonPath.parse(json);
    return context.read(jsonPath);
  }

  /**
   * Given a JSON MvcResult containing {@link AjaxReturnObject}, will attempt to parse it and
   * retrieve ErrorList (which might be null)
   *
   * @param result ErrorList (or null if no ErrorList in {@link AjaxReturnObject}
   * @return
   * @throws Exception JSON parsing, or server side exception
   */
  protected ErrorList getErrorListFromAjaxReturnObject(MvcResult result) throws Exception {
    return mvcUtils.getErrorListFromAjaxReturnObject(result);
  }

  /**
   * Given a JSON MvcResult containing ResponseBody, will attempt to parse it into a Java object,
   * using jackson's default {@link ObjectMapper}
   *
   * @param result
   * @param clazz The class you want parsed back
   * @return
   * @throws Exception JSON parsing, or server side exception
   */
  protected <T> T getFromJsonResponseBody(MvcResult result, Class<T> clazz) throws Exception {
    return mvcUtils.getFromJsonResponseBody(result, clazz);
  }

  /**
   * Given a Java object will attempt to convert it into JSON
   *
   * @param object Java object to convert
   * @return JSON string
   * @throws JsonProcessingException
   */
  protected String getAsJsonString(Object object) throws JsonProcessingException {
    return mvcUtils.getAsJsonString(object);
  }

  protected MockHttpServletRequestBuilder createBuilderForPostWithJSONBody(
      String apiKey, String suffixUrl, User user, Object toPost) {
    String body = getStringBody(toPost);
    return preparePostOrPutRequestBody(
        post(createUrl(API_VERSION.ONE, suffixUrl)), body, user, apiKey);
  }

  protected MockHttpServletRequestBuilder preparePostOrPutRequestBody(
      MockHttpServletRequestBuilder builder, String body, User user, String apiKey) {
    return builder
        .content(body)
        .contentType(MediaType.APPLICATION_JSON)
        .principal(new MockPrincipal(user.getUsername()))
        .header("apiKey", apiKey);
  }

  protected String createUrl(API_VERSION version, String suffixUrl) {
    return "/api/v" + version.getVersion() + "/" + suffixUrl;
  }

  protected String getStringBody(Object toPost) {
    String body = "";
    if (toPost instanceof String) {
      body = toPost.toString();
    } else {
      body = JacksonUtil.toJson(toPost);
    }
    return body;
  }
}
