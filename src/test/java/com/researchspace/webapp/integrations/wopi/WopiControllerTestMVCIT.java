package com.researchspace.webapp.integrations.wopi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.User;
import com.researchspace.service.impl.ShiroTestUtils;
import com.researchspace.webapp.controller.MVCTestBase;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class WopiControllerTestMVCIT extends MVCTestBase {

  @Autowired private WopiProofKeyValidationInterceptor proofKeyInterceptor;

  @Autowired private WopiAccessTokenHandler accessTokenHandler;

  @Autowired private WopiDiscoveryServiceHandler discoveryServiceHandler;

  @Autowired private WopiDiscoveryProcessor discoveryProcessor;

  private MockMvc mockMvc;
  private User testUser;
  private ShiroTestUtils shiroTestUtils;

  @Rule public MockitoRule rule = MockitoJUnit.rule();

  @Mock private Subject subject;

  @Before
  public void setUp() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();

    WopiTestUtilities.setWopiDiscoveryFromExampleFile(
        discoveryServiceHandler, discoveryProcessor, WopiTestUtilities.MSOFFICE_DISCOVERY_XML_FILE);
    testUser = createAndSaveUser(getRandomAlphabeticString("user"));
    setUpUserWithInitialisedContent(testUser);
    shiroTestUtils = new ShiroTestUtils();
    shiroTestUtils.setSubject(subject);
    Mockito.when(subject.getSession()).thenReturn(new SimpleSession());

    // proof keys only validated in a specific test
    proofKeyInterceptor.setProofKeyValidationEnabled("false");
  }

  @After
  public void tearDown() throws Exception {
    shiroTestUtils.clearSubject();
  }

  @Test
  public void testAccessTokenAndProofKeyInterceptors() throws Exception {
    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);
    String fileId = msExcel.getGlobalIdentifier().toString();
    String userToken = createAccessToken(testUser, fileId);

    MvcResult invalidTokenResult =
        this.mockMvc
            .perform(get("/wopi/files/" + fileId).param("access_token", "dummyToken"))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertEquals(401, invalidTokenResult.getResponse().getStatus());

    proofKeyInterceptor.setProofKeyValidationEnabled("true");
    MvcResult noProofKeyResult =
        this.mockMvc
            .perform(get("/wopi/files/" + fileId).param("access_token", userToken))
            .andExpect(status().is5xxServerError())
            .andReturn();
    assertEquals(500, noProofKeyResult.getResponse().getStatus());
  }

  @Test
  public void testCheckFileInfoOperation() throws Exception {

    EcatDocumentFile msExcel = addDocumentFromTestResourcesToGallery("simpleExcel.xlsx", testUser);
    String fileId = msExcel.getGlobalIdentifier().toString();
    String userToken = createAccessToken(testUser, fileId);

    MvcResult checkFileInfoResult =
        this.mockMvc
            .perform(get("/wopi/files/" + fileId).param("access_token", userToken))
            .andExpect(jsonPath("$.BaseFileName").value(msExcel.getName()))
            .andExpect(jsonPath("$.OwnerId").value(msExcel.getOwner().getId()))
            .andExpect(jsonPath("$.Size").value(msExcel.getSize()))
            .andExpect(jsonPath("$.UserId").value(testUser.getId()))
            .andExpect(
                jsonPath("$.UserCanNotWriteRelative")
                    .value(true)) // writeRelative only allowed for conversion flow
            .andExpect(jsonPath("$.Version").value("1"))
            .andExpect(status().isOk())
            .andReturn();

    assertNull(checkFileInfoResult.getResolvedException());
  }

  @Test
  public void testGetFileOperation() throws Exception {

    EcatDocumentFile msDoc = addDocumentFromTestResourcesToGallery("MSattachment.doc", testUser);
    String fileId = msDoc.getGlobalIdentifier().toString();
    String userToken = createAccessToken(testUser, fileId);

    MvcResult getFileResult =
        this.mockMvc
            .perform(get("/wopi/files/" + fileId + "/contents").param("access_token", userToken))
            .andExpect(status().isOk())
            .andReturn();

    byte[] content = getFileResult.getResponse().getContentAsByteArray();
    assertNotNull(content);
    assertEquals(msDoc.getSize(), content.length);
  }

  @Test
  public void testLockOperationSequence() throws Exception {

    EcatDocumentFile msDoc = addDocumentFromTestResourcesToGallery("MSattachment.doc", testUser);
    String fileId = msDoc.getGlobalIdentifier().toString();
    String userToken = createAccessToken(testUser, fileId);
    String firstLockId = "lockId";
    String secondLockId = "secondLockId";

    // verify no lock
    this.mockMvc
        .perform(
            post("/wopi/files/" + fileId)
                .param("access_token", userToken)
                .header(
                    WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_GET_LOCK))
        .andExpect(status().isOk())
        .andExpect(header().string(WopiController.X_WOPI_LOCK_HEADER, ""))
        .andReturn();

    // lock the file
    this.mockMvc
        .perform(
            post("/wopi/files/" + fileId)
                .param("access_token", userToken)
                .header(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_LOCK)
                .header(WopiController.X_WOPI_LOCK_HEADER, firstLockId))
        .andExpect(status().isOk())
        .andReturn();

    // verify lock with correct id applied now
    this.mockMvc
        .perform(
            post("/wopi/files/" + fileId)
                .param("access_token", userToken)
                .header(
                    WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_GET_LOCK))
        .andExpect(status().isOk())
        .andExpect(header().string(WopiController.X_WOPI_LOCK_HEADER, firstLockId))
        .andReturn();

    // refresh lock
    this.mockMvc
        .perform(
            post("/wopi/files/" + fileId)
                .param("access_token", userToken)
                .header(
                    WopiController.X_WOPI_OVERRIDE_HEADER,
                    WopiController.OVERRIDE_HEADER_REFRESH_LOCK)
                .header(WopiController.X_WOPI_LOCK_HEADER, firstLockId))
        .andExpect(status().isOk())
        .andReturn();

    // relock with different lock id
    this.mockMvc
        .perform(
            post("/wopi/files/" + fileId)
                .param("access_token", userToken)
                .header(WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_LOCK)
                .header(WopiController.X_WOPI_LOCK_HEADER, secondLockId)
                .header(WopiController.X_WOPI_OLD_LOCK_HEADER, firstLockId))
        .andExpect(status().isOk())
        .andReturn();

    // verify lock with new id applied now
    this.mockMvc
        .perform(
            post("/wopi/files/" + fileId)
                .param("access_token", userToken)
                .header(
                    WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_GET_LOCK))
        .andExpect(status().isOk())
        .andExpect(header().string(WopiController.X_WOPI_LOCK_HEADER, secondLockId))
        .andReturn();

    // unlock
    this.mockMvc
        .perform(
            post("/wopi/files/" + fileId)
                .param("access_token", userToken)
                .header(
                    WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_UNLOCK)
                .header(WopiController.X_WOPI_LOCK_HEADER, secondLockId))
        .andExpect(status().isOk())
        .andReturn();

    // verify no lock after unlock
    this.mockMvc
        .perform(
            post("/wopi/files/" + fileId)
                .param("access_token", userToken)
                .header(
                    WopiController.X_WOPI_OVERRIDE_HEADER, WopiController.OVERRIDE_HEADER_GET_LOCK))
        .andExpect(status().isOk())
        .andExpect(header().string(WopiController.X_WOPI_LOCK_HEADER, ""))
        .andReturn();
  }

  protected String createAccessToken(User testUser, String fileId) {
    return accessTokenHandler.createAccessToken(testUser, fileId).getAccessToken();
  }
}
