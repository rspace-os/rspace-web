package com.researchspace.webapp.integrations.wopi;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.service.impl.ShiroTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.subject.Subject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

public class WopiAuthorisationInterceptorTest extends SpringTransactionalTest {

  @Autowired private WopiAuthorisationInterceptor authInterceptor;

  @Autowired private WopiAccessTokenHandler accessTokenHandler;

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Mock private Subject subject;

  private User testUser;
  private MockHttpServletRequest req;
  private MockHttpServletResponse resp;
  private ShiroTestUtils shiroTestUtils;

  @Before
  public void setUp() throws Exception {
    testUser = doCreateAndInitUser(getRandomAlphabeticString("wopi"));
    shiroTestUtils = new ShiroTestUtils();
    shiroTestUtils.setSubject(subject);
    Mockito.when(subject.getSession()).thenReturn(new SimpleSession());
  }

  @After
  public void tearDown() throws Exception {
    shiroTestUtils.clearSubject();
  }

  @Test
  public void testAccessTokenValidation() throws IOException, URISyntaxException {
    // upload doc
    EcatDocumentFile msDoc = addDocumentFromTestResourcesToGallery("MSattachment.doc", testUser);
    String docFileId = msDoc.getGlobalIdentifier().toString();
    String docFileAccessToken =
        accessTokenHandler.createAccessToken(testUser, docFileId).getAccessToken();

    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);
    String excelFileId = msExcel.getGlobalIdentifier().toString();
    String excelFileAccessToken =
        accessTokenHandler.createAccessToken(testUser, excelFileId).getAccessToken();

    // try with invalid access token
    req = new MockHttpServletRequest();
    setRequestAccessTokenAndFileId(req, "dummyToken", docFileId);
    resp = new MockHttpServletResponse();
    boolean invalidTokenResult = authInterceptor.preHandle(req, resp, null);
    assertFalse(invalidTokenResult);
    assertEquals(401, resp.getStatus());

    // try checking doc file with access token of excel file
    req = new MockHttpServletRequest();
    setRequestAccessTokenAndFileId(req, excelFileAccessToken, docFileId);
    resp = new MockHttpServletResponse();
    boolean invalidFileResult = authInterceptor.preHandle(req, resp, null);
    assertFalse(invalidFileResult);
    assertEquals(401, resp.getStatus());

    // try checking with correct access token matching file id
    req = new MockHttpServletRequest();
    setRequestAccessTokenAndFileId(req, docFileAccessToken, docFileId);
    resp = new MockHttpServletResponse();
    boolean correctTokenResult = authInterceptor.preHandle(req, resp, null);
    assertTrue(correctTokenResult);
    assertEquals(testUser, req.getAttribute("user"));
  }

  private void setRequestAccessTokenAndFileId(
      MockHttpServletRequest req, String accessToken, String fileId) {
    // access token is a parameter
    req.setParameter(WopiController.ACCESS_TOKEN_PARAM_NAME, accessToken);
    // file id is a path variable
    Map<Object, Object> pathVars = new HashMap<>();
    pathVars.put(WopiAuthorisationInterceptor.FILE_ID_PATH_VAR_NAME, fileId);
    req.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, pathVars);
  }
}
