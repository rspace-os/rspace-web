package com.researchspace.dao.hibernate;

import static org.junit.Assert.assertThat;

import com.axiope.search.IFileIndexer;
import com.axiope.search.SearchConstants;
import com.researchspace.Constants;
import com.researchspace.model.Group;
import com.researchspace.model.GroupType;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.search.impl.FileIndexSearcher;
import com.researchspace.search.impl.FileIndexer;
import com.researchspace.search.impl.LuceneSearchStrategy;
import com.researchspace.service.IGroupCreationStrategy;
import com.researchspace.service.SearchSpringTestBase;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;

public class WithinRecordsSearchTest extends SearchSpringTestBase {

  @Autowired private IGroupCreationStrategy groupCreationStrategy;

  public @Rule TemporaryFolder tempIndexFolder = new TemporaryFolder();
  private @Autowired FileIndexSearcher searcher;
  private IFileIndexer fileIndexer;

  @Before
  public void setUp() throws Exception {
    fileIndexer = new FileIndexer();
    fileIndexer.setIndexFolderDirectly(tempIndexFolder.getRoot());
    fileIndexer.init(true);
    getTargetObject(searcher.getFileSearchStrategy(), LuceneSearchStrategy.class)
        .setIndexFolderDirectly(tempIndexFolder.getRoot());

    setupRandomUser();
  }

  @Test
  public void testWithinRecordSearches() throws IOException, InterruptedException {
    // Scenario setup
    // Given the example folder tree below, where 'a' ,'b' 'c', and 'empty' are Folders, and
    // 'notebook' is a Notebook:
    //
    // * All documents/entries contain the same word 'protein'.
    //
    // * The word 'nucleotide' does not exist in any documents.
    //
    // * 'doc1b' contains the word 'detergent' uniquely, i.e. no other documents contain this word.
    //
    // root
    // ├── a
    // │   ├── doc1
    // │   ├── doc1a
    // │   ├── doc1b
    // │   └── doc1c
    // ├── b
    // │   ├── c
    // │   │   └── doc3
    // |   └── doc2
    // ├── empty
    // └── notebook
    //    ├── entry_1
    //    ├── entry_2
    //    └── entry_3
    Folder root = folderMgr.getRootFolderForUser(user);
    Folder a = createFolder(root, user, "a");
    Folder b = createFolder(root, user, "b");
    Folder empty = createFolder(root, user, "empty");
    Notebook notebook = createNotebookWithNEntries(root.getId(), "notebook", 3, user);
    Folder c = createFolder(b, user, "c");

    StructuredDocument doc1 = createBasicDocumentInFolder(user, a, "I think protein is awesome.");
    StructuredDocument doc1a = createBasicDocumentInFolder(user, a, "I think protein is awesome.");
    StructuredDocument doc1b =
        createBasicDocumentInFolder(
            user, a, "I don't think laundry detergent contains any protein.");
    StructuredDocument doc1c = createBasicDocumentInFolder(user, a, "I think protein is awesome.");
    StructuredDocument doc2 = createBasicDocumentInFolder(user, b, "I think protein is awesome.");
    StructuredDocument doc3 = createBasicDocumentInFolder(user, c, "I think protein is awesome.");
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    StructuredDocument entry1 =
        (StructuredDocument)
            notebook.getChildrens().stream()
                .filter(r -> r.getName().equals("entry_1"))
                .findFirst()
                .get();
    entry1.getFields().get(0).setFieldData("I think protein is awesome");
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    StructuredDocument entry2 =
        (StructuredDocument)
            notebook.getChildrens().stream()
                .filter(r -> r.getName().equals("entry_2"))
                .findFirst()
                .get();
    entry2.getFields().get(0).setFieldData("I think protein is awesome");
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    StructuredDocument entry3 =
        (StructuredDocument)
            notebook.getChildrens().stream()
                .filter(r -> r.getName().equals("entry_3"))
                .findFirst()
                .get();
    entry3.getFields().get(0).setFieldData("I think protein is awesome");
    recordMgr.save(entry1, user);
    recordMgr.save(entry2, user);
    recordMgr.save(entry3, user);

    flushToSearchIndices();

    // Carry out test scenarios

    // Case #1
    // Search for protein in folder a and get all its entries
    testRecordSearch(
        Collections.singletonList(a.getGlobalIdentifier()),
        "protein",
        Arrays.asList(doc1, doc1a, doc1b, doc1c),
        user);

    // Case #2
    // Search for protein in doc1a and get it back
    testRecordSearch(
        Collections.singletonList(doc1a.getGlobalIdentifier()),
        "protein",
        Collections.singletonList(doc1a),
        user);

    // Case #3
    // Search for protein in doc1a and doc1b and get them back
    testRecordSearch(
        Arrays.asList(doc1a.getGlobalIdentifier(), doc1b.getGlobalIdentifier()),
        "protein",
        Arrays.asList(doc1a, doc1b),
        user);

    // Case #4
    // Search for detergent in doc1a and doc1b and only get doc1b back
    testRecordSearch(
        Arrays.asList(doc1a.getGlobalIdentifier(), doc1b.getGlobalIdentifier()),
        "detergent",
        Collections.singletonList(doc1b),
        user);

    // Case #5
    // Search for protein in the notebook and get all entries
    testRecordSearch(
        Collections.singletonList(notebook.getGlobalIdentifier()),
        "protein",
        Arrays.asList(entry1, entry2, entry3),
        user);

    // Case #6
    // Search for protein in the notebook AND folder and get back all results
    testRecordSearch(
        Arrays.asList(notebook.getGlobalIdentifier(), a.getGlobalIdentifier()),
        "protein",
        Arrays.asList(entry1, entry2, entry3, doc1, doc1a, doc1b, doc1c),
        user);

    // Case #7
    // Search for protein in folder b and get results in subfolders too
    testRecordSearch(
        Collections.singletonList(b.getGlobalIdentifier()),
        "protein",
        Arrays.asList(doc2, doc3),
        user);

    // Case #8
    // Empty results still get filtered to be empty
    testRecordSearch(
        Collections.singletonList(b.getGlobalIdentifier()),
        "nucleotide",
        Collections.emptyList(),
        user);

    // Case #9
    // Empty folder returns no hits
    testRecordSearch(
        Collections.singletonList(empty.getGlobalIdentifier()),
        "protein",
        Collections.emptyList(),
        user);

    // Case #10
    // No results when searching in a folder that doesn't exist
    Folder nonExistant = new Folder();
    nonExistant.setId(9999L);
    testRecordSearch(
        Collections.singletonList(nonExistant.getGlobalIdentifier()),
        "protein",
        Collections.emptyList(),
        user);

    // Case #11
    // Searching in the root folder returns everything
    testRecordSearch(
        Collections.singletonList(root.getGlobalIdentifier()),
        "protein",
        Arrays.asList(doc1, doc1a, doc1b, doc1c, doc2, doc3, entry1, entry2, entry3),
        user);

    // Case #12
    // No results when trying to search in a folder that the user isn't authorized to see
    User newUser = doCreateAndInitUser("someguy");
    logoutAndLoginAs(newUser);
    testRecordSearch(
        Collections.singletonList(root.getGlobalIdentifier()),
        "protein",
        Collections.emptyList(),
        newUser);
  }

  private WorkspaceListingConfig setupSearch(String[] options, String[] terms) {
    WorkspaceListingConfig cfg =
        new WorkspaceListingConfig(
            PaginationCriteria.createDefaultForClass(BaseRecord.class), options, terms, -1L, true);
    return cfg;
  }

  /**
   * When files are shared they can have more than 1 parent. This test verifies that such scenarios
   * still work as one would expect – as long as 1 of the parents are selected, the record will be
   * returned in the search results.
   */
  @Test
  public void testMultipleParentSearch() throws IOException {
    User pi = doCreateAndInitUser("pi-guy", Constants.PI_ROLE);
    Folder root = folderMgr.getRootFolderForUser(pi);
    Folder a = createFolder(root, pi, "a");
    Group g = groupCreationStrategy.createAndSaveGroup(pi, pi, GroupType.LAB_GROUP, pi);
    Long groupFolderId = g.getCommunalGroupFolderId();

    StructuredDocument doc =
        createBasicDocumentInFolder(pi, a, "When I grow up, I want to be a potato!");
    flushToSearchIndices();

    // Document is only in folder a
    testRecordSearch(
        Collections.singletonList(a.getGlobalIdentifier()),
        "potato",
        Collections.singletonList(doc),
        pi);
    testRecordSearch(
        Collections.singletonList("FL" + groupFolderId), "potato", Collections.emptyList(), pi);

    // Share document to lab group folder
    folderMgr.addChild(groupFolderId, doc, pi);
    flushToSearchIndices();

    testRecordSearch(
        Collections.singletonList(a.getGlobalIdentifier()),
        "potato",
        Collections.singletonList(doc),
        pi);
    testRecordSearch(
        Collections.singletonList("FL" + groupFolderId),
        "potato",
        Collections.singletonList(doc),
        pi);

    // Remove document from original folder
    folderMgr.removeBaseRecordFromFolder(doc, a.getId());
    flushToSearchIndices();

    testRecordSearch(
        Collections.singletonList(a.getGlobalIdentifier()), "potato", Collections.emptyList(), pi);
    testRecordSearch(
        Collections.singletonList("FL" + groupFolderId),
        "potato",
        Collections.singletonList(doc),
        pi);

    // Remove document from lab group folder as well
    folderMgr.removeBaseRecordFromFolder(doc, groupFolderId);
    flushToSearchIndices();

    testRecordSearch(
        Collections.singletonList(a.getGlobalIdentifier()), "potato", Collections.emptyList(), pi);
    testRecordSearch(
        Collections.singletonList("FL" + groupFolderId), "potato", Collections.emptyList(), pi);
  }

  private void testRecordSearch(
      List<String> recordsToSelect,
      String searchTerm,
      List<BaseRecord> recordsToExpect,
      User userSearching)
      throws IOException {
    String[] options =
        new String[] {
          SearchConstants.FULL_TEXT_SEARCH_OPTION, SearchConstants.RECORDS_SEARCH_OPTION
        };
    String[] terms =
        new String[] {
          searchTerm,
          recordsToSelect.stream().map(Object::toString).collect(Collectors.joining(","))
        };
    WorkspaceListingConfig cfg =
        new WorkspaceListingConfig(
            PaginationCriteria.createDefaultForClass(BaseRecord.class), options, terms, -1L, true);

    List<BaseRecord> results = searchMgr.searchWorkspaceRecords(cfg, userSearching).getResults();
    assertThat(
        results, IsIterableContainingInAnyOrder.containsInAnyOrder(recordsToExpect.toArray()));
  }
}
