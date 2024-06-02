package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
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

  private Long getFirstEntryId(Notebook notebook) {
    return folderMgr.getRecordIds(notebook).get(0);
  }
}
