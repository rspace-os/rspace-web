package com.researchspace.webapp.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.impl.ConditionalTestRunner;
import com.researchspace.service.impl.RunIfSystemPropertyDefined;
import com.researchspace.testutils.RSpaceTestUtils;
import java.io.UnsupportedEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
  public void testNotebookSearch() throws Exception {

    User user = createAndSaveUser(getRandomAlphabeticString("any"));
    initUser(user);

    // login so we don't get redirected to login page
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

    // login so we don't get redirected to login page
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

    //		JSONParser p = new JSONParser();
    //		List<JournalEntry> list = (List<JournalEntry>)
    // p.parse(result.getResponse().getContentAsString());
    //		assertEquals(2, list.size());
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

  @Test
  public void testEntryIndexRetrieveCycle() throws Exception {
    User u = createAndSaveUser(getRandomAlphabeticString("any"));
    initUser(u);
    logoutAndLoginAs(u);
    Folder root = folderMgr.getRootRecordForUser(u, u);
    Notebook nb = createNotebookWithNEntries(root.getId(), "nb", 3, u);
    List<Long> ids = folderMgr.getRecordIds(nb);
    Collections.sort(ids);
    StructuredDocument entry1 = getNotebookEntry(u, ids, 0);
    StructuredDocument entry2 = getNotebookEntry(u, ids, 1);
    StructuredDocument entry3 = getNotebookEntry(u, ids, 2);

    List<BaseRecord> sortedEntries = Arrays.asList(new BaseRecord[] {entry1, entry2, entry3});

    // store a map of the entry index and the record..
    Map<Integer, BaseRecord> indexToRecord = new TreeMap<>();
    MvcResult result = getIndexOfEntry(u, nb, sortedEntries.get(0));
    indexToRecord.put(parseIndexFromResponse(result), sortedEntries.get(0));
    MvcResult result2 = getIndexOfEntry(u, nb, sortedEntries.get(1));
    indexToRecord.put(parseIndexFromResponse(result2), sortedEntries.get(1));
    MvcResult result3 = getIndexOfEntry(u, nb, sortedEntries.get(2));
    indexToRecord.put(parseIndexFromResponse(result3), sortedEntries.get(2));
    for (Integer index : indexToRecord.keySet()) {
      // now lets load the record and make sure we get the right record for the index
      this.mockMvc
          .perform(
              get(
                      "/journal/ajax/retrieveEntry/{notebookid}/{position}/{positionmodifier}",
                      nb.getId() + "",
                      index + "",
                      0)
                  .principal(new MockPrincipal(u.getUsername())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(indexToRecord.get(index).getId().intValue()))
          .andReturn();
    }
    // let's delete record2.
    recordDeletionMgr.deleteRecord(nb.getId(), sortedEntries.get(1).getId(), u);
    indexToRecord.clear();
    MvcResult afterDeletionresult = getIndexOfEntry(u, nb, sortedEntries.get(0));
    indexToRecord.put(parseIndexFromResponse(afterDeletionresult), sortedEntries.get(0));
    // now check that indexes still match
    MvcResult afterDeletionresult3 = getIndexOfEntry(u, nb, sortedEntries.get(2));
    indexToRecord.put(parseIndexFromResponse(afterDeletionresult3), sortedEntries.get(2));
    for (Integer index : indexToRecord.keySet()) {
      // now lets load the record and make sure we get the right record for the index
      this.mockMvc
          .perform(
              get(
                      "/journal/ajax/retrieveEntry/{notebookid}/{position}/{positionmodifier}",
                      nb.getId() + "",
                      index + "",
                      0)
                  .principal(new MockPrincipal(u.getUsername())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(indexToRecord.get(index).getId().intValue()))
          .andReturn();
    }
  }

  private int parseIndexFromResponse(MvcResult result) throws UnsupportedEncodingException {
    return Integer.parseInt(result.getResponse().getContentAsString());
  }

  private MvcResult getIndexOfEntry(User u, Notebook nb, BaseRecord entry1) throws Exception {
    return this.mockMvc
        .perform(
            get(
                    "/journal/ajax/entryIndex/{notebookId}/{entryId}",
                    nb.getId() + "",
                    entry1.getId() + "")
                .principal(new MockPrincipal(u.getUsername())))
        .andExpect(status().isOk())
        .andReturn();
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
