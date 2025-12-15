package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.researchspace.Constants;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.testutils.TestGroup;
import org.junit.Before;
import org.junit.Test;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

/** Acceptance test for NotebookEditorController */
@WebAppConfiguration
public class NotebookEditorControllerMVCIT extends MVCTestBase {

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @Test
  public void testDeleteRecord() throws Exception {
    User u = createAndSaveUser(getRandomAlphabeticString("u"));
    User other = createAndSaveUser(getRandomAlphabeticString("other"));
    initUsers(u, other);
    logoutAndLoginAs(u);
    final int NUM_ENTRIES = 2;
    Folder rootFolder = folderMgr.getRootRecordForUser(u, u);
    Notebook notebook = createNotebookWithNEntries(rootFolder.getId(), "any", NUM_ENTRIES, u);

    // happy case
    Long entryId = getFirstEntryId(notebook);
    assertTrue(entryId != null); // sanity check

    assertEquals(
        NUM_ENTRIES,
        recordMgr
            .listFolderRecords(notebook.getId(), DEFAULT_RECORD_PAGINATION)
            .getHits()
            .intValue());
    MvcResult result =
        mockMvc
            .perform(
                post("/notebookEditor/ajax/delete/{recordid}/{parentid}", entryId, notebook.getId())
                    .principal(new MockPrincipal(u.getUsername())))
            .andReturn();
    assertEquals(entryId.longValue(), Long.parseLong(result.getResponse().getContentAsString()));
    // check it is actually marked deleted
    assertEquals(
        NUM_ENTRIES - 1,
        recordMgr
            .listFolderRecords(notebook.getId(), DEFAULT_RECORD_PAGINATION)
            .getHits()
            .intValue());

    // unauthorised
    logoutAndLoginAs(other);
    MvcResult result2 =
        mockMvc
            .perform(
                post("/notebookEditor/ajax/delete/{recordid}/{parentid}", entryId, notebook.getId())
                    .principal(new MockPrincipal(other.getUsername())))
            .andReturn();
    assert (result2.getResolvedException() instanceof RecordAccessDeniedException);
  }

  @Test
  public void openNotebookWithoutParentContext() throws Exception {
    TestGroup g1 = createTestGroup(1);
    User u1 = g1.u1();
    logoutAndLoginAs(u1);

    Notebook nb = createNotebookWithNEntries(getRootFolderForUser(u1).getId(), "nb", 1, u1);
    shareNotebookWithGroup(u1, nb, g1.getGroup(), "read");

    // retrieve notebook with just an id, e.g. when navigating through globalId url
    MvcResult result =
        mockMvc
            .perform(
                get("/notebookEditor/" + nb.getId()).principal(new MockPrincipal(u1.getUsername())))
            .andReturn();
    assertNull(result.getResolvedException());
    assertNotNull(result.getModelAndView());
    assertEquals(nb.getId(), result.getModelAndView().getModel().get("selectedNotebookId"));
    assertTrue(
        ((ActionPermissionsDTO) result.getModelAndView().getModel().get("permDTO"))
            .isCreateRecord());

    // now try retrieving as a sysadmin, who has global read permission
    User sysadmin = createAndSaveUser(CoreTestUtils.getRandomName(10), Constants.SYSADMIN_ROLE);
    initUser(sysadmin);
    result =
        mockMvc
            .perform(
                get("/notebookEditor/" + nb.getId())
                    .principal(new MockPrincipal(sysadmin.getUsername())))
            .andReturn();
    assertNull(result.getResolvedException());
    assertNotNull(result.getModelAndView());
    assertEquals(nb.getId(), result.getModelAndView().getModel().get("selectedNotebookId"));
    assertFalse(
        ((ActionPermissionsDTO) result.getModelAndView().getModel().get("permDTO"))
            .isCreateRecord());
  }

  private Long getFirstEntryId(Notebook notebook) {
    return folderMgr.getFolderChildrenIds(notebook).get(0);
  }
}
