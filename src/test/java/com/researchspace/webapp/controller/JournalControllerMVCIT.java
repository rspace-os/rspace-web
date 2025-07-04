package com.researchspace.webapp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.JournalEntry;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@WebAppConfiguration
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@RunWith(ConditionalTestRunner.class)
public class JournalControllerMVCIT extends MVCTestBase {

  @Autowired private WebApplicationContext wac;

  private MockMvc mockMvc;
  @Autowired private MvcTestUtils mvcUtils;

  @Before
  public void setup() throws Exception {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).build();
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    RSpaceTestUtils.logout();
  }

  @Test
  public void testRetrieveNotebookEntryById() throws Exception {
    User user = createAndSaveUser(getRandomAlphabeticString("any"));
    initUser(user);
    logoutAndLoginAs(user);

    Folder rootFolder = folderMgr.getRootRecordForUser(user, user);
    Notebook nbook = createNotebookWithNEntries(rootFolder.getId(), "any", 2, user);
    StructuredDocument entry = createEntryInNotebook(nbook, user);

    MvcResult result =
        mockMvc
            .perform(
                get("/journal/ajax/retrieveEntryById/{notebookId}/{entryId}", nbook.getId(), entry.getId())
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    JournalEntry retrievedEntry = getFromJsonResponseBody(result, JournalEntry.class);
    assertEquals(entry.getGlobalIdentifier(), retrievedEntry.getGlobalId());
    assertEquals(2, retrievedEntry.getPosition()); // it's a 3rd entry
  }

  @Test
  public void testNotebookSearch() throws Exception {

    User user = createAndSaveUser(getRandomAlphabeticString("any"));
    initUser(user);
    logoutAndLoginAs(user);

    Folder rootFolder = folderMgr.getRootRecordForUser(user, user);
    Notebook nbook = createNotebookWithNEntries(rootFolder.getId(), "any", 1, user);
    StructuredDocument entry = createEntryInNotebook(nbook, user);
    Field f1 = entry.getFields().get(0);
    // via autosave
    addContentToEntry("<p> wasp bee honey hornet evangelism</p>", entry.getId(), user, f1);
    // search doesn't work on autosaved only items, requires 'saved'
    saveEntry(entry.getId(), user);

    // should get hit
    assertSearchForTermRetrievesEntrry("bee", entry, user, true);
    assertSearchForTermRetrievesEntrry("'honey hornet'", entry, user, true);
    assertSearchForTermRetrievesEntrry("hon*", entry, user, true);

    // should miss
    assertSearchForTermRetrievesEntrry("noh*", entry, user, false);
  }

  @Test
  public void testFullTextNotebookSearch() throws Exception {

    User user = createAndSaveUser(getRandomAlphabeticString("any"));
    initUser(user);
    logoutAndLoginAs(user);

    Folder rootFolder = folderMgr.getRootRecordForUser(user, user);

    Notebook nbook = createNotebookWithNEntries(rootFolder.getId(), "nb", 2, user);
    List<Long> ids = folderMgr.getRecordIds(nbook);
    StructuredDocument entry1 = getNotebookEntry(user, ids, 0);
    StructuredDocument entry2 = getNotebookEntry(user, ids, 1);
    Field f1 = entry1.getFields().get(0);
    Field f2 = entry2.getFields().get(0);
    // via autosave
    addContentToEntry("rna protein entry", entry1.getId(), user, f1);
    addContentToEntry("rna protein entry", entry2.getId(), user, f2);

    // search doesn't work on autosaved only items, requires 'saved'
    saveEntry(entry1.getId(), user);
    saveEntry(entry2.getId(), user);

    MvcResult result =
        this.mockMvc
            .perform(
                get("/journal/ajax/quicksearch/" + nbook.getId() + "/0/" + "protein")
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(status().isOk())
            .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
            .andReturn();
  }

  private StructuredDocument getNotebookEntry(User user, List<Long> ids, int index) {
    return recordMgr.getRecordWithFields(ids.get(index), user).asStrucDoc();
  }

  private void assertSearchForTermRetrievesEntrry(
      String term, StructuredDocument entry, User user, boolean isMatch) throws Exception {
    ResultActions result =
        this.mockMvc
            .perform(
                get("/journal/ajax/quicksearch/" + entry.getParent().getId() + "/0/" + term)
                    .principal(new MockPrincipal(user.getUsername())))
            .andExpect(status().isOk());

    if (isMatch) {
      result.andExpect(jsonPath("$[0].id").value(entry.getId().intValue()));
    } else {
      result.andExpect(content().string(not(containsString(entry.getId() + ""))));
    }
    if (isMatch) {
      result.andExpect(jsonPath("$.length()").value(1));
    }
  }

  private void saveEntry(Long entryId, User user) throws Exception {
    this.mockMvc
        .perform(
            post("/workspace/editor/structuredDocument/ajax/saveStructuredDocument/")
                .param("structuredDocumentId", entryId + "")
                .param("unlock", "true")
                .principal(new MockPrincipal(user.getUsername())))
        .andExpect(status().isOk())
        .andReturn();
  }

  private void addContentToEntry(String data, Long entryId, User user, Field field)
      throws Exception {
    mvcUtils.doAutosaveAndSaveMVC(field, data, user, mockMvc);
  }

  private StructuredDocument createEntryInNotebook(Notebook nbook, User user) {
    return recordMgr.createBasicDocument(nbook.getId(), user);
  }

  @Test
  @RunIfSystemPropertyDefined(value = "nightly")
  public void testNavigating150EntriesNotebook() throws Exception {
    User u = createAndSaveUser(getRandomAlphabeticString("u"));
    initUsers(u);
    logoutAndLoginAs(u);

    final int NUM_ENTRIES = 150;
    Folder rootFolder = folderMgr.getRootRecordForUser(u, u);
    Notebook notebook = createNotebookWithNEntries(rootFolder.getId(), "any", NUM_ENTRIES, u);

    // open notebook
    Instant openStart = Instant.now();
    MvcResult result =
        mockMvc
            .perform(
                get("/journal/ajax/retrieveEntry/{notebookId}/0/0", notebook.getId())
                    .principal(new MockPrincipal(u.getUsername())))
            .andReturn();
    Instant openEnd = Instant.now();
    Duration notebookOpeningTime = Duration.between(openStart, openEnd);
    //  System.out.println("opening took: " + notebookOpeningTime);

    assertNull(result.getResolvedException());
    // initial entry retrieval may be a bit longer, as classes/tables are being loaded first time
    assertTrue(
        "opening notebook should take under 4 seconds, but was: " + notebookOpeningTime.toString(),
        notebookOpeningTime.getSeconds() < 4);

    // switch to next entry
    Instant nextEntryStart = Instant.now();
    MvcResult result2 =
        mockMvc
            .perform(
                get("/journal/ajax/retrieveEntry/{notebookId}/0/1", notebook.getId())
                    .principal(new MockPrincipal(u.getUsername())))
            .andReturn();
    Instant nextEntryEnd = Instant.now();
    Duration entrySwitchTime = Duration.between(nextEntryStart, nextEntryEnd);
    //  System.out.println("switching entry took: " + entrySwitchTime);

    assertNull(result2.getResolvedException());
    // subsequent entry retrieval should be faster
    assertTrue(
        "switching entry should take under 2 second, but was: " + entrySwitchTime.toString(),
        entrySwitchTime.getSeconds() < 2);
  }
}
