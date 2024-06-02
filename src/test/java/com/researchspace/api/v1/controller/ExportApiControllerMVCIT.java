package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.BaseApiController.EXPORT_INTERNAL_ENDPOINT;
import static com.researchspace.api.v1.controller.BaseApiController.JOBS_INTERNAL_ENDPOINT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.api.v1.model.ApiJob;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.TestGroup;
import java.io.UnsupportedEncodingException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.validation.BindException;

@WebAppConfiguration
public class ExportApiControllerMVCIT extends API_MVC_TestBase {

  @Autowired ExportApiController exportApiController;

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void testExportUserSelfValid() throws Exception {
    User any = createAndSaveUser(getRandomAlphabeticString("user"));
    initUser(any, true);
    logoutAndLoginAs(any);
    createBasicDocumentInRootFolderWithText(any, "some text");

    String apiKey = userApiKeyMgr.createKeyForUser(any).getApiKey();
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE, apiKey, EXPORT_INTERNAL_ENDPOINT + "/html/user", any))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(status().isAccepted())
            .andReturn();
    ApiJob job = getFromJsonResponseBody(result, ApiJob.class);
    assertNull(result.getResolvedException());
    ApiJob completed = assertJobCompleted(any, apiKey, job);
  }

  @Test
  public void testExportSelectionWithRevisionHistory() throws Exception {
    User any = createAndSaveUser(getRandomAlphabeticString("user"));
    initUser(any, true);
    logoutAndLoginAs(any);
    StructuredDocument sd2 = createBasicDocumentInRootFolderWithText(any, "some othertext");
    int newRevisionCount = 2;
    renameDocumentNTimes(sd2, newRevisionCount);

    String reqParam = "" + sd2.getId();
    String apiKey = userApiKeyMgr.createKeyForUser(any).getApiKey();

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    apiKey,
                    EXPORT_INTERNAL_ENDPOINT
                        + "/html/selection?includeRevisionHistory=true&selections="
                        + reqParam,
                    any))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(status().isAccepted())
            .andReturn();
    ApiJob job = getFromJsonResponseBody(result, ApiJob.class);
    assertNull(result.getResolvedException());
    ApiJob completed = assertJobCompleted(any, apiKey, job);
    ZipInputStream zis = getZipInputStreamToArchive(any, apiKey, completed);

    ZipEntry z = null;
    int htmlFileCount = 0;
    while ((z = zis.getNextEntry()) != null) {
      if (z.getName().toLowerCase().matches(".+rev\\d+\\.html$")) {
        htmlFileCount++;
      }
    }
    assertEquals(newRevisionCount + 1, htmlFileCount); // original + 2 revisions
  }

  @Test
  public void testExportOtherUserByPi() throws Exception {

    TestGroup grp = createTestGroup(1);

    String piApiKey = userApiKeyMgr.createKeyForUser(grp.getPi()).getApiKey();
    logoutAndLoginAs(grp.u1());
    createBasicDocumentInRootFolderWithText(grp.u1(), "text");

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    piApiKey,
                    EXPORT_INTERNAL_ENDPOINT + "/html/user/" + grp.getUserByPrefix("u1").getId(),
                    grp.getPi()))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(status().isAccepted())
            .andReturn();
    ApiJob job = getFromJsonResponseBody(result, ApiJob.class);
    assertNull(result.getResolvedException());

    // now get jobs, this will be complete, as tests run synchronously, so export will be completed
    // before this is called.
    assertJobCompleted(grp.getPi(), piApiKey, job);
  }

  @Test
  public void testExportGroupByPi() throws Exception {

    TestGroup grp = createTestGroup(1);

    String piApiKey = userApiKeyMgr.createKeyForUser(grp.getPi()).getApiKey();
    logoutAndLoginAs(grp.getPi());
    createBasicDocumentInRootFolderWithText(grp.getPi(), "text");

    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    piApiKey,
                    EXPORT_INTERNAL_ENDPOINT + "/html/group/" + grp.getGroup().getId(),
                    grp.getPi()))
            .andExpect(jsonPath("$.id").exists())
            .andExpect(jsonPath("$.status").exists())
            .andExpect(status().isAccepted())
            .andReturn();
    ApiJob job = getFromJsonResponseBody(result, ApiJob.class);
    assertNull(result.getResolvedException());

    // now get jobs, this will be complete, as tests run synchronously, so export will be completed
    // before this is called.
    assertJobCompleted(grp.getPi(), piApiKey, job);

    // and check user can't export group:
    String userApiKey = userApiKeyMgr.createKeyForUser(grp.getUserByPrefix("u1")).getApiKey();
    MvcResult result2 =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE,
                    userApiKey,
                    EXPORT_INTERNAL_ENDPOINT + "/html/group/" + grp.getGroup().getId(),
                    grp.getUserByPrefix("u1")))
            .andExpect(status().is4xxClientError())
            .andReturn();
  }

  private ApiJob assertJobCompleted(User subject, String apiKey, ApiJob job)
      throws Exception, UnsupportedEncodingException {
    MvcResult result2 =
        mockMvc
            .perform(
                createBuilderForGet(
                    API_VERSION.ONE, apiKey, JOBS_INTERNAL_ENDPOINT + "/" + job.getId(), subject))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.percentComplete").value(100.00))
            .andReturn();
    return getFromJsonResponseBody(result2, ApiJob.class);
  }

  @Test
  public void testExportInValid() throws Exception {
    User sysadmin = logoutAndLoginAsSysAdmin();
    String apiKey = userApiKeyMgr.createKeyForUser(sysadmin).getApiKey();
    MvcResult result =
        mockMvc
            .perform(
                createBuilderForPost(
                        API_VERSION.ONE, apiKey, EXPORT_INTERNAL_ENDPOINT + "/html/XXX", sysadmin)
                    .param("ids", "1", "2"))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertTrue(result.getResolvedException() instanceof BindException);
    MvcResult result2 =
        mockMvc
            .perform(
                createBuilderForPost(
                    API_VERSION.ONE, apiKey, EXPORT_INTERNAL_ENDPOINT + "/XXX/user", sysadmin))
            .andExpect(status().is4xxClientError())
            .andReturn();
    assertTrue(result2.getResolvedException() instanceof BindException);
  }
}
