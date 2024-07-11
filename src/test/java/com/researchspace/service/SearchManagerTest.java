package com.researchspace.service;

import static com.axiope.search.SearchConstants.FORM_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.FULL_TEXT_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.MODIFICATION_DATE_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.NAME_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.OWNER_SEARCH_OPTION;
import static com.axiope.search.SearchConstants.TAG_SEARCH_OPTION;
import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static com.researchspace.testutils.SearchTestUtils.createAdvSearchCfg;
import static com.researchspace.testutils.SearchTestUtils.createAdvSearchCfgWithFilters;
import static com.researchspace.testutils.SearchTestUtils.createSearchByTemplateCfg;
import static com.researchspace.testutils.SearchTestUtils.createSimpleFormSearchCfg;
import static com.researchspace.testutils.SearchTestUtils.createSimpleFullTextSearchCfg;
import static com.researchspace.testutils.SearchTestUtils.createSimpleGeneralSearchCfg;
import static com.researchspace.testutils.SearchTestUtils.createSimpleOwnerSearchCfg;
import static com.researchspace.testutils.SearchTestUtils.createSimpleTagSearchCfg;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.axiope.search.IFileIndexer;
import com.axiope.search.InventorySearchConfig.InventorySearchDeletedOption;
import com.axiope.search.InventorySearchConfig.InventorySearchType;
import com.axiope.search.SearchConstants;
import com.axiope.search.SearchUtils;
import com.researchspace.Constants;
import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiBasket;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiExtraField;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo.ApiInventoryRecordType;
import com.researchspace.api.v1.model.ApiInventorySearchResult;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.core.util.IPagination;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SortOrder;
import com.researchspace.dao.hibernate.FullTextSearcherImpl;
import com.researchspace.model.EcatAudio;
import com.researchspace.model.EcatDocumentFile;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatVideo;
import com.researchspace.model.Group;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.dtos.SearchOperator;
import com.researchspace.model.dtos.ShareConfigElement;
import com.researchspace.model.dtos.StringFieldDTO;
import com.researchspace.model.dtos.WorkspaceFilters;
import com.researchspace.model.dtos.WorkspaceListingConfig;
import com.researchspace.model.field.FieldTestUtils;
import com.researchspace.model.field.StringFieldForm;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.permissions.DefaultPermissionFactory;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.IllegalAddChildOperation;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.search.impl.FileIndexSearcher;
import com.researchspace.search.impl.FileIndexer;
import com.researchspace.search.impl.LuceneSearchStrategy;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SearchTestUtils;
import com.researchspace.testutils.TestGroup;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.shiro.authz.AuthorizationException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.springframework.beans.factory.annotation.Autowired;

public class SearchManagerTest extends SearchSpringTestBase {
  private @Autowired FormManager formMgr;
  private @Autowired MediaManager mediaMgr;
  private @Autowired RecordFavoritesManager favoritesManager;
  @Autowired FullTextSearcherImpl fullTextSearchSearchManagerTester;
  @Autowired FileIndexSearcher fileIndexSearcher;

  IFileIndexer fileIndexer;

  public @Rule TemporaryFolder randomFilefolder = new TemporaryFolder();
  public @Rule TemporaryFolder indexfolder = new TemporaryFolder();

  @Before
  public void setUp() throws Exception {
    fileIndexer = new FileIndexer();
    fileIndexer.setIndexFolderDirectly(indexfolder.getRoot());
    fileIndexer.init(true);
    getTargetObject(fileIndexSearcher.getFileSearchStrategy(), LuceneSearchStrategy.class)
        .setIndexFolderDirectly(indexfolder.getRoot());
    perFactory = new DefaultPermissionFactory();
    sampleDao.resetDefaultTemplateOwner();
  }

  /** Check if we can search documents created from templates by the template name */
  @Test
  public void testSDqueryByTemplate() throws DocumentAlreadyEditedException, IOException {
    User newUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(newUser);

    Folder root = initialiseContentWithExampleContent(newUser);
    assertFalse(root.getChildren().isEmpty());

    logoutAndLoginAs(newUser);

    String newName = "copied_record";

    // create document
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(newUser, "text");

    // create template from document
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(anyDoc.getId(), newUser);

    StructuredDocument fromTemplate = createFromTemplate(newUser, template, newName);
    flushToSearchIndices();

    // we expect fromTemplate to be returned from both queries
    // search by name
    String searchFor = template.getName();

    ISearchResults<BaseRecord> results =
        searchMgr.searchWorkspaceRecords(createSearchByTemplateCfg(searchFor), newUser);
    assertEquals(1, (int) results.getHits());
    assertEquals(fromTemplate, results.getFirstResult());

    // search by Oid
    searchFor = template.getOid().getIdString();

    results = searchMgr.searchWorkspaceRecords(createSearchByTemplateCfg(searchFor), newUser);
    assertEquals(1, (int) results.getHits());
    assertEquals(fromTemplate, results.getFirstResult());
  }

  /** Test if a template is findable when changing the name */
  @Test
  public void searchForSharedTemplate() throws DocumentAlreadyEditedException, IOException {
    TestGroup testGroup = createTestGroup(2);
    User pi = testGroup.getPi();
    User normalUser = testGroup.u1();

    logoutAndLoginAs(pi);

    // create document from which we will create a template
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(pi, "text");

    // create template from document
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(anyDoc.getId(), pi);

    // share that template with the group
    shareRecordWithUser(pi, template, normalUser);

    logoutAndLoginAs(normalUser);

    flushToSearchIndices();
    // sanity check that user can see the shared template
    ISearchResults<BaseRecord> results =
        searchMgr.searchWorkspaceRecords(
            createSimpleGeneralSearchCfg(template.getName()), normalUser);
    assertEquals(1, (int) results.getHits());
    assertEquals(template, results.getFirstResult());

    String documentFromTemplateName = "Document_from_template";
    // now create doc from shared template
    StructuredDocument fromTemplate =
        createFromTemplate(normalUser, template, documentFromTemplateName);
    flushToSearchIndices();
    // should get the template
    results =
        searchMgr.searchWorkspaceRecords(createSearchByTemplateCfg(template.getName()), normalUser);
    assertEquals(1, (int) results.getHits());
    assertEquals(fromTemplate, results.getFirstResult());
  }

  /**
   * Test if a template is findable when changing the name.
   *
   * <p>FIXME: derived docs are only findable by template global id, not by new name. See in-line
   * comments for details.
   */
  @Test
  public void testSearchWithTemplateChangedName()
      throws DocumentAlreadyEditedException, IOException {
    User newUser = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(newUser);

    Folder root = initialiseContentWithExampleContent(newUser);
    assertTrue(!root.getChildren().isEmpty());

    logoutAndLoginAs(newUser);

    // create document
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(newUser, "text");

    // create template from document
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(anyDoc.getId(), newUser);
    StructuredDocument fromTemplate = createFromTemplate(newUser, template, "from_template");

    String templateName = template.getName();
    flushToSearchIndices();

    // we expect fromTemplate to be returned from this query
    ISearchResults<BaseRecord> results =
        searchMgr.searchWorkspaceRecords(createSearchByTemplateCfg(templateName), newUser);
    assertEquals(1, (int) results.getHits());
    assertEquals(fromTemplate, results.getFirstResult());

    // now change the template name and try to find the record again
    String newTemplateName = RandomStringUtils.randomAlphabetic(10);
    boolean renameOk = recordMgr.renameRecord(newTemplateName, template.getId(), newUser);
    if (!renameOk) {
      throw new IllegalStateException("couldn't rename");
    }
    flushToSearchIndices();

    /*
     * mk 19/06/20:
     * After fixing a problem that the indexes were not always flushed
     * inside SpringTransactionalTest this test now fails
     * i.e. the search works for old template name, not for new one.
     *
     * The reason is that we search by 'templateName' indexed field, which
     * is not updated for derived documents when the template is renamed.
     * I confirmed the behaviour in the application - you can't find
     * template-derived docs by new template name, just by old one.
     *
     * This is probably a bug, but it's a pre-existing one that nobody
     * ever complained about, so I'm just documenting it now.
     *
     * Also note that you can find derived docs by globalId of the template.
     * Maybe that was used by people as a workaround.
     */
    results = searchMgr.searchWorkspaceRecords(createSearchByTemplateCfg(templateName), newUser);
    assertEquals(1, (int) results.getHits());
    assertEquals(fromTemplate, results.getFirstResult());
    results = searchMgr.searchWorkspaceRecords(createSearchByTemplateCfg(newTemplateName), newUser);
    assertEquals(0, (int) results.getHits()); // FIXME: probably a bug in the application
    results =
        searchMgr.searchWorkspaceRecords(
            createSearchByTemplateCfg(template.getGlobalIdentifier()), newUser);
    assertEquals(1, (int) results.getHits()); // you can find doc by template global id though
    assertEquals(fromTemplate, results.getFirstResult());
  }

  /**
   * TODO: This test has been modified including "commentXYZ" to the text field (Structured
   * Document). EcatCommentItem is not retrieved by search when we set a user filter on the search.
   */
  @Test
  public void testSearchCommentText() throws IOException {
    setupRandomUser();
    StructuredDocument sd =
        createBasicDocumentInRootFolderWithText(
            user, "<p> any text</p>"); // Remove commentXYZ from this line to get error.
    createCommentItemForField(sd.getFields().get(0), "commentxyz", user);
    flushToSearchIndices();

    WorkspaceListingConfig cfg = createSimpleFullTextSearchCfg("commentxyz");
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results);
    assertTrue("doc was not retrieved", results.getResults().contains(sd));
  }

  @Test
  public void testSearchByGlobalIdInFullText() throws IOException {
    setupRandomPIUser();
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(user, "<p> any text</p>");
    flushToSearchIndices();

    WorkspaceListingConfig cfg = createSimpleFullTextSearchCfg(sd.getGlobalIdentifier());
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results);
    assertTrue(results.getResults().contains(sd));
  }

  @Test
  public void testSearchDescriptionInMediaSubclasses() throws IOException {

    setupRandomPIUser();
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(user, "<p> any text</p>");
    sd.setDescription("qwerty");
    recordMgr.save(sd, user);
    flushToSearchIndices();

    WorkspaceListingConfig cfg = createSimpleFullTextSearchCfg("qwerty");
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results);
    assertTrue(results.getResults().contains(sd));

    // check another record
    InputStream fis =
        SearchManagerTest.class.getClassLoader().getResourceAsStream("TestResources/Picture1.png");
    EcatImage image = mediaMgr.saveNewImage("Picture1.png", fis, user, null);
    image.setDescription("image1");
    recordMgr.save(image, user);
    flushToSearchIndices();

    cfg = createSimpleFullTextSearchCfg("image1");

    ISearchResults<BaseRecord> results2 = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results2);

    // check audio
    InputStream is =
        SearchManagerTest.class
            .getClassLoader()
            .getResourceAsStream("TestResources/mpthreetest.mp3");
    EcatAudio audio = mediaMgr.saveNewAudio("mpthreetest.mp3", is, user, null, null);
    audio.setDescription("audio1");
    recordMgr.save(audio, user);
    flushToSearchIndices();
    cfg = createSimpleFullTextSearchCfg("audio1");

    ISearchResults<BaseRecord> results3 = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results3);
    assertTrue(results3.getResults().contains(audio));

    // check document
    InputStream is2 =
        SearchManagerTest.class.getClassLoader().getResourceAsStream("TestResources/testTxt.txt");
    EcatDocumentFile doc = mediaMgr.saveNewDocument("testTxt.txt", is2, user, null, null);
    doc.setDescription("doc1");
    recordMgr.save(doc, user);
    flushToSearchIndices();
    cfg = createSimpleFullTextSearchCfg("doc1");

    results2 = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results2);
    assertTrue(results2.getResults().contains(doc));

    // check video
    InputStream is3 =
        SearchManagerTest.class.getClassLoader().getResourceAsStream("TestResources/video.flv");
    EcatVideo vid = mediaMgr.saveNewVideo("video.flv", is3, user, null, null);
    vid.setDescription("vid1");
    recordMgr.save(vid, user);
    flushToSearchIndices();
    cfg = createSimpleFullTextSearchCfg("vid1");

    results2 = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results2);
    assertTrue(results2.getResults().contains(vid));
  }

  @Test
  public void testMultipleHitsInMultipleCategoriesAreCoalescedIntoSingleResult()
      throws IOException {

    setupRandomPIUser();
    final String content = "qwerty" + getRandomName(5);

    WorkspaceListingConfig cfg = createSimpleFullTextSearchCfg(content);
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(cfg, user);

    int initHitCount = results == null ? 0 : results.getTotalHits().intValue();
    // set the same string into multiple properties of objects
    StructuredDocument sd =
        createBasicDocumentInRootFolderWithText(user, "<p>" + content + " text</p>");
    sd.setDescription(content);
    sd.setDocTag(content);
    recordMgr.save(sd, user);
    flushToSearchIndices();

    cfg = createSimpleFullTextSearchCfg(content);
    results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results);
    assertTrue(results.getResults().contains(sd));
    // should just be 1 record returned.
    assertEquals(initHitCount + 1, results.getTotalHits().intValue());
  }

  @Test
  public void testMultipleHitsOnSameDocument() throws IOException {
    setupRandomPIUser();
    final String random = getRandomName(10);

    StructuredDocument sd = createDocWithTagNameDesc(random, random, random, random);
    flushToSearchIndices();

    WorkspaceListingConfig cfg = createSimpleGeneralSearchCfg(random);
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results);
    assertTrue(results.getResults().contains(sd));
    // should just be 1 record returned.
    assertEquals(1, results.getTotalHits().intValue());
  }

  @Test
  public void testAdvancedSearchOr() throws IOException, ParseException {
    setupRandomPIUser();
    final String random = getRandomName(10);
    final String random2 = getRandomName(10);
    final String tag1 = random + "tag1";
    final String name1 = random + "name1";
    final String desc1 = random + "desc1";
    final String tag2 = random2 + "tag2";
    final String name2 = random2 + "name2";
    final String desc2 = random2 + "desc2";

    createDocWithTagNameDesc(random, tag1, name1, desc1);
    createDocWithTagNameDesc(random2, tag2, name2, desc2);
    final int expectedOrHitCount = 2;
    // search by tag OR
    flushToSearchIndices();
    WorkspaceListingConfig cfg =
        createAdvSearchCfg(
            new String[] {SearchConstants.TAG_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {tag1, tag2},
            SearchOperator.OR);
    assertNHits(cfg, expectedOrHitCount, user);

    // name OR
    cfg =
        createAdvSearchCfg(
            new String[] {NAME_SEARCH_OPTION, NAME_SEARCH_OPTION},
            new String[] {name1, name2},
            SearchOperator.OR);
    assertNHits(cfg, expectedOrHitCount, user);

    // text OR
    cfg =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION, FULL_TEXT_SEARCH_OPTION},
            new String[] {desc1, desc2},
            SearchOperator.OR);
    assertNHits(cfg, expectedOrHitCount, user);

    // form OR
    cfg =
        createAdvSearchCfg(
            new String[] {FORM_SEARCH_OPTION, FORM_SEARCH_OPTION},
            new String[] {"Basic*", "Basic Document"},
            SearchOperator.OR);
    assertNHits(cfg, expectedOrHitCount, user);

    // name/text OR
    cfg =
        createAdvSearchCfg(
            new String[] {NAME_SEARCH_OPTION, FULL_TEXT_SEARCH_OPTION},
            new String[] {name1, desc2},
            SearchOperator.OR);
    assertNHits(cfg, expectedOrHitCount, user);

    // name/tag OR
    cfg =
        createAdvSearchCfg(
            new String[] {NAME_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {name1, tag2},
            SearchOperator.OR);
    assertNHits(cfg, expectedOrHitCount, user);

    // text/tag OR
    cfg =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {desc1, tag2},
            SearchOperator.OR);
    assertNHits(cfg, expectedOrHitCount, user);
    // tag/date OR
    final int expectedCreatedToday = 10;

    String datesearchString = getFormatedDateStr();
    cfg =
        createAdvSearchCfg(
            new String[] {MODIFICATION_DATE_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {datesearchString, tag2},
            SearchOperator.OR);
    assertNHits(cfg, expectedCreatedToday, user);
    // check for and as well
    cfg =
        createAdvSearchCfg(
            new String[] {MODIFICATION_DATE_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {datesearchString, tag2},
            SearchOperator.AND);
    // check creation date option as well
    cfg =
        createAdvSearchCfg(
            new String[] {SearchConstants.CREATION_DATE_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {datesearchString, tag2},
            SearchOperator.OR);
    assertNHits(cfg, expectedCreatedToday, user);
    // check for and as well
    cfg =
        createAdvSearchCfg(
            new String[] {SearchConstants.CREATION_DATE_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {datesearchString, tag2},
            SearchOperator.AND);
    assertNHits(cfg, 1, user);
  }

  private String getFormatedDateStr() {
    String dFrom =
        ZonedDateTime.now(ZoneOffset.UTC).minusDays(3).format(DateTimeFormatter.ISO_INSTANT);
    String dTo =
        ZonedDateTime.now(ZoneOffset.UTC).plusDays(3).format(DateTimeFormatter.ISO_INSTANT);
    String dateSearchString = formatDateSearchString(dFrom, dTo);
    return dateSearchString;
  }

  private StructuredDocument createDocWithTagNameDesc(
      final String random, final String tag1, final String name1, final String desc1) {
    StructuredDocument sd =
        createBasicDocumentInRootFolderWithText(user, "<p>" + random + " text</p>");
    sd.setName(name1);
    sd.setDescription(desc1);
    sd.setDocTag(tag1);
    recordMgr.save(sd, user);
    return sd;
  }

  @Test
  public void testMultipleHitsOnDifferentDocuments() throws IOException {
    final int n = 10;
    setupRandomPIUser();
    final String random = getRandomName(n);
    final String random2 = getRandomName(n);

    StructuredDocument sd = createDocWithTagNameDesc(random, random, random, random);
    createDocWithTagNameDesc(random2, random2, random2, random2);
    flushToSearchIndices();

    WorkspaceListingConfig cfg =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {random, random2},
            SearchOperator.AND);

    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    cfg =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION, TAG_SEARCH_OPTION},
            new String[] {random, random2},
            SearchOperator.OR);

    results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results);
    assertTrue(results.getResults().contains(sd));
    // should just be 2 records returned.
    assertEquals(2, results.getTotalHits().intValue());
  }

  @Test
  public void testSearchStringField() throws IOException {
    setupRandomPIUser();
    Folder root = user.getRootFolder();
    RSForm rs = formMgr.create(user);
    rs.addFieldForm(FieldTestUtils.createStringForm());
    rs.setName("stringForm");
    formMgr.save(rs, user);
    StructuredDocument sd = recordMgr.createNewStructuredDocument(root.getId(), rs.getId(), user);
    String content = "xyz" + getRandomName(3);
    sd.getFields().get(0).setFieldData(content);
    recordMgr.save(sd, user);

    flushToSearchIndices();

    WorkspaceListingConfig cfg = createSimpleFullTextSearchCfg(content);
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results);
    assertTrue(results.getResults().contains(sd));
  }

  @Test
  public void testGetSearchTagResults() throws IOException {
    setupRandomPIUser();
    Folder root = user.getRootFolder();
    StructuredDocument doc = recordMgr.createBasicDocument(root.getId(), user);
    WorkspaceListingConfig cfg = createSimpleTagSearchCfg("tag1");
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(cfg, user);
    // returns not null if no hits (
    // DatabaseSearchResults.emptyResult(input.getPgCrit()))
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    doc.setDocTag("first,second,spaced tag"); // comma delimited
    recordMgr.save(doc, user);
    flushToSearchIndices();

    // textSearchDao.indexText();
    // searching for either tag works
    cfg = createSimpleTagSearchCfg("first");
    results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertEquals(1L, results.getTotalHits().longValue());

    cfg = createSimpleTagSearchCfg("second");
    results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertEquals(1L, results.getTotalHits().longValue());

    // partial search doesn't work using default strategy
    cfg = createSimpleTagSearchCfg("firs");
    results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    // spaced tag works too
    cfg = createAdvSearchCfg(new String[] {TAG_SEARCH_OPTION}, new String[] {"'spaced tag'"});
    results = searchMgr.searchWorkspaceRecords(cfg, user);
    assertNotNull(results);
    assertEquals(1L, results.getTotalHits().longValue());
  }

  /**
   * Luke is a GUI viewer of the search index ( use version that is same as Hibernate search
   * version) that can help debug the tests
   *
   * @see <a href="http://code.google.com/p/luke/downloads/list">this thing</a>
   */
  @Test
  public void testGetFullTextSearch() throws IOException {
    setupRandomPIUser();
    createBasicDocumentInRootFolderWithText(
        user, " <p style='x'>lapwing INCENP-25 the, bearded tit, avocet, lapwing</p>");
    flushToSearchIndices(); // don't forget this else is not indexed!
    // will match twice but should just return 1 document
    WorkspaceListingConfig config = createSimpleFullTextSearchCfg("lapwing");

    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // standard stopwords not matched
    config = createSimpleFullTextSearchCfg("the");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    // html tags are stripped out and not matched
    config = createSimpleFullTextSearchCfg("<p/>");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    config = createSimpleFullTextSearchCfg("</style>");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    // phrase search
    config = createSimpleFullTextSearchCfg("'bearded tit'");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(1, results.getTotalHits().intValue());

    // wildcard search
    config = createSimpleFullTextSearchCfg("avoc*");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);

    // search includes commas,with or without quotes- RSPAC-1483
    config = createSimpleFullTextSearchCfg("'bearded tit, avocet'");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(1, results.getTotalHits().intValue());
    config = createSimpleFullTextSearchCfg("bearded tit, avocet");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(1, results.getTotalHits().intValue());

    // hyphenated term with number
    config = createSimpleFullTextSearchCfg("incenp-25");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(1, results.getTotalHits().intValue());

    // understands native Lucene?
    // default is anded:
    config = createSimpleFullTextSearchCfg("l:lapwing AND avocet");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(1, results.getTotalHits().intValue());

    config = createSimpleFullTextSearchCfg("l:lapwing -bearded");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    // or-ed together,  hit
    config = createSimpleFullTextSearchCfg("l:lapwing OR nohit");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(1, results.getTotalHits().intValue());

    // anded together, no hit
    config = createSimpleFullTextSearchCfg("l:+lapwing +nohit");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    // wildcard
    config = createSimpleFullTextSearchCfg("l:lapwi*");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(1, results.getTotalHits().intValue());

    // wildcard
    config = createSimpleFullTextSearchCfg("l:la?wing");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(1, results.getTotalHits().intValue());
  }

  /**
   * Test to compare the results on the advanced search and the simple search.
   *
   */
  @Test
  public void testAdvancedSearchNativeLucene() throws IOException {
    setupRandomPIUser();
    createBasicDocumentInRootFolderWithText(
        user, " <p style='x'>lapwing INCENP-25 the ,bearded tit ,avocet, lapwing</p>");
    flushToSearchIndices();

    // Advanced search by full text and tag.
    WorkspaceListingConfig config =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"l: lapwing AND avocet"});
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    config =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"l: lapwing -bearded"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().intValue());

    config =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"l: lapwing OR nohit"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);

    config =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"l: +lapwing +nohit"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().intValue());

    config = createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"l: lapwi*"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);

    config =
        createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"l: la?wing"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
  }

  /**
   * Test about advanced search results. Searching on different fields to retrieve the right
   * information
   *
   */
  @Test
  public void testAdvancedSearchMultipleTerm() throws IOException {
    setupRandomPIUser();
    Folder root = user.getRootFolder();
    StructuredDocument doc = recordMgr.createBasicDocument(root.getId(), user);
    doc.setName("DocumentNameTest");
    doc.getFields()
        .get(0)
        .setFieldData(" <p style='x'>lapwing INCENP-25 the ,bearded tit ,avocet, lapwing</p>");
    doc.setDocTag("doctagtest");
    recordMgr.save(doc, user);
    flushToSearchIndices();

    WorkspaceListingConfig config =
        createAdvSearchCfg(new String[] {TAG_SEARCH_OPTION}, new String[] {"lapwing"});
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().intValue());

    config = createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"lapwing"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by full text, tag and owner.
    config =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION, TAG_SEARCH_OPTION, OWNER_SEARCH_OPTION},
            new String[] {"lapwing", "doctagtest", user.getUsername()});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by full text, tag and full text.
    config =
        createAdvSearchCfg(
            new String[] {
              FULL_TEXT_SEARCH_OPTION,
              TAG_SEARCH_OPTION,
              FULL_TEXT_SEARCH_OPTION,
              OWNER_SEARCH_OPTION
            },
            new String[] {"lapwing", "doctagtest", "avocet", user.getUsername()});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by full text and full text.
    config =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION, FULL_TEXT_SEARCH_OPTION, OWNER_SEARCH_OPTION},
            new String[] {"'bearded tit'", "INCENP-25", user.getUsername()});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by name, tag and full text.
    config =
        createAdvSearchCfg(
            new String[] {
              NAME_SEARCH_OPTION,
              SearchConstants.TAG_SEARCH_OPTION,
              FULL_TEXT_SEARCH_OPTION,
              OWNER_SEARCH_OPTION
            },
            new String[] {"DocumentNameTest", "doctagtest", "avocet", user.getUsername()});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by form name, tag and full text.
    config =
        createAdvSearchCfg(
            new String[] {
              FORM_SEARCH_OPTION,
              SearchConstants.TAG_SEARCH_OPTION,
              FULL_TEXT_SEARCH_OPTION,
              OWNER_SEARCH_OPTION
            },
            new String[] {"Basic Document", "doctagtest", "avocet", user.getUsername()});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by form name, tag and full text.
    config =
        createAdvSearchCfg(
            new String[] {
              FORM_SEARCH_OPTION,
              SearchConstants.TAG_SEARCH_OPTION,
              FULL_TEXT_SEARCH_OPTION,
              OWNER_SEARCH_OPTION
            },
            new String[] {"Experiment", "doctagtest", "avocet", user.getUsername()});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().intValue());

    // Advanced search by form name, tag and full text.
    config =
        createAdvSearchCfg(
            new String[] {
              FORM_SEARCH_OPTION,
              SearchConstants.TAG_SEARCH_OPTION,
              FULL_TEXT_SEARCH_OPTION,
              OWNER_SEARCH_OPTION
            },
            new String[] {"Basic Document", "tt", "avocet", user.getUsername()});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);

    // Advanced search by name, docTag and two different modification dates
    // (from date - to date).
    config =
        createAdvSearchCfg(
            new String[] {
              NAME_SEARCH_OPTION,
              SearchConstants.TAG_SEARCH_OPTION,
              MODIFICATION_DATE_SEARCH_OPTION,
              OWNER_SEARCH_OPTION
            },
            new String[] {
              "DocumentNameTest", "doctagtest", getFormatedDateStr(), user.getUsername()
            });
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by name, docTag and two different creation dates
    // (from date - to date).
    config =
        createAdvSearchCfg(
            new String[] {
              NAME_SEARCH_OPTION,
              SearchConstants.TAG_SEARCH_OPTION,
              SearchConstants.CREATION_DATE_SEARCH_OPTION,
              OWNER_SEARCH_OPTION
            },
            new String[] {
              "DocumentNameTest", "doctagtest", getFormatedDateStr(), user.getUsername()
            });
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by name, docTag and modification date.
    config =
        createAdvSearchCfg(
            new String[] {
              NAME_SEARCH_OPTION,
              SearchConstants.TAG_SEARCH_OPTION,
              MODIFICATION_DATE_SEARCH_OPTION,
              OWNER_SEARCH_OPTION
            },
            new String[] {
              "DocumentNameTest", "doctagtest", getFormatedDateStr(), user.getUsername()
            });
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by name, docTag and creation date.
    config =
        createAdvSearchCfg(
            new String[] {
              NAME_SEARCH_OPTION,
              SearchConstants.TAG_SEARCH_OPTION,
              SearchConstants.CREATION_DATE_SEARCH_OPTION,
              OWNER_SEARCH_OPTION
            },
            new String[] {
              "DocumentNameTest", "doctagtest", getFormatedDateStr(), user.getUsername()
            });
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());
  }

  /**
   * Tests if files modified on one day, but created on another can be found using creation date
   * search and modification date search.
   *
   */
  @Test
  public void testModificationResultsCanBeDifferentFromCreation()
      throws IOException {

    String dCreation =
        ZonedDateTime.now(ZoneOffset.UTC).minusDays(3).format(DateTimeFormatter.ISO_INSTANT);
    ZonedDateTime pastInstant = ZonedDateTime.now(ZoneOffset.UTC).minusDays(30);
    Date pastDate = new Date(pastInstant.toInstant().toEpochMilli());

    String dModificationMinus1 = pastInstant.minusDays(1).format(DateTimeFormatter.ISO_INSTANT);
    String dModificationPlus1 = pastInstant.plusDays(1).format(DateTimeFormatter.ISO_INSTANT);
    String rangeToGetModifiedOnlyString = dModificationMinus1 + ";" + dModificationPlus1;

    setupRandomPIUser();
    Folder root = user.getRootFolder();
    StructuredDocument doc = recordMgr.createBasicDocument(root.getId(), user);
    doc.setModificationDate(pastDate);
    doc = recordMgr.save(doc, user).asStrucDoc();
    flushToSearchIndices();

    // Searching by creation date and choosing today should include the record
    WorkspaceListingConfig config =
        SearchTestUtils.createSimpleCreationDateSearchCfg(dCreation + ";");
    config.getPgCrit().setGetAllResults();
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertTrue(results.getResults().contains(doc));

    // Searching by creation date and choosing modification date should not
    // include the record
    config = SearchTestUtils.createSimpleCreationDateSearchCfg(";" + dModificationMinus1);
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertFalse(results.getResults().contains(doc));

    // Searching by modification date and choosing creation date should not
    // include the record
    config = SearchTestUtils.createSimpleModificationDateSearchCfg(dCreation + ";");
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertFalse(results.getResults().contains(doc));

    // Searching by modification date and choosing the modification date
    // should include the record
    config = SearchTestUtils.createSimpleModificationDateSearchCfg(rangeToGetModifiedOnlyString);
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertTrue(results.getResults().contains(doc));
  }

  /**
   * Test about advanced search results. Searching on single field to retrieve the right information
   */
  @Test
  public void testAdvancedSearchSingleTerm() throws IOException {
    setupRandomPIUser();
    Folder root = user.getRootFolder();
    StructuredDocument doc = recordMgr.createBasicDocument(root.getId(), user);
    doc.setName("Document Name Test");
    doc.getFields()
        .get(0)
        .setFieldData(" <p style='x'>lapwing INCENP-25 the ,bearded tit ,avocet, lapwing</p>");
    doc.setDocTag("doctagtest");
    recordMgr.save(doc, user);
    flushToSearchIndices();

    WorkspaceListingConfig config =
        createAdvSearchCfg(new String[] {TAG_SEARCH_OPTION}, new String[] {"lapwing"});
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().intValue());

    config = createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"lapwing"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by full text.
    config = createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"<style>"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().intValue());

    // Advanced search by full text.
    config = createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"<p>"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().intValue());

    // Advanced search by full text (fuzzy search).
    config = createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"bearde~"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);

    // Advanced search by full text (native lucene query).
    config =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"l: bearded AND avocet"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);

    // Advanced search by full text (native lucene query).
    config =
        createAdvSearchCfg(
            new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"l: bearded NOT experiment"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);

    // Advanced search by full text.
    config = createAdvSearchCfg(new String[] {TAG_SEARCH_OPTION}, new String[] {"doctagtest"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by name (using wildcard strategy/sensitive to capital letters).
    config = createAdvSearchCfg(new String[] {NAME_SEARCH_OPTION}, new String[] {"document*"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    config = createAdvSearchCfg(new String[] {NAME_SEARCH_OPTION}, new String[] {"Document*"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by form name (using wildcard strategy).
    config = createAdvSearchCfg(new String[] {FORM_SEARCH_OPTION}, new String[] {"Basic*"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by owner/user name.
    config =
        createAdvSearchCfg(new String[] {OWNER_SEARCH_OPTION}, new String[] {user.getUsername()});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
  }

  /**
   * Test about advanced search results. Creating multiple folders and records and searching them.
   */
  @Test
  public void testAdvancedSearchMultipleFolders() throws Exception {
    final int numFolders = IPagination.DEFAULT_RESULTS_PERPAGE, numRecords = 4;
    final int recordsPerPage = IPagination.DEFAULT_RESULTS_PERPAGE;

    setupRandomPIUser();
    Folder root = user.getRootFolder();

    RSForm anyForm = new RSForm("Any Form", "description", user);
    formDao.save(anyForm);

    ISearchResults<BaseRecord> results;
    addNFoldersAndMRecords(root, numFolders, numRecords, user, anyForm);

    flushToSearchIndices();

    // Advanced search by name of folder.
    WorkspaceListingConfig config =
        createAdvSearchCfg(new String[] {NAME_SEARCH_OPTION}, new String[] {"0f"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by name of document.
    config = createAdvSearchCfg(new String[] {NAME_SEARCH_OPTION}, new String[] {"0sd"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    // Advanced search by name over all folders.
    config = createAdvSearchCfg(new String[] {NAME_SEARCH_OPTION}, new String[] {"?f*"});
    config.getPgCrit().setResultsPerPage(recordsPerPage);
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(10, results.getTotalHits().intValue());

    // Advanced search by name.
    config = createAdvSearchCfg(new String[] {NAME_SEARCH_OPTION}, new String[] {"?sd*"});
    config.getPgCrit().setResultsPerPage(recordsPerPage);
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(numRecords, results.getTotalHits().intValue());

    String dFrom =
        ZonedDateTime.now(ZoneOffset.UTC).minusDays(3).format(DateTimeFormatter.ISO_INSTANT);
    String dTo =
        ZonedDateTime.now(ZoneOffset.UTC).plusDays(3).format(DateTimeFormatter.ISO_INSTANT);
    String dateSearchString = formatDateSearchString(dFrom, dTo);

    // Advanced search by modification dates (from date - to date).
    config =
        createAdvSearchCfg(
            new String[] {MODIFICATION_DATE_SEARCH_OPTION}, new String[] {dateSearchString});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    final int expectedCount = numFolders + 13;
    assertEquals(expectedCount, results.getTotalHits().intValue());
    assertEquals(recordsPerPage, results.getResults().size());

    // Advanced search by modification date 'from'.
    config =
        createAdvSearchCfg(
            new String[] {MODIFICATION_DATE_SEARCH_OPTION}, new String[] {dFrom + ";"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(expectedCount, results.getTotalHits().intValue());
    assertEquals(recordsPerPage, results.getResults().size());

    // Advanced search by creation dates (from date - to date).
    config =
        createAdvSearchCfg(
            new String[] {SearchConstants.CREATION_DATE_SEARCH_OPTION},
            new String[] {dateSearchString});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(expectedCount, results.getTotalHits().intValue());
    assertEquals(recordsPerPage, results.getResults().size());

    // Advanced search by creation date 'from'.
    config =
        createAdvSearchCfg(
            new String[] {SearchConstants.CREATION_DATE_SEARCH_OPTION}, new String[] {dFrom + ";"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(expectedCount, results.getTotalHits().intValue());
    assertEquals(recordsPerPage, results.getResults().size());

    // Search for basic document
    config = createAdvSearchCfg(new String[] {FORM_SEARCH_OPTION}, new String[] {"Basic*"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    final int basicDocCount = results != null ? results.getTotalHits().intValue() : 0;

    StructuredDocument doc =
        createBasicDocumentInRootFolderWithText(
            user, " <p style='x'>lapwing INCENP-25 the ,bearded tit ,avocet, lapwing</p>");
    doc.setName("DocumentNameTest");
    doc.setDocTag("doctagtest");
    recordMgr.save(doc, user);
    flushToSearchIndices();

    // Advanced search by modification date.
    config =
        createAdvSearchCfg(
            new String[] {MODIFICATION_DATE_SEARCH_OPTION}, new String[] {dFrom + ";"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(expectedCount + 1, results.getTotalHits().intValue());
    assertEquals(recordsPerPage, results.getResults().size());

    // Advanced search by form name (using wildcard strategy).
    config = createAdvSearchCfg(new String[] {FORM_SEARCH_OPTION}, new String[] {"basic document"});
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(basicDocCount + 1, results.getTotalHits().intValue());
  }

  private String formatDateSearchString(String dFrom, String dTo) {
    return dFrom + ";" + dTo;
  }

  /**
   * Test about advanced search and simple search by owner.
   *
   */
  @Test
  public void testSearchByOwner() throws IOException {
    setupRandomPIUser();
    flushToSearchIndices();

    // Advanced search
    WorkspaceListingConfig config =
        createAdvSearchCfg(new String[] {OWNER_SEARCH_OPTION}, new String[] {user.getUsername()});
    ISearchResults<BaseRecord> advancedResults = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(advancedResults);
    for (BaseRecord result : advancedResults.getResults()) {
      assertEquals(result.getOwner(), user);
    }

    // Simple search
    WorkspaceListingConfig input = createSimpleOwnerSearchCfg(user.getUsername());
    ISearchResults<BaseRecord> simpleResults = searchMgr.searchWorkspaceRecords(input, user);
    assertNotNull(simpleResults);
    for (BaseRecord result : simpleResults.getResults()) {
      assertEquals(result.getOwner(), user);
    }
    assertEquals(simpleResults.getTotalHits(), advancedResults.getTotalHits());
  }

  @Test
  public void testSearchByMultipleUsers() throws IOException {
    // Make a group with PI and userInGroup, and some random user otherUser.
    setupRandomPIUser();
    User pi = user;
    flushToSearchIndices();

    User userInGroup = createAndSaveUserIfNotExists("userInGroup");
    initialiseContentWithEmptyContent(userInGroup);

    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, userInGroup);

    User otherUser = createAndSaveUserIfNotExists("otherUser");
    initialiseContentWithEmptyContent(otherUser);

    RSpaceTestUtils.logoutCurrUserAndLoginAs(userInGroup.getUsername(), TESTPASSWD);
    String docNameUserA = getRandomName(10);
    StructuredDocument docUserA =
        createBasicDocumentInRootFolderWithText(userInGroup, docNameUserA);

    RSpaceTestUtils.logoutCurrUserAndLoginAs(otherUser.getUsername(), TESTPASSWD);
    String docNameUserB = getRandomName(10);
    StructuredDocument docUserB = createBasicDocumentInRootFolderWithText(otherUser, docNameUserB);

    RSpaceTestUtils.logoutCurrUserAndLoginAs(pi.getUsername(), TESTPASSWD);
    String docNameUserPi = getRandomName(10);
    StructuredDocument docPi = createBasicDocumentInRootFolderWithText(pi, docNameUserPi);

    flushToSearchIndices();
    // Do searches

    // Pi should be able to see their and userInGroup's files
    WorkspaceListingConfig input =
        createSimpleOwnerSearchCfg(pi.getUsername() + "," + userInGroup.getUsername());
    List<BaseRecord> results = searchMgr.searchWorkspaceRecords(input, pi).getResults();
    assertTrue(results.contains(docUserA));
    assertTrue(results.contains(docPi));
    assertFalse(results.contains(docUserB));

    // Pi shouldn't be able to see otherUser's files
    input = createSimpleOwnerSearchCfg(pi.getUsername() + "," + otherUser.getUsername());
    results = searchMgr.searchWorkspaceRecords(input, pi).getResults();
    assertFalse(results.contains(docUserA));
    assertTrue(results.contains(docPi));
    assertFalse(results.contains(docUserB));

    // userInGroup can only see their files
    input = createSimpleOwnerSearchCfg(pi.getUsername() + "," + otherUser.getUsername());
    results = searchMgr.searchWorkspaceRecords(input, userInGroup).getResults();
    assertTrue(results.isEmpty());
  }

  @Test
  public void testSearchByOwnerPermissions() throws IOException {
    setupRandomPIUser();
    flushToSearchIndices();

    // Simple search
    WorkspaceListingConfig input = createSimpleOwnerSearchCfg(user.getUsername());
    ISearchResults<BaseRecord> simpleSearch = searchMgr.searchWorkspaceRecords(input, user);
    assertNotNull(simpleSearch);

    for (BaseRecord br : simpleSearch.getResults()) {
      assertTrue(permissionUtils.isPermitted(br, PermissionType.READ, user));
      assertFalse(br.isDeleted());
      assertFalse(br.isInvisible());
    }
  }

  @Test
  public void testDeletedRecordsAreNotReturnedInSearchResults()
      throws IOException, IllegalAddChildOperation, DocumentAlreadyEditedException {

    setupRandomPIUser();
    final String searchTerm = "sametext";
    StructuredDocument userDoc = createBasicDocumentInRootFolderWithText(user, searchTerm);
    flushToSearchIndices();

    WorkspaceListingConfig config =
        createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {searchTerm});
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(1, results.getTotalHits().intValue());

    recordDeletionMgr.deleteRecord(userDoc.getParent().getId(), userDoc.getId(), user);
    results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().intValue());
  }

  @Test
  public void testSearchIsFiltered() throws Exception {
    final int n10 = 10;
    setupRandomPIUser();
    final String searchTerm = "sametext";
    // StructuredDocument userDoc =
    createBasicDocumentInRootFolderWithText(user, searchTerm);
    RSpaceTestUtils.logout();
    User user2 = createAndSaveUserIfNotExists(getRandomName(n10));

    initialiseContentWithEmptyContent(user2);
    StructuredDocument userDoc2 = createBasicDocumentInRootFolderWithText(user2, searchTerm);
    flushToSearchIndices();
    // will match 2 indices.
    WorkspaceListingConfig config =
        createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {searchTerm});
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user2);
    assertNotNull(results);
    // check we don't get other user's documents as well.
    assertEquals(userDoc2, results.getResults().get(0));
    assertEquals(1, results.getTotalHits().intValue());
  }

  @Test
  public void testPICanGetSearchResultsFromGroupMemberRecords()
      throws IllegalAddChildOperation, IOException {
    setupRandomPIUser();
    // setup a group with a pi(user) and aa default member('other')
    User other = createAndSaveUserIfNotExists("other");
    initialiseContentWithEmptyContent(other);
    Group group = createGroup("group", user);
    addUsersToGroup(user, group, other);

    // now we create a record in PIs folder..
    createBasicDocumentInRootFolderWithText(user, "pitext commontext");

    // .. and other's folder
    RSpaceTestUtils.logoutCurrUserAndLoginAs(other.getUsername(), TESTPASSWD);
    StructuredDocument sd2 = createBasicDocumentInRootFolderWithText(other, "othertext commontext");
    flushToSearchIndices();

    // other should only see his own hits:
    WorkspaceListingConfig config =
        createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"commontext"});
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, other);
    assertEquals(1, results.getTotalHits().intValue());
    assertEquals(sd2, results.getResults().get(0));
    // shouldn't see pi hits
    config = createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"pitext"});
    ISearchResults<BaseRecord> results2 = searchMgr.searchWorkspaceRecords(config, other);
    assertNotNull(results2);
    assertEquals(0, results2.getTotalHits().longValue());

    // now we'll login as PI:
    RSpaceTestUtils.logoutCurrUserAndLoginAs(user.getUsername(), TESTPASSWD);
    config =
        createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"commontext"});
    ISearchResults<BaseRecord> results3 = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(2, results3.getTotalHits().intValue());

    // should see 'other' hits
    config = createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"othertext"});
    ISearchResults<BaseRecord> results4 = searchMgr.searchWorkspaceRecords(config, user);
    assertEquals(1, results4.getTotalHits().intValue());
  }

  @Test
  public void testSharedRecordsSearchableByAll() throws IllegalAddChildOperation, IOException {
    setupRandomPIUser();
    // setup a group with a pi(user) and 2 default member('other' and
    // 'other2')
    User other = createAndSaveUserIfNotExists("other");
    initialiseContentWithEmptyContent(other);
    User other2 = createAndSaveUserIfNotExists("other2");
    initialiseContentWithEmptyContent(other2);

    Group group = createGroup("group", user);
    addUsersToGroup(user, group, other, other2);

    RSpaceTestUtils.logoutCurrUserAndLoginAs(other.getUsername(), TESTPASSWD);
    // now we create a record in other's folder
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(other, "other2text commontext");
    flushToSearchIndices();
    ShareConfigElement gcse = new ShareConfigElement(group.getId(), "read");

    // b4 sharing, other2 does not get a search hit in 'other's record
    RSpaceTestUtils.logoutCurrUserAndLoginAs(other2.getUsername(), TESTPASSWD);

    WorkspaceListingConfig config =
        createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"other2text"});
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, other2);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    // now, other shares with group
    RSpaceTestUtils.logoutCurrUserAndLoginAs(other.getUsername(), TESTPASSWD);
    sharingMgr.shareRecord(other, sd.getId(), new ShareConfigElement[] {gcse});

    // now should be visible to 'other2'
    RSpaceTestUtils.logoutCurrUserAndLoginAs(other2.getUsername(), TESTPASSWD);
    config =
        createAdvSearchCfg(new String[] {FULL_TEXT_SEARCH_OPTION}, new String[] {"other2text"});
    ISearchResults<BaseRecord> results3 = searchMgr.searchWorkspaceRecords(config, other2);
    assertEquals(1, results3.getTotalHits().intValue());
    assertEquals(sd, results3.getResults().get(0));
  }

  /**
   * Test to filter the searched results by shared with a specific user.
   */
  @Test
  public void testSharedRecordsWithUser() throws IllegalAddChildOperation, IOException {

    setupRandomPIUser();
    // setup a group with a pi(user) and 2 default member('other' and 'other2')
    User other = createAndSaveUserIfNotExists("other");
    initialiseContentWithEmptyContent(other);
    User other2 = createAndSaveUserIfNotExists("other2");
    initialiseContentWithEmptyContent(other2);

    Group group = createGroup("group", user);
    addUsersToGroup(user, group, other, other2);

    RSpaceTestUtils.logoutCurrUserAndLoginAs(other.getUsername(), TESTPASSWD);
    // now we create a record in other's folder
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(other, "other2text commontext");
    flushToSearchIndices();
    ShareConfigElement gcse = new ShareConfigElement(group.getId(), "read");

    // b4 sharing, other2 does not get a search hit in 'other's record
    RSpaceTestUtils.logoutCurrUserAndLoginAs(other2.getUsername(), TESTPASSWD);

    WorkspaceFilters filters = new WorkspaceFilters();
    filters.setSharedFilter(true);

    WorkspaceListingConfig config =
        createAdvSearchCfgWithFilters(
            new String[] {FULL_TEXT_SEARCH_OPTION},
            new String[] {"other2text"},
            SearchOperator.AND,
            filters);

    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, other2);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    // now, other shares with group
    RSpaceTestUtils.logoutCurrUserAndLoginAs(other.getUsername(), TESTPASSWD);
    sharingMgr.shareRecord(other, sd.getId(), new ShareConfigElement[] {gcse});

    // now should be visible to 'other2'
    RSpaceTestUtils.logoutCurrUserAndLoginAs(other2.getUsername(), TESTPASSWD);
    config =
        createAdvSearchCfgWithFilters(
            new String[] {FULL_TEXT_SEARCH_OPTION},
            new String[] {"other2text"},
            SearchOperator.AND,
            filters);

    ISearchResults<BaseRecord> results3 = searchMgr.searchWorkspaceRecords(config, other2);
    assertEquals(1, results3.getTotalHits().intValue());
    assertEquals(sd, results3.getResults().get(0));
  }

  /**
   * Test to filter the searched results by favorites.
   *
   */
  @Test
  public void testSearchedFavoritesRecords() throws IllegalAddChildOperation, IOException {

    final int n = 10;
    User user = createAndSaveUserIfNotExists("user");
    String randomText = getRandomName(n);

    initialiseContentWithEmptyContent(user);
    logoutAndLoginAs(user);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(user, randomText);
    favoritesManager.saveFavoriteRecord(sd.getId(), user.getId());
    flushToSearchIndices();

    WorkspaceFilters filters = new WorkspaceFilters();
    filters.setFavoritesFilter(true);
    WorkspaceListingConfig config =
        createAdvSearchCfgWithFilters(
            new String[] {FULL_TEXT_SEARCH_OPTION},
            new String[] {randomText},
            SearchOperator.AND,
            filters);
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().longValue());
  }

  @Test
  public void testSearchForTemplatesWithFilter()
      throws IOException, DocumentAlreadyEditedException {

    setupRandomPIUser();

    // setup a group with a pi(user) and 1 'other' member
    User other = createAndSaveUserIfNotExists("other");
    initialiseContentWithEmptyContent(other);

    Group group = createGroup("group", user);
    addUsersToGroup(user, group, other);

    logoutAndLoginAs(user);
    String randomText = getRandomName(10);
    StructuredDocument sd = createBasicDocumentInRootFolderWithText(user, randomText);
    StructuredDocument template =
        createTemplateFromDocumentAndAddtoTemplateFolder(sd.getId(), user);
    flushToSearchIndices();

    // default search should return both document and template
    WorkspaceFilters filters = new WorkspaceFilters();
    WorkspaceListingConfig config =
        createAdvSearchCfgWithFilters(
            new String[] {FULL_TEXT_SEARCH_OPTION},
            new String[] {randomText},
            SearchOperator.AND,
            filters);
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(2, results.getTotalHits().longValue());

    // search templates filter should just return the template
    filters.setTemplatesFilter(true);
    config =
        createAdvSearchCfgWithFilters(
            new String[] {FULL_TEXT_SEARCH_OPTION},
            new String[] {randomText},
            SearchOperator.AND,
            filters);
    ISearchResults<BaseRecord> results2 = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results2);
    assertEquals(1, results2.getTotalHits().longValue());
    assertEquals(template.getId(), results2.getFirstResult().getId());

    // now let's share doc and template with the group
    shareRecordWithGroup(user, group, sd);
    shareRecordWithGroup(user, group, template);

    // let's login as other user and run template filter search
    logoutAndLoginAs(other);
    ISearchResults<BaseRecord> results3 = searchMgr.searchWorkspaceRecords(config, other);
    assertNotNull(results3);
    assertEquals(1, results3.getTotalHits().longValue());
    assertEquals(template.getId(), results3.getFirstResult().getId());
  }

  /**
   * Test to filter the searched results by viewable items by default user.
   */
  @Test
  public void testSearchedAllViewableRecordsByUser() throws IOException {
    User user = createAndSaveUserIfNotExists("user");
    String randomText = getRandomName(10);

    initialiseContentWithEmptyContent(user);
    logoutAndLoginAs(user);
    createBasicDocumentInRootFolderWithText(user, randomText);
    flushToSearchIndices();

    WorkspaceFilters filters = new WorkspaceFilters();
    filters.setViewableItemsFilter(true);
    WorkspaceListingConfig config =
        createAdvSearchCfgWithFilters(
            new String[] {FULL_TEXT_SEARCH_OPTION},
            new String[] {randomText},
            SearchOperator.AND,
            filters);
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, user);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().longValue());
  }

  /**
   * Test to filter the searched results by viewable items by pi user.
   *
   */
  @Test
  public void testSearchedAllViewableRecordsByPIUser() throws IOException {
    final int n = 10;
    String randomText = getRandomName(n);

    User pi = createAndSaveUserIfNotExists("pi", Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    User userA = createAndSaveUserIfNotExists("userA");
    initialiseContentWithEmptyContent(userA);
    User userB = createAndSaveUserIfNotExists("userB");
    initialiseContentWithEmptyContent(userB);

    Group groupA = createGroup("groupA", pi);
    addUsersToGroup(pi, groupA, userA, userB);

    logoutAndLoginAs(userA);
    createBasicDocumentInRootFolderWithText(userA, randomText);
    flushToSearchIndices();

    logoutAndLoginAs(userB);
    createBasicDocumentInRootFolderWithText(userB, randomText);
    flushToSearchIndices();

    logoutAndLoginAs(pi);
    WorkspaceFilters filters = new WorkspaceFilters();
    filters.setViewableItemsFilter(true);
    WorkspaceListingConfig config =
        createAdvSearchCfgWithFilters(
            new String[] {FULL_TEXT_SEARCH_OPTION},
            new String[] {randomText},
            SearchOperator.AND,
            filters);
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, pi);
    assertNotNull(results);
    assertEquals(2, results.getTotalHits().longValue());
  }

  @Test
  public void testSearchSharedRecordsLabGroup()
      throws IllegalAddChildOperation, IOException {

    final int n = 10;
    String random = getRandomName(n);

    setupRandomPIUser();

    User userA = createAndSaveUserIfNotExists("userA");
    initialiseContentWithEmptyContent(userA);

    User userB = createAndSaveUserIfNotExists("userB");
    initialiseContentWithEmptyContent(userB);

    User userC = createAndSaveUserIfNotExists("userC");
    initialiseContentWithEmptyContent(userC);

    Group group = createGroup("group", user);
    addUsersToGroup(user, group, userA, userB, userC);

    RSpaceTestUtils.logoutCurrUserAndLoginAs(userA.getUsername(), TESTPASSWD);
    StructuredDocument docD = createBasicDocumentInRootFolderWithText(userA, random);
    flushToSearchIndices();

    RSpaceTestUtils.logoutCurrUserAndLoginAs(userB.getUsername(), TESTPASSWD);
    WorkspaceListingConfig config = createSimpleGeneralSearchCfg(random);
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, userB);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    RSpaceTestUtils.logoutCurrUserAndLoginAs(userA.getUsername(), TESTPASSWD);

    ShareConfigElement gcse = new ShareConfigElement();
    gcse.setUserId(userB.getId());
    gcse.setOperation("read");
    sharingMgr.shareRecord(userA, docD.getId(), new ShareConfigElement[] {gcse});

    RSpaceTestUtils.logoutCurrUserAndLoginAs(userB.getUsername(), TESTPASSWD);
    config = createSimpleGeneralSearchCfg(random);
    results = searchMgr.searchWorkspaceRecords(config, userB);
    assertEquals(1, results.getTotalHits().intValue());

    RSpaceTestUtils.logoutCurrUserAndLoginAs(userC.getUsername(), TESTPASSWD);
    config = createSimpleGeneralSearchCfg(random);
    results = searchMgr.searchWorkspaceRecords(config, userC);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    RSpaceTestUtils.logoutCurrUserAndLoginAs(userA.getUsername(), TESTPASSWD);
    sharingMgr.unshareRecord(userA, docD.getId(), new ShareConfigElement[] {gcse});

    RSpaceTestUtils.logoutCurrUserAndLoginAs(userB.getUsername(), TESTPASSWD);
    config = createSimpleGeneralSearchCfg(random);
    results = searchMgr.searchWorkspaceRecords(config, userB);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());
  }

  @Test
  public void testSearchSharedRecordsCollaborationGroup()
      throws IllegalAddChildOperation, IOException {

    final int n = 10;
    String random1 = getRandomName(n);
    String random2 = getRandomName(n);

    User piA = createAndSaveUserIfNotExists("piA", Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piA);
    User userA = createAndSaveUserIfNotExists("userA");
    initialiseContentWithEmptyContent(userA);
    User userB = createAndSaveUserIfNotExists("userB");
    initialiseContentWithEmptyContent(userB);

    User piB = createAndSaveUserIfNotExists("piB", Constants.PI_ROLE);
    initialiseContentWithEmptyContent(piB);
    User userX = createAndSaveUserIfNotExists("userX");
    initialiseContentWithEmptyContent(userX);
    User userY = createAndSaveUserIfNotExists("userY");
    initialiseContentWithEmptyContent(userY);

    Group groupA = createGroup("groupA", piA);
    addUsersToGroup(piA, groupA, userA, userB);

    Group groupB = createGroup("groupB", piB);
    addUsersToGroup(piB, groupB, userX, userY);

    RSpaceTestUtils.logoutCurrUserAndLoginAs(piA.getUsername(), TESTPASSWD);
    Group collabGroup = createCollabGroupBetweenGroups(groupA, groupB);
    addUsersToGroup(piA, collabGroup, userA, userX);

    RSpaceTestUtils.logoutCurrUserAndLoginAs(userA.getUsername(), TESTPASSWD);
    StructuredDocument docD1 = createBasicDocumentInRootFolderWithText(userA, random1);
    createBasicDocumentInRootFolderWithText(userA, random2);
    flushToSearchIndices();

    ShareConfigElement gcse = new ShareConfigElement(collabGroup.getId(), "read");
    sharingMgr.shareRecord(userA, docD1.getId(), new ShareConfigElement[] {gcse});

    RSpaceTestUtils.logoutCurrUserAndLoginAs(userX.getUsername(), TESTPASSWD);
    WorkspaceListingConfig config = createSimpleGeneralSearchCfg(random1);
    ISearchResults<BaseRecord> results = searchMgr.searchWorkspaceRecords(config, userX);
    assertEquals(1, results.getTotalHits().intValue());

    config = createSimpleGeneralSearchCfg(random2);
    results = searchMgr.searchWorkspaceRecords(config, userX);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().longValue());

    RSpaceTestUtils.logoutCurrUserAndLoginAs(piB.getUsername(), TESTPASSWD);
    config = createSimpleGeneralSearchCfg(random1);
    results = searchMgr.searchWorkspaceRecords(config, piB);
    assertEquals(1, results.getTotalHits().intValue());

    // Now the groupB PI user can't see groupA
    // documents if they haven't been shared previously.
    config = createSimpleGeneralSearchCfg(random2);
    results = searchMgr.searchWorkspaceRecords(config, piB);
    assertEquals(0, results.getTotalHits().intValue());
  }

  @Test
  public void testFilterSearchResults() throws Exception {

    final int length = 10, endIndex = 3;
    final int numFolders = 10, numRecords = 4;
    User user = createAndSaveUserIfNotExists(getRandomName(length));
    initialiseContentWithExampleContent(user);
    RSpaceTestUtils.logoutCurrUserAndLoginAs(user.getUsername(), TESTPASSWD);

    Folder root = folderDao.getRootRecordForUser(user);

    // RSForm anyForm = formDao.getAll().get(0);
    RSForm anyForm = new RSForm("Example Form", "Some description", user);
    formDao.save(anyForm);

    PaginationCriteria<BaseRecord> pg = PaginationCriteria.createDefaultForClass(BaseRecord.class);
    long numb4InDB =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    assertTrue(numb4InDB > 0L);
    // add 10 folders & 4 docs
    addNFoldersAndMRecords(root, numFolders, numRecords, user, anyForm);
    flushToSearchIndices();

    // test document with wildcard name
    WorkspaceListingConfig input =
        new WorkspaceListingConfig(
            pg, new String[] {NAME_SEARCH_OPTION}, new String[] {"1sd"}, -1L, false);
    ISearchResults<BaseRecord> res2 = searchMgr.searchWorkspaceRecords(input, user);
    assertEquals(1L, res2.getHits().longValue());

    // test form
    // we need also to check this works if there are multiple forms with the same name
    RSForm newForm = TestFactory.createAnyForm(anyForm.getName());
    newForm.setOwner(user);
    formDao.save(newForm);
    flushToSearchIndices();

    input =
        new WorkspaceListingConfig(
            pg, new String[] {FORM_SEARCH_OPTION}, new String[] {anyForm.getName()}, -1L, false);

    ISearchResults<BaseRecord> res3 = searchMgr.searchWorkspaceRecords(input, user);
    assertEquals(numRecords, res3.getHits().longValue());

    // use a different form to see if sort working
    recordMgr.createNewStructuredDocument(root.getId(), newForm.getId(), user);
    flushToSearchIndices();

    input =
        new WorkspaceListingConfig(
            pg,
            new String[] {FORM_SEARCH_OPTION},
            new String[] {newForm.getName()},
            root.getId(),
            false);
    ISearchResults<BaseRecord> res5 = searchMgr.searchWorkspaceRecords(input, user);
    // 4 original form numRecords + 1 created here
    assertEquals(numRecords + 1, res5.getHits().intValue());

    // partial form name with wildcard should get same results as well
    input =
        new WorkspaceListingConfig(
            pg,
            new String[] {FORM_SEARCH_OPTION},
            new String[] {newForm.getName().substring(0, endIndex) + SearchConstants.WILDCARD},
            root.getId(),
            false);

    ISearchResults<BaseRecord> res7 = searchMgr.searchWorkspaceRecords(input, user);
    // 4 original form numRecords + 1 created here
    assertEquals(numRecords + 1, res7.getHits().longValue());

    // test
    input =
        new WorkspaceListingConfig(
            pg, new String[] {NAME_SEARCH_OPTION}, new String[] {"Shared"}, -1L, false);
    ISearchResults<BaseRecord> res4 = searchMgr.searchWorkspaceRecords(input, user);
    assertEquals(1L, res4.getHits().longValue());

    // with unknown form name, get no results - no documents can be created
    // with an unknown form
    input =
        new WorkspaceListingConfig(
            pg, new String[] {FORM_SEARCH_OPTION}, new String[] {"ZXT1234"}, -1L, false);

    ISearchResults<BaseRecord> res6 = searchMgr.searchWorkspaceRecords(input, user);
    assertNotNull(res6);
    assertEquals(0, res6.getTotalHits().intValue());

    // test document with commas in name:
    StructuredDocument docWithCommaInName =
        recordMgr.createNewStructuredDocument(root.getId(), anyForm.getId(), user);
    final String SEARCH_TERM_WITH_COMMA = "Something, and everything, and nothing";
    docWithCommaInName.setName(SEARCH_TERM_WITH_COMMA);
    recordMgr.save(docWithCommaInName, user);
    flushToSearchIndices();
    WorkspaceListingConfig input2 =
        new WorkspaceListingConfig(
            pg,
            new String[] {NAME_SEARCH_OPTION},
            new String[] {SEARCH_TERM_WITH_COMMA},
            -1L,
            false);
    ISearchResults<BaseRecord> res_Comma = searchMgr.searchWorkspaceRecords(input2, user);
    assertEquals(docWithCommaInName, res_Comma.getFirstResult());
  }

  @Test
  public void testSysadminSearch() throws Exception {
    setupRandomPIUser();
    logoutAndLoginAs(user);
    createBasicDocumentInRootFolderWithText(user, "avocet");
    User sysadmin = logoutAndLoginAsSysAdmin();
    contentInitializer.init(sysadmin.getId());
    createBasicDocumentInRootFolderWithText(sysadmin, "avocet");
    flushToSearchIndices();
    ISearchResults<BaseRecord> results =
        searchMgr.searchWorkspaceRecords(createSimpleFullTextSearchCfg("avocet"), sysadmin);

    assertEquals(2, results.getTotalHits().intValue());
    assertTrue(results.getResults().stream().anyMatch(br -> br.getOwner().equals(user)));
  }

  @Test
  public void testSimpleNameSearchSortByDateOrName() throws Exception {

    final long numEntries = 3L;
    final int length = 10, numFolders = 8, numRecords = 4;
    User user = createAndSaveUserIfNotExists(getRandomName(length));
    initialiseContentWithExampleContent(user);
    RSpaceTestUtils.logoutCurrUserAndLoginAs(user.getUsername(), TESTPASSWD);

    Folder root = user.getRootFolder();
    RSForm anyForm = formDao.getAll().get(0);
    long numb4InDB =
        recordMgr.listFolderRecords(root.getId(), DEFAULT_RECORD_PAGINATION).getTotalHits();
    assertTrue(numb4InDB > 0L);
    // add 8 folders & 4 docs
    addNFoldersAndMRecords(root, numFolders, numRecords, user, anyForm);

    flushToSearchIndices();

    PaginationCriteria<BaseRecord> pg = PaginationCriteria.createDefaultForClass(BaseRecord.class);

    // Test order by 4 permutations of name / date and ASC / DESC
    pg.setOrderBy("name");
    pg.setSortOrder(SortOrder.DESC);
    WorkspaceListingConfig input =
        new WorkspaceListingConfig(
            pg, new String[] {NAME_SEARCH_OPTION}, new String[] {"Ent*"}, -1L, false);

    ISearchResults<BaseRecord> res = searchMgr.searchWorkspaceRecords(input, user);
    assertEquals(numEntries, res.getHits().longValue());
    assertTrue(res.getResults().get(0).getName().compareTo(res.getResults().get(1).getName()) > 0);

    pg.setSortOrder(SortOrder.ASC);
    res = searchMgr.searchWorkspaceRecords(input, user);
    assertEquals(numEntries, res.getHits().longValue());
    assertTrue(res.getResults().get(0).getName().compareTo(res.getResults().get(1).getName()) < 0);

    pg.setOrderBy(SearchUtils.BASE_RECORD_ORDER_BY_LAST_MODIFIED);
    pg.setSortOrder(SortOrder.DESC);
    res = searchMgr.searchWorkspaceRecords(input, user);
    assertEquals(numEntries, res.getHits().longValue());
    assertTrue(
        res.getResults()
                .get(0)
                .getModificationDateMillis()
                .compareTo(res.getResults().get(1).getModificationDateMillis())
            > 0);

    pg.setSortOrder(SortOrder.ASC);
    res = searchMgr.searchWorkspaceRecords(input, user);
    assertEquals(numEntries, res.getHits().longValue());
    assertTrue(
        res.getResults()
                .get(0)
                .getModificationDate()
                .compareTo(res.getResults().get(1).getModificationDate())
            < 0);
  }


  /**
   * Test consists of: Creating 2 forms: 'Example form' (and 4 documents from it), 'Another form'
   * (with 2 documents from it) Searching by 'Example form' global id should result in 4 documents
   * Updating 'Example form' with a new field (it gets assigned a new global id) Adding 5 more
   * documents created from the updated form Searching by original global id and updated global id
   * should both return 4 + 5 documents
   *
   */
  @Test
  public void testFormSearchByGlobalId() throws Exception {
    final int length = 10;
    final int numFolders = 0, numRecordsAnother = 3, numRecords = 4, numRecordsUpdated = 5;
    User user = createAndSaveUserIfNotExists(getRandomName(length));
    initialiseContentWithExampleContent(user);
    RSpaceTestUtils.logoutCurrUserAndLoginAs(user.getUsername(), TESTPASSWD);

    Folder root = folderDao.getRootRecordForUser(user);

    RSForm form = new RSForm("Example Form", "Some description", user);
    form.publish();
    formMgr.save(form, user);

    // Add 0 folders & 4 docs
    addNFoldersAndMRecords(root, numFolders, numRecords, user, form);
    flushToSearchIndices();

    // Create another form with 3 documents
    RSForm anotherForm = new RSForm("Another Form", "Some description", user);
    anotherForm.publish();
    formMgr.save(anotherForm, user);
    addNFoldersAndMRecords(root, numFolders, numRecordsAnother, user, anotherForm);
    flushToSearchIndices();

    // Search by global id should have 4 results (Form field)
    ISearchResults<BaseRecord> results =
        searchMgr.searchWorkspaceRecords(
            createSimpleFormSearchCfg(form.getOid().getIdString()), user);
    assertEquals(numRecords, results.getResults().size());

    // Search by global id should have 4 results (All field)
    results =
        searchMgr.searchWorkspaceRecords(
            createSimpleGeneralSearchCfg(form.getOid().getIdString()), user);
    assertEquals(numRecords, results.getResults().size());

    // Modify the form
    RSForm formToEdit = formMgr.getForEditing(form.getId(), user, anySessionTracker());
    assertTrue(formToEdit.isTemporary());
    StringFieldDTO<StringFieldForm> dto =
        new StringFieldDTO<>("New String Field", "no", "abc");
    formMgr.createFieldForm(dto, formToEdit.getId(), user);

    // Update the form
    formMgr.updateVersion(formToEdit.getId(), user);

    // Get the updated form
    RSForm updatedForm = formMgr.get(formToEdit.getId(), user);

    // Add 0 folders & 5 docs from the updated form
    addNFoldersAndMRecords(root, numFolders, numRecordsUpdated, user, updatedForm);
    flushToSearchIndices();

    // Search by global id (original form global id) should have results from both versions of the
    // form (Form field)
    results =
        searchMgr.searchWorkspaceRecords(
            createSimpleFormSearchCfg(form.getOid().getIdString()), user);
    assertEquals(numRecords + numRecordsUpdated, results.getResults().size());

    // Search by global id (original form global id) should have results from both versions of the
    // form (All field)
    results =
        searchMgr.searchWorkspaceRecords(
            createSimpleGeneralSearchCfg(form.getOid().getIdString()), user);
    assertEquals(numRecords + numRecordsUpdated, results.getResults().size());

    // Search by global id (updated form global id) should have results from both versions of the
    // form (Form field)
    results =
        searchMgr.searchWorkspaceRecords(
            createSimpleFormSearchCfg(updatedForm.getOid().getIdString()), user);
    assertEquals(numRecords + numRecordsUpdated, results.getResults().size());

    // Search by global id (updated form global id) should have results from both versions of the
    // form (All field)
    results =
        searchMgr.searchWorkspaceRecords(
            createSimpleGeneralSearchCfg(updatedForm.getOid().getIdString()), user);
    assertEquals(numRecords + numRecordsUpdated, results.getResults().size());
  }

  @Test(expected = AuthorizationException.class)
  public void testFormSearchForAnotherUsersForm() throws Exception {
    final int length = 10, numFolders = 0, numRecords = 1;
    User user = createAndSaveUserIfNotExists(getRandomName(length));
    initialiseContentWithExampleContent(user);

    User user2 = createAndSaveUserIfNotExists(getRandomName(length));
    initialiseContentWithExampleContent(user2);

    // Create a form with some documents
    RSpaceTestUtils.logoutCurrUserAndLoginAs(user.getUsername(), TESTPASSWD);
    Folder root = folderDao.getRootRecordForUser(user);
    RSForm form = new RSForm("Example Form", "Some description", user);
    form.publish();
    formMgr.save(form, user);
    addNFoldersAndMRecords(root, numFolders, numRecords, user, form);
    flushToSearchIndices();

    // Login as user2 and search for the documents from that form
    RSpaceTestUtils.logoutCurrUserAndLoginAs(user2.getUsername(), TESTPASSWD);
    searchMgr.searchWorkspaceRecords(createSimpleFormSearchCfg(form.getOid().getIdString()), user2);
  }

  @Test(expected = AuthorizationException.class)
  public void testFormSearchForNotExistingForm() throws Exception {
    final int length = 10;
    User user = createAndSaveUserIfNotExists(getRandomName(length));
    initialiseContentWithExampleContent(user);
    RSpaceTestUtils.logoutCurrUserAndLoginAs(user.getUsername(), TESTPASSWD);
    searchMgr.searchWorkspaceRecords(createSimpleFormSearchCfg("FM425346143"), user);
  }

  @Test
  public void testUserRecordsSimpleQuerySearch() throws Exception {

    setupRandomUser();
    Folder root = user.getRootFolder();

    StructuredDocument doc = recordMgr.createBasicDocument(root.getId(), user);
    doc.setName("Document Name Test");
    doc.getFields()
        .get(0)
        .setFieldData(" <p style='x'>lapwing INCENP-25 the ,bearded tit ,avocet, lapwing</p>");
    recordMgr.save(doc, user);
    flushToSearchIndices();

    ISearchResults<BaseRecord> results =
        searchMgr.searchUserRecordsWithSimpleQuery(user, "qwerty", null);
    assertNotNull(results);
    assertEquals(0, results.getTotalHits().intValue());

    results = searchMgr.searchUserRecordsWithSimpleQuery(user, "lapwing", null);
    assertNotNull(results);
    assertEquals(1, results.getTotalHits().intValue());

    assertExceptionThrown(
        () -> searchMgr.searchUserRecordsWithSimpleQuery(user, "", null),
        IllegalArgumentException.class);
  }

  @Test
  public void testTaggedRecordsFoundBySimpleQuerySearch() throws Exception {
    setupRandomUser();
    Folder root = user.getRootFolder();

    StructuredDocument doc =
        recordMgr.createBasicDocumentWithContent(
            root.getId(), "Document Name Test", user, "test content");
    documentTagManager.saveTag(doc.getId(), "testDocTag, tagged", user);

    Notebook notebook =
        folderMgr.createNewNotebook(
            root.getId(), "Notebook Name Test", new DefaultRecordContext(), user);
    documentTagManager.saveTag(notebook.getId(), "testNotebookTag, tagged", user);

    Folder folder = folderMgr.createNewFolder(root.getId(), "Folder Name Test", user);
    documentTagManager.saveTag(folder.getId(), "testFolderTag, tagged", user);

    flushToSearchIndices();

    ISearchResults<BaseRecord> results =
        searchMgr.searchUserRecordsWithSimpleQuery(user, "testDocTag", null);
    assertNotNull(results);
    // one hit from created doc, another possible from auto-generated ontology tags doc
    assertTrue("testDocTag should be found", results.getTotalHits().intValue() > 0);

    results = searchMgr.searchUserRecordsWithSimpleQuery(user, "testFolderTag", null);
    assertNotNull(results);
    // one hit from created folder, another possible from auto-generated ontology tags doc
    assertTrue("testFolderTag should be found", results.getTotalHits().intValue() > 0);

    results = searchMgr.searchUserRecordsWithSimpleQuery(user, "testNotebookTag", null);
    assertNotNull(results);
    // one hit from created notebook, another possible from auto-generated ontology tags doc
    assertTrue("testNotebookTag should be found", results.getTotalHits().intValue() > 0);

    results = searchMgr.searchUserRecordsWithSimpleQuery(user, "tagged", null);
    assertNotNull(results);
    // three hits from created records + one possible from auto-generated ontology doc
    assertTrue("tagged content should be found", results.getTotalHits().intValue() >= 3);
  }

  @Test
  public void invSampleSearchHandlesDeleted() {
    // create two samples named "mySample"
    User u = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample1 = createBasicSampleForUser(u);
    createBasicSampleForUser(u);
    flushToSearchIndices();

    // both samples and subsamples are found by default search
    ApiInventorySearchResult result =
        searchMgr.searchInventoryWithSimpleQuery("myS*", null, null, null, null, null, u);
    assertEquals(4, result.getTotalHits().intValue());

    // mark first sample as deleted
    sampleApiMgr.markSampleAsDeleted(sample1.getId(), false, u);

    // default search doesn't return deleted sample, nor subsample of deleted sample
    result = searchMgr.searchInventoryWithSimpleQuery("myS*", null, null, null, null, null, u);
    assertEquals(2, result.getTotalHits().intValue());

    // same when exclude deleted is chosen explicitly
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "myS*", null, null, null, InventorySearchDeletedOption.EXCLUDE, null, u);
    assertEquals(2, result.getTotalHits().intValue());

    // with "include" both active and deleted are returned
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "myS*", null, null, null, InventorySearchDeletedOption.INCLUDE, null, u);
    assertEquals(4, result.getTotalHits().intValue());

    // can also search just deleted
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "myS*", null, null, null, InventorySearchDeletedOption.DELETED_ONLY, null, u);
    assertEquals(2, result.getTotalHits().intValue());
  }

  @Test
  public void invSampleSearchHandlesBaskets() {

    // create basic sample with subsample
    User u = createInitAndLoginAnyUser();
    ApiSampleWithFullSubSamples sample1 = createBasicSampleForUser(u);
    flushToSearchIndices();

    // put subsample into basket
    String subSampleGlobalId = sample1.getSubSamples().get(0).getGlobalId();
    ApiBasket basketWithSubSample = createBasicBasketWithItems(List.of(subSampleGlobalId), u);
    assertEquals(1, basketWithSubSample.getItemCount());

    // create another empty basket
    ApiBasket emptyBasket = createBasicBasketWithItems(null, u);
    assertEquals(0, emptyBasket.getItemCount());

    // both samples and subsamples are found by default search
    ApiInventorySearchResult result =
        searchMgr.searchInventoryWithSimpleQuery("myS*", null, null, null, null, null, u);
    assertEquals(2, result.getTotalHits().intValue());

    // search with 1st basket finds subsample
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "myS*",
            null,
            new GlobalIdentifier(basketWithSubSample.getGlobalId()),
            null,
            null,
            null,
            u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(subSampleGlobalId, result.getRecords().get(0).getGlobalId());

    // search with empty basket finds nothing
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "myS*", null, new GlobalIdentifier(emptyBasket.getGlobalId()), null, null, null, u);
    assertEquals(0, result.getTotalHits().intValue());
  }

  @Test
  public void invSampleSimpleQuerySearch() throws Exception {
    User u = createInitAndLoginAnyUser();

    // sample named "mySample"
    ApiSampleWithFullSubSamples sample1 = createBasicSampleForUser(u);
    ApiExtraField extraField = new ApiExtraField();
    extraField.setNewFieldRequest(true);
    extraField.setContent("extra field content");
    sample1.getExtraFields().add(extraField);
    sample1.getBarcodes().add(new ApiBarcode("b456"));
    sample1.getBarcodes().get(0).setNewBarcodeRequest(true);
    sampleApiMgr.updateApiSample(sample1, u);

    // sample named "mySample #2", with tags
    ApiSampleWithFullSubSamples sample2 = createComplexSampleForUser(u);
    sample2.setName("xyz mySample complex #2");
    sample2.setApiTagInfo("test1, test2, test3");
    sample2.setDescription("desc1");
    sample2.getFields().get(5).setContent("junit sample field content");
    ApiBarcode barcodeWithDesc = new ApiBarcode();
    barcodeWithDesc.setDescription("part of b456 serie");
    barcodeWithDesc.setNewBarcodeRequest(true);
    sample2.getBarcodes().add(barcodeWithDesc);
    sampleApiMgr.updateApiSample(sample2, u);

    flushToSearchIndices();

    // no results for "unknown" name
    ApiInventorySearchResult result =
        searchMgr.searchInventoryWithSimpleQuery("unknown", null, null, null, null, null, u);
    assertEquals(0, result.getTotalHits().intValue());

    // two results for "mysample" name, default ordering (name asc)
    result = searchMgr.searchInventoryWithSimpleQuery("mysample", null, null, null, null, null, u);
    assertEquals(2, result.getRecords().size());
    assertEquals(2, result.getTotalHits().intValue());
    assertEquals(sample1.getGlobalId(), result.getRecords().get(0).getGlobalId());

    // one result if we force 1-per-page pagination and default ordering (desc)
    PaginationCriteria<InventoryRecord> pg =
        PaginationCriteria.createDefaultForClass(InventoryRecord.class);
    pg.setResultsPerPage(1);
    pg.setOrderBy(SearchUtils.ORDER_BY_NAME);
    result = searchMgr.searchInventoryWithSimpleQuery("mysample", null, null, null, null, pg, u);
    assertEquals(1, result.getRecords().size());
    assertEquals(2, result.getTotalHits().intValue());
    assertEquals(sample2.getGlobalId(), result.getRecords().get(0).getGlobalId());

    // one result for "test2" (from tag)
    result = searchMgr.searchInventoryWithSimpleQuery("test2", null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(sample2.getId(), result.getRecords().get(0).getId());

    // one result for "desc1"
    result = searchMgr.searchInventoryWithSimpleQuery("desc1", null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(sample2.getId(), result.getRecords().get(0).getId());

    // one result for "template" (from template field content)
    result = searchMgr.searchInventoryWithSimpleQuery("junit", null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(sample2.getId(), result.getRecords().get(0).getId());

    // two results from "field content" (from template field and extra field)
    result =
        searchMgr.searchInventoryWithSimpleQuery("field content", null, null, null, null, null, u);
    assertEquals(2, result.getTotalHits().intValue());

    // two results from "B456" (from sample barcode data and description)
    result = searchMgr.searchInventoryWithSimpleQuery("B456", null, null, null, null, null, u);
    assertEquals(2, result.getTotalHits().intValue());

    // no results from partial search term
    result = searchMgr.searchInventoryWithSimpleQuery("jun", null, null, null, null, null, u);
    assertEquals(0, result.getTotalHits().intValue());
    result = searchMgr.searchInventoryWithSimpleQuery("jun?", null, null, null, null, null, u);
    assertEquals(0, result.getTotalHits().intValue());

    // all results from wildcard search
    result = searchMgr.searchInventoryWithSimpleQuery("jun??", null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    result = searchMgr.searchInventoryWithSimpleQuery("jun*", null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());

    // full-wildcard search rejected
    assertExceptionThrown(
        () -> searchMgr.searchInventoryWithSimpleQuery("**", null, null, null, null, null, u),
        IllegalArgumentException.class);
  }

  @Test
  public void inventorySimpleQuerySearch() {
    User u = createInitAndLoginAnyUser();

    // sample named "mySample"
    ApiSampleWithFullSubSamples sample1 = createBasicSampleForUser(u);
    ApiExtraField extraField = new ApiExtraField();
    extraField.setNewFieldRequest(true);
    extraField.setContent("extra field content");
    sample1.getExtraFields().add(extraField);
    sampleApiMgr.updateApiSample(sample1, u);

    // sample named "mySample #2", with tags
    ApiSampleWithFullSubSamples sample2 = createComplexSampleForUser(u);
    sample2.setName("mySample complex #2");
    sample2.setApiTagInfo("test1, test2, test3");
    sample2.setDescription("desc1");
    sample2.getFields().get(5).setContent("template field content");
    sampleApiMgr.updateApiSample(sample2, u);
    ApiSubSample apiSubSample = sample2.getSubSamples().get(0);
    apiSubSample.setApiTagInfo("subsampleTag1, test2, test3");
    subSampleApiMgr.updateApiSubSample(apiSubSample, u);

    // container named "my container", with tags
    ApiContainer apiContainer = new ApiContainer();
    apiContainer.setName("container 1");
    apiContainer.setApiTagInfo("test2");
    ApiContainer topContainer = containerApiMgr.createNewApiContainer(apiContainer, u);
    // two subcontainers inside first container
    ApiContainer apiSubContainer = new ApiContainer();
    apiSubContainer.setName("container 2");
    apiSubContainer.setParentContainer(topContainer);
    containerApiMgr.createNewApiContainer(apiSubContainer, u);
    apiSubContainer.setName("container 3");
    containerApiMgr.createNewApiContainer(apiSubContainer, u);

    flushToSearchIndices();

    // search that matches both samples
    ApiInventorySearchResult result =
        searchMgr.searchInventoryWithSimpleQuery("mySample", null, null, null, null, null, u);
    assertEquals(2, result.getTotalHits().intValue());

    // search matching 2nd subsample
    result =
        searchMgr.searchInventoryWithSimpleQuery("subsampleTag1", null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(apiSubSample.getGlobalId(), result.getRecords().get(0).getGlobalId());

    // search matching 2nd sample, 2nd subsample and a container
    result = searchMgr.searchInventoryWithSimpleQuery("test2", null, null, null, null, null, u);
    assertEquals(3, result.getTotalHits().intValue());

    // limit search to samples
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "test2", InventorySearchType.SAMPLE, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(sample2.getGlobalId(), result.getRecords().get(0).getGlobalId());

    // limit search to subsamples
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "test2", InventorySearchType.SUBSAMPLE, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(apiSubSample.getGlobalId(), result.getRecords().get(0).getGlobalId());

    // limit search to containers
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "test2", InventorySearchType.CONTAINER, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(topContainer.getGlobalId(), result.getRecords().get(0).getGlobalId());

    // search inside the container
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "cont*", null, new GlobalIdentifier(topContainer.getGlobalId()), null, null, null, u);
    assertEquals(2, result.getTotalHits().intValue());
    assertEquals("container 2", result.getRecords().get(0).getName());
    assertEquals("container 3", result.getRecords().get(1).getName());

    // search inside the sample
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "my*", null, new GlobalIdentifier(sample2.getGlobalId()), null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(apiSubSample.getTags(), result.getRecords().get(0).getTags());
  }

  @Test
  public void inventorySimpleSearchByUrl_PRT566() {
    User u = createInitAndLoginAnyUser();

    // sample with various test.one urls
    ApiSampleWithFullSubSamples sample1 = createBasicSampleForUser(u);
    sample1.setDescription("http://test.one/a");
    sample1.setApiTagInfo("www.test.one/a");
    sample1.getBarcodes().add(new ApiBarcode("https://test.one/a"));
    sample1.getBarcodes().get(0).setNewBarcodeRequest(true);
    sampleApiMgr.updateApiSample(sample1, u);

    // sample with various test.two urls
    ApiSampleWithFullSubSamples sample2 = createComplexSampleForUser(u);
    ApiSubSample apiSubSample = sample2.getSubSamples().get(0);
    apiSubSample.setDescription("http://test.two/b");
    apiSubSample.setApiTagInfo("www.test.two/b");
    apiSubSample.getBarcodes().add(new ApiBarcode("https://test.two/b"));
    apiSubSample.getBarcodes().get(0).setNewBarcodeRequest(true);
    subSampleApiMgr.updateApiSubSample(apiSubSample, u);

    flushToSearchIndices();

    // search that matches both samples
    ApiInventorySearchResult result =
        searchMgr.searchInventoryWithSimpleQuery(
            "http://test.one/a", null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(sample1.getGlobalId(), result.getRecords().get(0).getGlobalId());

    // search matching 2nd subsample through barcode match
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "https://test.two/b", null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(apiSubSample.getGlobalId(), result.getRecords().get(0).getGlobalId());
    // serach matching 2nd subsample through tags match
    result =
        searchMgr.searchInventoryWithSimpleQuery("www.test.two/b", null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(apiSubSample.getGlobalId(), result.getRecords().get(0).getGlobalId());

    // some partial search still matching one or another
    result = searchMgr.searchInventoryWithSimpleQuery("test.one", null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(sample1.getGlobalId(), result.getRecords().get(0).getGlobalId());
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "http://test.two", null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(apiSubSample.getGlobalId(), result.getRecords().get(0).getGlobalId());
  }

  @Test
  public void invTemplateSearch() {
    User user = createInitAndLoginAnyUser();

    // sample from custom template
    ApiSampleWithFullSubSamples sampleFromCustomTemplate = createBasicSampleTemplateAndSample(user);

    // complex sample named "junit test sample"
    ApiSampleWithFullSubSamples complexSample = createComplexSampleForUser(user);
    complexSample.setName("junit test complex sample");
    sampleApiMgr.updateApiSample(complexSample, user);

    flushToSearchIndices();

    // default search should finds all types, including templates
    ApiInventorySearchResult result =
        searchMgr.searchInventoryWithSimpleQuery("junit", null, null, null, null, null, user);
    assertEquals(4, result.getTotalHits().intValue());
    assertEquals("junit test template", result.getRecords().get(0).getName());
    assertEquals(ApiInventoryRecordType.SAMPLE_TEMPLATE, result.getRecords().get(0).getType());
    assertEquals("junit test complex sample", result.getRecords().get(1).getName());
    assertEquals("sample from junit test template", result.getRecords().get(2).getName());
    assertEquals("sample from junit test template.01", result.getRecords().get(3).getName());

    // try templates-excluded search
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "junit", InventorySearchType.SAMPLE, null, null, null, null, user);
    assertEquals(2, result.getTotalHits().intValue());
    assertEquals("junit test complex sample", result.getRecords().get(0).getName());
    assertEquals("sample from junit test template", result.getRecords().get(1).getName());

    // try templates-only search
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "junit", InventorySearchType.TEMPLATE, null, null, null, null, user);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals("junit test template", result.getRecords().get(0).getName());

    // search for samples created from the basic template
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "sample",
            null,
            new GlobalIdentifier("IT" + complexSample.getTemplateId()),
            null,
            null,
            null,
            user);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals("junit test complex sample", result.getRecords().get(0).getName());

    // search for samples created from the custom template
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "sample",
            null,
            new GlobalIdentifier("IT" + sampleFromCustomTemplate.getTemplateId()),
            null,
            null,
            null,
            user);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals("sample from junit test template", result.getRecords().get(0).getName());
  }

  @Test
  public void inventorySearchDefaultPermissions() throws Exception {
    User u1 = createInitAndLoginAnyUser();
    createBasicSampleForUser(u1, "u1's inventorySearchTestSample");

    User u2 = createInitAndLoginAnyUser();
    createBasicSampleForUser(u2, "u2's inventorySearchTestSample");

    User pi = createAndSaveUserIfNotExists(getRandomName(10), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(pi);
    createBasicSampleForUser(pi, "pi's inventorySearchTestSample");

    // check visibility within a group
    Group group = createGroup("group", pi);
    addUsersToGroup(pi, group, u1);

    flushToSearchIndices();

    PaginationCriteria<InventoryRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(InventoryRecord.class);
    pgCrit.setOrderBy("name");
    pgCrit.setSortOrder(SortOrder.ASC);

    // user u2 can see only their sample, not u1's sample
    ApiInventorySearchResult result =
        searchMgr.searchInventoryWithSimpleQuery("inve*", null, null, null, null, pgCrit, u2);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals("u2's inventorySearchTestSample", result.getRecords().get(0).getName());

    // u1 can see both theirs, and pi's sample
    result = searchMgr.searchInventoryWithSimpleQuery("inve*", null, null, null, null, pgCrit, u1);
    assertEquals(2, result.getTotalHits().intValue());
    assertEquals("pi's inventorySearchTestSample", result.getRecords().get(0).getName());
    assertEquals("u1's inventorySearchTestSample", result.getRecords().get(1).getName());

    // user can limit the search to samples belonging to a particular owner
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "inve*", null, null, pi.getUsername(), null, pgCrit, u1);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals("pi's inventorySearchTestSample", result.getRecords().get(0).getName());

    // sysadmin can see samples belonging to all users
    User sysAdminUser = getSysAdminUser();
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "inventorySearchTestSample", null, null, null, null, pgCrit, sysAdminUser);
    assertEquals(3, result.getTotalHits().intValue());
    assertEquals("pi's inventorySearchTestSample", result.getRecords().get(0).getName());
    assertEquals("u1's inventorySearchTestSample", result.getRecords().get(1).getName());
    assertEquals("u2's inventorySearchTestSample", result.getRecords().get(2).getName());

    // partial-wildcard search rejected for sysadmin
    assertExceptionThrown(
        () ->
            searchMgr.searchInventoryWithSimpleQuery(
                "inve*", null, null, null, null, null, sysAdminUser),
        IllegalArgumentException.class);
  }

  @Test
  public void inventorySearchWhitelistPermissions() {
    User u1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u2"));
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initialiseContentWithEmptyContent(u1, u2, pi);

    // check visibility within a group
    Group group1 = createGroup("group1", pi);
    addUsersToGroup(pi, group1, u1);
    Group group2 = createGroup("group2", pi);
    addUsersToGroup(pi, group2, u2);

    // lets create samples with default access for user1 and user2
    createBasicSampleForUser(u1, "u1's inventorySearchTestSample");
    ApiSampleWithFullSubSamples u2Sample =
        createBasicSampleForUser(u2, "u2's inventorySearchTestSample");
    // for pi, let's create a sample visible only to group2
    createBasicSampleForUser(pi, "pi's inventorySearchTestSample", "pi ss", List.of(group2));

    flushToSearchIndices();

    PaginationCriteria<InventoryRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(InventoryRecord.class);
    pgCrit.setOrderBy("name");
    pgCrit.setSortOrder(SortOrder.ASC);

    // user u1 can see only their sample, not u2's nor pi's sample
    ApiInventorySearchResult result =
        searchMgr.searchInventoryWithSimpleQuery("inve*", null, null, null, null, pgCrit, u1);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals("u1's inventorySearchTestSample", result.getRecords().get(0).getName());

    // u2 can see both theirs, and pi's sample
    result = searchMgr.searchInventoryWithSimpleQuery("inve*", null, null, null, null, pgCrit, u2);
    assertEquals(2, result.getTotalHits().intValue());
    assertEquals("pi's inventorySearchTestSample", result.getRecords().get(0).getName());
    assertEquals("u2's inventorySearchTestSample", result.getRecords().get(1).getName());

    // let u2 change sample's permission to group1 and group2
    ApiSample sampleUpdate = new ApiSample();
    sampleUpdate.setId(u2Sample.getId());
    sampleUpdate.setSharingMode(ApiInventoryRecordInfo.ApiInventorySharingMode.WHITELIST);
    sampleUpdate.setSharedWith(
        List.of(
            ApiInventoryRecordInfo.ApiGroupInfoWithSharedFlag.forSharingWithGroup(group1, u2),
            ApiInventoryRecordInfo.ApiGroupInfoWithSharedFlag.forSharingWithGroup(group2, u2)));
    sampleApiMgr.updateApiSample(sampleUpdate, u2);

    flushToSearchIndices();

    // now u1 can see both their sample, and u2's
    result = searchMgr.searchInventoryWithSimpleQuery("inve*", null, null, null, null, pgCrit, u1);
    assertEquals(2, result.getTotalHits().intValue());
    assertEquals("u1's inventorySearchTestSample", result.getRecords().get(0).getName());
    assertEquals("u2's inventorySearchTestSample", result.getRecords().get(1).getName());

    // pi can see samples belonging to both users, irrelevant of whitelist
    User sysAdminUser = getSysAdminUser();
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "inventorySearchTestSample", null, null, null, null, pgCrit, sysAdminUser);
    assertEquals(3, result.getTotalHits().intValue());
    assertEquals("pi's inventorySearchTestSample", result.getRecords().get(0).getName());
    assertEquals("u1's inventorySearchTestSample", result.getRecords().get(1).getName());
    assertEquals("u2's inventorySearchTestSample", result.getRecords().get(2).getName());
  }

  @Test
  public void inventorySearchById() {
    User u = createInitAndLoginAnyUser();
    ApiContainer workbench = getWorkbenchForUser(u);

    // create a sample
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(u);
    ApiSubSample subSample = sample.getSubSamples().get(0);
    // create deleted container
    ApiContainer deletedContainer = createBasicContainerForUser(u);
    containerApiMgr.markContainerAsDeleted(deletedContainer.getId(), u);

    flushToSearchIndices();

    String sampleGlobalId = sample.getGlobalId();
    String subSampleGlobalId = subSample.getGlobalId();
    String deletedContainerGlobalId = deletedContainer.getGlobalId();

    // search for sample's global id
    ApiInventorySearchResult result =
        searchMgr.searchInventoryWithSimpleQuery(sampleGlobalId, null, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals(sampleGlobalId, result.getRecords().get(0).getGlobalId());

    /* search for numeric string matching sample id - user probably expects exact string match, not a record with equal id */
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            sample.getId() + "", null, null, null, null, null, u);
    assertEquals(0, result.getTotalHits().intValue());

    // limit search to samples
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            sampleGlobalId, InventorySearchType.SAMPLE, null, null, null, null, u);
    assertEquals(1, result.getTotalHits().intValue());

    // limit search to samples but with workbench filter active (PRT-483)
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            sampleGlobalId,
            InventorySearchType.SAMPLE,
            new GlobalIdentifier(workbench.getGlobalId()),
            null,
            null,
            null,
            u);
    assertEquals(0, result.getTotalHits().intValue());

    // limit search to deleted samples
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            sampleGlobalId,
            InventorySearchType.SAMPLE,
            null,
            null,
            InventorySearchDeletedOption.DELETED_ONLY,
            null,
            u);
    assertEquals(0, result.getTotalHits().intValue());

    // limit search to containers
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            sampleGlobalId, InventorySearchType.CONTAINER, null, null, null, null, u);
    assertEquals(0, result.getTotalHits().intValue());

    // limit search to containers
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            deletedContainerGlobalId, InventorySearchType.CONTAINER, null, null, null, null, u);
    assertEquals(0, result.getTotalHits().intValue());

    // limit search to containers but include deleted
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            deletedContainerGlobalId,
            InventorySearchType.CONTAINER,
            null,
            null,
            InventorySearchDeletedOption.INCLUDE,
            null,
            u);
    assertEquals(1, result.getTotalHits().intValue());

    // limit search to containers, including deleted, but within workbench
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            deletedContainerGlobalId,
            InventorySearchType.CONTAINER,
            new GlobalIdentifier(workbench.getGlobalId()),
            null,
            InventorySearchDeletedOption.INCLUDE,
            null,
            u);
    assertEquals(0, result.getTotalHits().intValue());

    // search by subsample's global id, limited to workbench
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            subSampleGlobalId,
            InventorySearchType.ALL,
            new GlobalIdentifier(workbench.getGlobalId()),
            null,
            null,
            null,
            u);
    assertEquals(1, result.getTotalHits().intValue());

    // search by subsample's global id, limited to parent sample id
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            subSampleGlobalId,
            InventorySearchType.ALL,
            new GlobalIdentifier(sampleGlobalId),
            null,
            null,
            null,
            u);
    assertEquals(1, result.getTotalHits().intValue());

    // search by subsample's global id, limited to deleted container id
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            subSampleGlobalId,
            InventorySearchType.ALL,
            new GlobalIdentifier(deletedContainer.getGlobalId()),
            null,
            null,
            null,
            u);
    assertEquals(0, result.getTotalHits().intValue());
  }

  @Test
  public void inventorySearchForLimitedAndPublicView() {

    // create two users in a group
    User u1 = createAndSaveUserIfNotExists(getRandomAlphabeticString("u1"));
    User pi = createAndSaveUserIfNotExists(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User unrelatedUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("default"));
    initialiseContentWithEmptyContent(u1, pi, unrelatedUser);

    // check visibility within a group
    Group group1 = createGroup("group1", pi);
    addUsersToGroup(pi, group1, u1);

    // pi creates group container, and a private container with subcontainer subsample in it
    ApiContainer piGroupContainer =
        createBasicContainerForUser(pi, "pi's inventorySearchTestContainer");
    ApiContainer piPrivateContainer =
        createBasicContainerForUser(pi, "pi's inventorySearchTestContainer", List.of());
    ApiContainer piPrivateSubContainer =
        createBasicContainerForUser(pi, "pi's inventorySearchTestContainer", List.of());
    moveContainerIntoListContainer(piPrivateSubContainer.getId(), piPrivateContainer.getId(), pi);
    ApiSampleWithFullSubSamples piPrivateSample =
        createBasicSampleForUser(pi, "pi's inventorySearchTestSample", "pi's ss", List.of());
    ApiSubSample piPrivateSubSample = piPrivateSample.getSubSamples().get(0);
    moveSubSampleIntoListContainer(piPrivateSubSample.getId(), piPrivateContainer.getId(), pi);

    // add barcode to the subsample and subcontainer
    ApiSubSample subSampleUpdate = new ApiSubSample();
    subSampleUpdate.setId(piPrivateSubSample.getId());
    final String TEST_BARCODE = "ABC_BARCODE";
    subSampleUpdate.getBarcodes().add(new ApiBarcode(TEST_BARCODE));
    subSampleUpdate.getBarcodes().get(0).setNewBarcodeRequest(true);
    subSampleApiMgr.updateApiSubSample(subSampleUpdate, pi);
    ApiContainer containerUpdate = new ApiContainer();
    containerUpdate.setId(piPrivateSubContainer.getId());
    containerUpdate.getBarcodes().add(new ApiBarcode(TEST_BARCODE));
    containerUpdate.getBarcodes().get(0).setNewBarcodeRequest(true);
    containerApiMgr.updateApiContainer(containerUpdate, pi);

    new GlobalIdentifier(piGroupContainer.getGlobalId());
    GlobalIdentifier piPrivateContainerOid = new GlobalIdentifier(piPrivateContainer.getGlobalId());
    GlobalIdentifier piPrivateSampleOid = new GlobalIdentifier(piPrivateSample.getGlobalId());
    GlobalIdentifier piPrivateSubSampleOid = new GlobalIdentifier(piPrivateSubSample.getGlobalId());

    // sanity check - u1 has only limited view to private container, and no access to
    // subsample/sample
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(piPrivateContainerOid, u1));
    assertTrue(invPermissionUtils.canUserLimitedReadInventoryRecord(piPrivateContainerOid, u1));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(piPrivateSampleOid, u1));
    assertFalse(invPermissionUtils.canUserLimitedReadInventoryRecord(piPrivateSampleOid, u1));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(piPrivateSubSampleOid, u1));
    assertFalse(invPermissionUtils.canUserLimitedReadInventoryRecord(piPrivateSubSampleOid, u1));

    flushToSearchIndices();

    PaginationCriteria<InventoryRecord> pgCrit =
        PaginationCriteria.createDefaultForClass(InventoryRecord.class);
    pgCrit.setOrderBy("name");
    pgCrit.setSortOrder(SortOrder.ASC);

    // user u1 can only find a group-shared container with text search
    ApiInventorySearchResult result =
        searchMgr.searchInventoryWithSimpleQuery(
            "inve*", InventorySearchType.ALL, null, null, null, pgCrit, u1);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals("pi's inventorySearchTestContainer", result.getRecords().get(0).getName());

    // user u1 may find public details of private sample searched by id
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            piPrivateSample.getGlobalId(), InventorySearchType.ALL, null, null, null, null, u1);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals("pi's inventorySearchTestSample", result.getRecords().get(0).getName());
    assertTrue(result.getRecords().get(0).isClearedForPublicView());

    // may also find details of subsample and container when searching by barcode
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            TEST_BARCODE, InventorySearchType.ALL, null, null, null, null, u1);
    assertEquals(2, result.getTotalHits().intValue());
    assertTrue(result.getRecords().get(0).isClearedForPublicView());
    assertTrue(result.getRecords().get(1).isClearedForPublicView());

    // barcode search works for unrelated user too
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            TEST_BARCODE, InventorySearchType.ALL, null, null, null, null, unrelatedUser);
    assertEquals(2, result.getTotalHits().intValue());
    assertTrue(result.getRecords().get(0).isClearedForPublicView());
    assertTrue(result.getRecords().get(1).isClearedForPublicView());

    // move private subsample from private container to group-shared one, that'll give limited-read
    // permission to u1
    moveSubSampleIntoListContainer(piPrivateSubSample.getId(), piGroupContainer.getId(), pi);

    // sanity check - u1 has limited-read access to subsample/sample
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(piPrivateSampleOid, u1));
    assertTrue(invPermissionUtils.canUserLimitedReadInventoryRecord(piPrivateSampleOid, u1));
    assertFalse(invPermissionUtils.canUserReadInventoryRecord(piPrivateSubSampleOid, u1));
    assertTrue(invPermissionUtils.canUserLimitedReadInventoryRecord(piPrivateSubSampleOid, u1));

    // user u1 still only finds container with text search
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            "inve*", InventorySearchType.ALL, null, null, null, pgCrit, u1);
    assertEquals(1, result.getTotalHits().intValue());
    assertEquals("pi's inventorySearchTestContainer", result.getRecords().get(0).getName());

    // u1 can find sample/subsample with global id search though
    result =
        searchMgr.searchInventoryWithSimpleQuery(
            piPrivateSample.getGlobalId(), InventorySearchType.ALL, null, null, null, null, u1);
    assertEquals(1, result.getTotalHits().intValue());
    ApiInventoryRecordInfo piSampleInfoAsSeenByU1 = result.getRecords().get(0);
    assertEquals("pi's inventorySearchTestSample", piSampleInfoAsSeenByU1.getName());
    assertEquals(
        List.of(ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.LIMITED_READ),
        piSampleInfoAsSeenByU1.getPermittedActions());
    assertNotNull(result.getRecords().get(0).getBarcodes()); // not-null in limited view
    assertNull(piSampleInfoAsSeenByU1.getModifiedBy()); // null in limited view

    result =
        searchMgr.searchInventoryWithSimpleQuery(
            piPrivateSubSampleOid.getIdString(),
            InventorySearchType.ALL,
            null,
            null,
            null,
            null,
            u1);
    assertEquals(1, result.getTotalHits().intValue());
    ApiInventoryRecordInfo piSubSampleInfoAsSeenByU1 = result.getRecords().get(0);
    assertEquals("pi's ss", piSubSampleInfoAsSeenByU1.getName());
    assertEquals(
        List.of(ApiInventoryRecordInfo.ApiInventoryRecordPermittedAction.LIMITED_READ),
        piSubSampleInfoAsSeenByU1.getPermittedActions());
    assertNotNull(result.getRecords().get(0).getBarcodes()); // not-null in limited view
    assertNull(piSubSampleInfoAsSeenByU1.getModifiedBy()); // null in limited view
  }
}
