package com.researchspace.webapp.controller;

import static com.researchspace.session.UserSessionTracker.USERS_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.axiope.search.SearchManager;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.model.RecordGroupSharing;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.field.TextField;
import com.researchspace.model.field.TextFieldForm;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.Record;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.JournalEntry;
import com.researchspace.service.FolderManager;
import com.researchspace.service.RecordSigningManager;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.session.SessionAttributeUtils;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpSession;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.session.Session;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.subject.support.SubjectThreadState;
import org.apache.shiro.util.ThreadState;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;

public class JournalControllerTest extends SpringTransactionalTest {

  private static final String EXPECTED_FIELD_HEADER_HTML = "<h2 class='formTitles'>";
  private static ThreadState subjectThreadState;
  @Rule public MockitoRule rule = MockitoJUnit.rule();

  private static final String TEXT_FIELD_TEST_DATA = "I AM A TEXT FIELD BELONGING TO ";
  private static final String TEXT_FIELD_NAME = "TEXT ";
  private static final String TEST_DATA_RECORD_NAME = "RECORD NAME ";
  private @Autowired JournalController journalController;

  private MockServletContext sc;

  private JournalRecordManagerStub recordManagerStub;
  private JournalSearchManagerStub searchManagerStub;
  @Mock private RecordSigningManager signingManager;
  @Mock private FolderManager folderManager;
  @Mock private SearchManager searchMgr;
  @Mock private SystemPropertyPermissionManager systemPropertyPermissionManager;
  @Mock private Session shiroSessionMock;
  private HttpSession session;
  @Mock private Subject subject;
  @Mock private User userMock;

  private Principal mockPrincipal =
      new Principal() {
        @Override
        public String getName() {
          return "user1a";
        }
      };
  private ISearchResults<BaseRecord> searchResults;
  private ISearchResults<BaseRecord> noSearchResults;
  @Mock private Notebook notebookMock;

  @Before
  public void setUp() throws IOException {
    setupLoggedInUser("user1a");
    recordManagerStub = new JournalRecordManagerStub();
    searchManagerStub = new JournalSearchManagerStub();
    journalController.setRecordManager(recordManagerStub);
    journalController.setSearchManager(searchMgr);
    journalController.setSigningManager(signingManager);
    journalController.setFolderManager(folderManager);
    journalController.setSysPropPermissionsMgr(systemPropertyPermissionManager);
    session = new MockHttpSession();

    sc = new MockServletContext();
    sc.setAttribute(USERS_KEY, anySessionTracker());

    journalController.setServletContext(sc);
    setUpSearchResults();
  }

  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
    RSpaceTestUtils.logout();
    subjectThreadState.clear();
  }

  private void setupLoggedInUser(String userName) {
    subjectThreadState = new SubjectThreadState(subject);
    subjectThreadState.bind();
    when(subject.getSession()).thenReturn(shiroSessionMock);
    when(shiroSessionMock.getAttribute(eq(SessionAttributeUtils.USER))).thenReturn(userMock);
    when(userMock.getUsername()).thenReturn(userName);
    when(userMock.isAnonymousGuestAccount())
        .thenReturn(RecordGroupSharing.ANONYMOUS_USER.equals(userName));
  }

  private void setUpSearchResults() throws IOException {
    ArrayList<BaseRecord> found = new ArrayList<BaseRecord>();
    for (int i = 0; i < 20; i++) {
      found.add(createRecordForStub((long) i));
    }
    searchResults = new SearchResultsImpl<BaseRecord>(found, 7, 3L);
    noSearchResults = new SearchResultsImpl<BaseRecord>(new ArrayList<BaseRecord>(), 7, 3L);
    when(searchMgr.searchWorkspaceRecords(any(WorkspaceListingConfig.class), eq(userMock)))
        .thenReturn(searchResults);
  }

  @Test
  public void retrieveEntryTest() throws Exception {

    // 0L represents root record and is not used in the stubs
    final Long targetParent = 0L;
    // test retrieval of first record call used when editing a record or
    // when you first open the editor
    JournalEntry entry = journalController.retrieveEntry(targetParent, 0, 0, mockPrincipal);

    assertNotNull(entry);
    assertEquals(new Long(1), entry.getId());
    assertEquals(new Integer(0), entry.getPosition());
    assertEquals(TEST_DATA_RECORD_NAME + 1L, entry.getName());
    assertTrue(entry.getHtml().contains(TEXT_FIELD_NAME + 1L));

    // test retrieval of next record should skip 2L
    JournalEntry nextEntry = journalController.retrieveEntry(targetParent, 0, 1, mockPrincipal);
    assertNotNull(nextEntry);
    assertEquals(new Long(3), nextEntry.getId());
    assertEquals(new Integer(2), nextEntry.getPosition());
    assertEquals(TEST_DATA_RECORD_NAME + 3L, nextEntry.getName());
    assertTrue(nextEntry.getHtml().contains(TEXT_FIELD_NAME + 3L));

    // test retrieval of previous record should skip 2L
    JournalEntry previousEntry =
        journalController.retrieveEntry(targetParent, 1, -1, mockPrincipal);
    assertNotNull(previousEntry);
    assertEquals(new Long(1), previousEntry.getId());
    assertEquals(new Integer(0), previousEntry.getPosition());
    assertEquals(TEST_DATA_RECORD_NAME + 1L, previousEntry.getName());
    assertTrue(previousEntry.getHtml().contains(TEXT_FIELD_NAME + 1L));

    // test nothing to the left of position
    JournalEntry nothingLeftEntry =
        journalController.retrieveEntry(targetParent, 0, -1, mockPrincipal);
    assertNotNull(nothingLeftEntry);
    assertNull(nothingLeftEntry.getId());
    assertEquals("NO_RESULT", nothingLeftEntry.getName()); // name should be
    // NO RESULT

    // test nothing to the right of position
    JournalEntry nothingRightEntry =
        journalController.retrieveEntry(targetParent, 20, 1, mockPrincipal);
    assertNotNull(nothingRightEntry);
    assertNull(nothingRightEntry.getId());
    assertEquals("NO_RESULT", nothingRightEntry.getName()); // name should
    // be NO RESULT

    // test when no records exist
    recordManagerStub.makeEmpty(true);
    JournalEntry emptyEntry = journalController.retrieveEntry(targetParent, 0, 0, mockPrincipal);
    assertNotNull(emptyEntry);
    assertNull(emptyEntry.getId());
    assertNull(emptyEntry.getPosition());
    assertEquals("", emptyEntry.getHtml());
    assertEquals("EMPTY", emptyEntry.getName()); // name should be EMPTY

    recordManagerStub.makeEmpty(false);
  }

  @Test
  public void onlyTextFieldsContainLoMDivd() {
    assertLoMIncluded(true, 1);
  }

  @Test
  public void listOfMaterialsDivOmittedIfInventoryUnavailable() {
    assertLoMIncluded(false, 0);
  }

  void assertLoMIncluded(final boolean include, final int expectedLoMCount) {
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    RSForm form = createTestForm();
    StructuredDocument doc =
        recordFactory.createStructuredDocument("testDocWithHtmlContent", user, form);
    Mockito.when(
            systemPropertyPermissionManager.isPropertyAllowed((User) null, "inventory.available"))
        .thenReturn(include);
    String combinedEntryContent = journalController.prepareStructuredDocumentContent(doc);
    Document d = Jsoup.parse(combinedEntryContent);
    Elements divs = d.select("div.invMaterialsListing");
    assertEquals(expectedLoMCount, divs.size());
  }

  @Test
  public void testEscapingOfFieldsContent() {
    User user = createAndSaveUserIfNotExists(getRandomAlphabeticString("any"));
    RSForm form = createTestForm();
    StructuredDocument doc =
        recordFactory.createStructuredDocument("testDocWithHtmlContent", user, form);
    String combinedEntryContent = journalController.prepareStructuredDocumentContent(doc);

    // non-text field content should be escaped
    assertTrue(combinedEntryContent.contains("<p>default text</p><br/><br/>"));
    assertTrue(combinedEntryContent.contains("&lt;p&gt;default string&lt;/p&gt;<br/>"));
  }

  private RSForm createTestForm() {
    RSForm form = recordFactory.createNewForm();
    TextFieldForm textField = new TextFieldForm("Text");
    textField.setDefaultValue("<p>default text</p>");
    textField.setColumnIndex(0);
    textField.setId(1L);
    form.addFieldForm(textField);

    StringFieldForm stringField = new StringFieldForm("String");
    stringField.setDefaultStringValue("<p>default string</p>");
    stringField.setColumnIndex(1);
    stringField.setId(2L);
    form.addFieldForm(stringField);
    return form;
  }

  @Test
  public void retrieveHistoryTest() {
    // 0l represents root record and is not used in the stubs
    final Long targetParent = 0l;

    // test retrieval of page -1 should be 0 enteries
    List<JournalEntry> pageMinus1enteries =
        journalController.retrieveHistory(targetParent, -1, true, session, mockPrincipal).getBody();
    assertNotNull(pageMinus1enteries);
    assertEquals(0, pageMinus1enteries.size());

    // test retrieval of page 1 should be 7 enteries
    List<JournalEntry> page1enteries =
        journalController.retrieveHistory(targetParent, 1, true, session, mockPrincipal).getBody();
    assertNotNull(page1enteries);
    assertEquals(7, page1enteries.size());

    // test retrieval of page 2 should be 7 enteries
    List<JournalEntry> page2enteries =
        journalController.retrieveHistory(targetParent, 2, true, session, mockPrincipal).getBody();
    assertNotNull(page2enteries);
    assertEquals(7, page2enteries.size());

    // test retrieval of page 3 should be 2 enteries
    List<JournalEntry> page3enteries =
        journalController.retrieveHistory(targetParent, 3, true, session, mockPrincipal).getBody();
    assertNotNull(page3enteries);
    assertEquals(2, page3enteries.size());

    // test retrieval of page 4 should be 0 enteries
    List<JournalEntry> page4enteries =
        journalController.retrieveHistory(targetParent, 4, true, session, mockPrincipal).getBody();
    assertNotNull(page4enteries);
    assertEquals(0, page4enteries.size());
  }

  @Test
  public void searchTextTest() throws IOException {
    final Long targetParent = 0l;

    // test search should return 20 records
    List<JournalEntry> searchEntries =
        journalController.searchText("abc", targetParent, 0, mockPrincipal).getBody();
    assertNotNull(searchEntries);
    assertEquals(20, searchEntries.size());

    JournalEntry anEntry = searchEntries.get(0);
    assertTrue(anEntry.getName().contains(TEST_DATA_RECORD_NAME));
    assertNotNull(anEntry.getPosition()); // plugin sets and relies heavily
    // on position needs to
    // always be returned

    // test no results
    when(searchMgr.searchWorkspaceRecords(any(WorkspaceListingConfig.class), eq(userMock)))
        .thenReturn(noSearchResults);
    assertNull(journalController.searchText("abc", targetParent, 0, mockPrincipal));
  }

  @Test
  public void shouldSearchWhenAnonymousUserLoggedInSearchingAsTheDocumentOwner()
      throws IOException {
    final Long targetParent = 0l;
    when(folderManager.getNotebook(eq(targetParent))).thenReturn(notebookMock);
    when(notebookMock.getOwner()).thenReturn(userMock);
    when(notebookMock.isPublished()).thenReturn(true);
    setupLoggedInUser(RecordGroupSharing.ANONYMOUS_USER);
    // test search should return 20 records
    List<JournalEntry> searchEntries =
        journalController.searchText("abc", targetParent, 0, mockPrincipal).getBody();
    assertNotNull(searchEntries);
    assertEquals(20, searchEntries.size());
  }

  @Test
  public void shouldThrowExceptionWhenSearchAsAnonymousUserForUnpublishedDocument()
      throws IOException {
    final Long targetParent = 0l;
    when(folderManager.getNotebook(eq(targetParent))).thenReturn(notebookMock);
    when(notebookMock.getOwner()).thenReturn(userMock);
    setupLoggedInUser(RecordGroupSharing.ANONYMOUS_USER);
    Exception exception =
        assertThrows(
            AuthorizationException.class,
            () -> {
              journalController.searchText("abc", targetParent, 0, mockPrincipal);
            });
  }

  /** I've extended the stub because I need getRecord to not return certain records */
  private class JournalRecordManagerStub extends RecordManagerStub {

    private Boolean isEmpty = false;

    // override so we can test what happens when no records are returned
    public void makeEmpty(Boolean isEmpty) {
      this.isEmpty = isEmpty;
    }

    @Override
    public Record get(long id) {
      if (2L == id || 4L == id || 6L == id) {
        return null;
      }
      return createRecordForStub(id);
    }

    public List<Long> getDescendantRecordIdsExcludeFolders(Long parentId) {
      ArrayList<Long> results = new ArrayList<Long>();
      if (!isEmpty) {
        for (int i = 1; i < 20; i++) {
          results.add((long) i);
        }
      }
      return results;
    }

    public List<Record> getLoadableNotebookEntries(User user, Long notebookId) {
      List<Record> results = new ArrayList<>();
      if (!isEmpty) {
        for (long i = 1L; i < 20; i++) {
          if (i == 2L || i == 4L || i == 6L) {
            continue;
          }
          results.add(createRecordForStub(i));
        }
      }
      return results;
    }
  }

  private class JournalSearchManagerStub {

    private Boolean isEmpty = false;

    // override so we can test what happens when no records are returned
    public void makeEmpty(Boolean isEmpty) {
      this.isEmpty = isEmpty;
    }

    public ISearchResults<BaseRecord> searchRecords(WorkspaceListingConfig cfg, User u) {
      ArrayList<BaseRecord> results = new ArrayList<BaseRecord>();
      if (!isEmpty) {
        for (int i = 0; i < 20; i++) {
          results.add(createRecordForStub((long) i));
        }
      }
      return new SearchResultsImpl<BaseRecord>(results, 7, cfg.getParentFolderId());
    }
  }

  private StructuredDocument createRecordForStub(Long id) {
    StructuredDocument sd = new StructuredDocument(TestFactory.createAnyForm());
    sd.setId(id);
    sd.setName(TEST_DATA_RECORD_NAME + id);
    // add some fake fields using id as unique identifier
    TextField tf = new TextField(new TextFieldForm());
    tf.setName(TEXT_FIELD_NAME + id);
    tf.setFieldData(TEXT_FIELD_TEST_DATA + id);
    sd.addField(tf);
    return sd;
  }
}
