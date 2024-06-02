package com.researchspace.service;

import static com.researchspace.model.record.StructuredDocument.MAX_TAG_LENGTH;
import static com.researchspace.service.DocumentTagManager.FINAL_DATA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;

import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.impl.BioPortalOntologiesService;
import com.researchspace.service.impl.CustomFormAppInitialiser;
import com.researchspace.service.impl.OntologyDocManager;
import com.researchspace.service.inventory.InventoryTagApiManager;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.testutils.TestGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.validation.ConstraintViolationException;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.util.ReflectionTestUtils;

public class DocumentTagManagerTest extends SpringTransactionalTest {
  private @Autowired DocumentTagManager tagMgr;
  @Mock private BioPortalOntologiesService bioPortalOntologiesServiceMock;

  @Autowired
  @Qualifier("customFormAppInitialiser")
  private IApplicationInitialisor customFormAppInitialiser;

  @Mock private OntologyDocManager ontologyDocManagerMock;
  @Mock private InventoryTagApiManager inventoryTagsApiManagerMock;

  @Before
  public void setUp() throws Exception {
    openMocks(this);
    super.setUp();
    ReflectionTestUtils.setField(tagMgr, "ontologyDocManager", ontologyDocManagerMock);
    ReflectionTestUtils.setField(
        tagMgr, "bioPortalOntologiesService", bioPortalOntologiesServiceMock);
    ReflectionTestUtils.setField(tagMgr, "inventoryTagApiManager", inventoryTagsApiManagerMock);
    when(inventoryTagsApiManagerMock.getTagsForUser(any(User.class))).thenReturn(new ArrayList<>());
    // this is hacky but 1) CustomFormAppInitialiser onAppStartup doesnt get called in tests even
    // though the bean is present
    // and 2) calling onAppStartup() in this test on the CustomFormAppInitialiser bean fails due to
    // GlobalInitSysadminAuthenticationToken
    // being missing.
    Object realBean = AopProxyUtils.getSingletonTarget(customFormAppInitialiser);
    // NOTE - in these test that use TransactionalTestExecutionListener, transaction boundary is per
    // test and therefore if we use any Manager
    // classes for setupc(which have their own transaction boundaries) our test code will not see
    // the changes to the DB). Therefore we must use
    // hibernate/dao classes for all DB setup
    RSForm ontologiesForm =
        ((CustomFormAppInitialiser) realBean)
            .createOntologiesForm(
                CustomFormAppInitialiser.ONTOLOGY_FORM_NAME,
                CustomFormAppInitialiser.ONTOLOGY_DESCRIPTION,
                userDao.getUserByUserName("sysadmin1"));
    formDao.save(ontologiesForm);
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void testUpdateOntologyDocUsesOneSecondMinInterval() {
    assertTrue(tagMgr.getMinUpdateIntervalMillis() == 1000);
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    logoutAndLoginAs(user);
    final StructuredDocument doc = createBasicDocumentInRootFolderWithText(user, "any");
    doc.setDocTag("docTag");
    doc.setTagMetaData("docTag");
    recordMgr.save(doc, user);
    tagMgr.updateUserOntologyDocument(user);
    Set tags = Set.of("docTag");
    verify(ontologyDocManagerMock, times(1)).writeTagsToUsersOntologyTagDoc(eq(user), eq(tags));
    for (int i = 0; i < 10; i++) {
      tagMgr.updateUserOntologyDocument(user);
    }
    verify(ontologyDocManagerMock, times(1)).writeTagsToUsersOntologyTagDoc(eq(user), eq(tags));
  }

  @Test
  public void saveTag() throws Exception {
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    final StructuredDocument any = createBasicDocumentInRootFolderWithText(user, "any");
    final User other = createAndSaveRandomUser();
    logoutAndLoginAs(other);
    assertAuthorisationExceptionThrown(() -> tagMgr.saveTag(any.getId(), "tag1", other));
    logoutAndLoginAs(user);
    // saving empty tag is NOT rejected if is empty - in general, attempt to re-save current tags
    // passes
    assertTrue(tagMgr.saveTag(any.getId(), "", user).isSucceeded());
    assertTrue(tagMgr.saveTag(any.getId(), "tag1", user).isSucceeded());
    assertEquals("tag1", recordDao.get(any.getId()).asStrucDoc().getDocTag());
    // removing existing tag, RSPAC-198
    assertTrue(tagMgr.saveTag(any.getId(), "", user).isSucceeded());
    assertTrue(
        tagMgr
            .saveTag(any.getId(), RandomStringUtils.randomAlphabetic(MAX_TAG_LENGTH), user)
            .isSucceeded());
    assertThrows(
        ConstraintViolationException.class,
        () ->
            tagMgr.saveTag(
                any.getId(), RandomStringUtils.randomAlphabetic(MAX_TAG_LENGTH + 1), user));
  }

  @Test
  public void apiSaveTagOntologiesNotEnforced() throws Exception {
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    final StructuredDocument any = createBasicDocumentInRootFolderWithText(user, "any");
    any.setTagMetaData(
        "tag1__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_NAME_DELIM__MYONTOLOGY__RSP_EXTONT_VERSION_DELIM__1");
    any.setDocTag("tag1");
    recordDao.save(any);
    logoutAndLoginAs(user);
    assertTrue(tagMgr.apiSaveTagForDocument(any.getId(), "tag1", user).isSucceeded());
    assertEquals("tag1", recordDao.get(any.getId()).asStrucDoc().getDocTag());
    assertEquals(
        "tag1__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_NAME_DELIM__MYONTOLOGY__RSP_EXTONT_VERSION_DELIM__1",
        recordDao.get(any.getId()).asStrucDoc().getTagMetaData());
    assertTrue(tagMgr.apiSaveTagForDocument(any.getId(), "tag2", user).isSucceeded());
    assertEquals("tag2", recordDao.get(any.getId()).asStrucDoc().getDocTag());
    assertEquals("tag2", recordDao.get(any.getId()).asStrucDoc().getTagMetaData());
    assertTrue(tagMgr.apiSaveTagForDocument(any.getId(), "", user).isSucceeded());
    assertEquals("", recordDao.get(any.getId()).asStrucDoc().getDocTag());
    assertEquals("", recordDao.get(any.getId()).asStrucDoc().getTagMetaData());
    assertThrows(
        IllegalArgumentException.class,
        () -> tagMgr.apiSaveTagForDocument(any.getId(), "tag/", user));
    assertThrows(
        IllegalArgumentException.class,
        () -> tagMgr.apiSaveTagForDocument(any.getId(), "tag\\", user));
    assertThrows(
        IllegalArgumentException.class,
        () -> tagMgr.apiSaveTagForDocument(any.getId(), "tag>", user));
    assertThrows(
        IllegalArgumentException.class,
        () -> tagMgr.apiSaveTagForDocument(any.getId(), "tag<", user));
  }

  @Test
  public void apiSaveTagOntologiesEnforced() {
    User user = setupUserWithOntologyDocAndDocWithTagsEnforceOntologiesAndOntologyNotShared();
    initialiseContentWithEmptyContent(user);
    final StructuredDocument any = createBasicDocumentInRootFolderWithText(user, "any");
    any.setTagMetaData(
        "tag1__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_NAME_DELIM__MYONTOLOGY__RSP_EXTONT_VERSION_DELIM__1");
    any.setDocTag("tag1");
    recordDao.save(any);
    logoutAndLoginAs(user);
    // new tags forbidden
    assertThrows(
        IllegalArgumentException.class,
        () -> tagMgr.apiSaveTagForDocument(any.getId(), "tag2", user));
    assertTrue(tagMgr.apiSaveTagForDocument(any.getId(), "tag1", user).isSucceeded());
    assertTrue(tagMgr.apiSaveTagForDocument(any.getId(), "", user).isSucceeded());
    // tag deletion allowed
    assertEquals("", recordDao.get(any.getId()).asStrucDoc().getDocTag());
    assertEquals("", recordDao.get(any.getId()).asStrucDoc().getTagMetaData());
  }

  @Test
  public void saveTagShouldUpdateUserontologyDocument() {
    User user = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(user);
    StructuredDocument one = createBasicDocumentInRootFolderWithText(user, "one");
    StructuredDocument two = createBasicDocumentInRootFolderWithText(user, "two");
    logoutAndLoginAs(user);
    tagMgr.saveTag(one.getId(), "tag1", user);
    Set<String> tagSet = Set.of("tag1");
    verify(ontologyDocManagerMock).writeTagsToUsersOntologyTagDoc(eq(user), eq(tagSet));
    tagMgr.saveTag(two.getId(), "tag2", user);
    tagSet = Set.of("tag1", "tag2");
    verify(ontologyDocManagerMock).writeTagsToUsersOntologyTagDoc(eq(user), eq(tagSet));
  }

  @Test
  public void testGetTagsForViewableDocumentsForSingleUser() {
    User any = createAndSaveRandomUser();
    when(inventoryTagsApiManagerMock.getTagsForUser(eq(any))).thenReturn(List.of("INVENTORY_TAG"));
    initialiseContentWithEmptyContent(any);
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(any, "text");
    anyDoc.setDocTag("tag1,another, thirdTag");
    anyDoc.setTagMetaData("tag1,another, thirdTag");
    recordDao.save(anyDoc);
    StructuredDocument anyDoc2 = createBasicDocumentInRootFolderWithText(any, "text2");
    anyDoc2.setDocTag("tag12,another2, thirdTag2");
    anyDoc2.setTagMetaData("tag12,another2, thirdTag2");
    recordDao.save(anyDoc2);
    // tags
    assertNTags(7, any, "");
    // case insensitive, match anywhere
    assertNTags(5, any, "tag");
    assertNTags(1, any, "INVENTORY_TAG");
    // no hits
    assertNTags(0, any, "nomatch");
    assertEquals(
        1,
        documentTagManager
            .getTagsPlusMetaForViewableInventoryDocuments(any, "INVENTORY_TAG")
            .size());
    assertEquals(
        1, documentTagManager.getTagsPlusMetaForViewableInventoryDocuments(any, "tag").size());
    assertEquals(
        0, documentTagManager.getTagsPlusMetaForViewableELNDocuments(any, "INVENTORY_TAG").size());
    assertEquals(4, documentTagManager.getTagsPlusMetaForViewableELNDocuments(any, "tag").size());
  }

  @Test
  public void
      testGetTagsPlusOntologiesForViewableDocumentsForSingleUserWhenOntologiesNotEnforced() {
    User any = setupUserWithOntologyDocAndDocWithTagsOntologyNotEnforcedAndOntologyNotShared();
    TreeSet<String> ontologies =
        (TreeSet<String>) tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "", 0);
    assertEquals(7, ontologies.size());
    assertTrue(ontologies.last().equals("thirdTag"));
    assertTrue(ontologies.first().equals(DocumentTagManager.SMALL_DATASET_IN_SINGLE_BLOCK));
    assertTrue(ontologies.contains("key=value"));
    assertTrue(ontologies.contains("key=value2"));
    assertTrue(ontologies.contains("key2=value2"));
    assertTrue(ontologies.contains("tag1"));
    assertTrue(ontologies.contains("another"));
  }

  @Test
  public void
      testGetTagsPlusOntologiesForViewableDocumentsForwardSlashReplacedButCommaNotReplaced() {
    TestGroup grp1 = createTestGroup(1, new TestGroupConfig(true));
    User u1 = grp1.getUserByPrefix("u1");
    logoutAndLoginAs(u1);
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(u1, "text");
    anyDoc.setDocTag("tag__rspactags_forsl__1,another__rspactags_comma__abigone, thirdTag");
    anyDoc.setTagMetaData("tag__rspactags_forsl__1,another__rspactags_comma__abigone, thirdTag");
    recordDao.save(anyDoc);
    TreeSet<String> ontologies =
        (TreeSet<String>) tagMgr.getTagsPlusOntologiesForViewableDocuments(u1, "", 0);
    assertEquals(4, ontologies.size());
    assertTrue(ontologies.last().equals("thirdTag"));
    assertTrue(ontologies.first().equals(DocumentTagManager.SMALL_DATASET_IN_SINGLE_BLOCK));
    assertTrue(ontologies.contains("tag/1"));
    assertTrue(ontologies.contains("another__rspactags_comma__abigone"));
  }

  @Test
  public void testGetTagsPlusOntologiesWithBioPortalDataWhenAllowedAndNotAllowed() {
    String bioOntology1 =
        "termValue__RSP_EXTONT_URL_DELIM__termurl__RSP_EXTONT_NAME_DELIM__OntologyName__RSP_EXTONT_VERSION_DELIM__1";
    String bioOntology2 =
        "termValue2__RSP_EXTONT_URL_DELIM__termurl2__RSP_EXTONT_NAME_DELIM__OntologyName2__RSP_EXTONT_VERSION_DELIM__2";
    when(bioPortalOntologiesServiceMock.getBioOntologyDataForQuery(any(String.class)))
        .thenReturn(List.of(bioOntology1, bioOntology2));
    TestGroup grp1 = createTestGroup(1, new TestGroupConfig(true));
    User u1 = grp1.getUserByPrefix("u1");
    logoutAndLoginAs(u1);
    TreeSet<String> ontologies = tagMgr.getTagsPlusOntologiesForViewableDocuments(u1, "", 0);
    assertEquals(1, ontologies.size());
    assertEquals("=========SMALL_DATASET_IN_SINGLE_BLOCK=========", ontologies.iterator().next());
    grp1.getGroup().setAllowBioOntologies(true);
    grpdao.save(grp1.getGroup());
    ontologies = tagMgr.getTagsPlusOntologiesForViewableDocuments(u1, "", 0);
    assertEquals(3, ontologies.size());
    assertTrue(ontologies.contains(bioOntology1));
    assertTrue(ontologies.contains(bioOntology2));
  }

  @Test
  public void
      testGetTagsPlusOntologiesForViewableDocumentsWhenOntologiesAreEnforcedAndOntologyDocNotSharedWithGroup() {
    User any = setupUserWithOntologyDocAndDocWithTagsEnforceOntologiesAndOntologyNotShared();
    TreeSet<String> ontologies =
        (TreeSet<String>) tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "", 0);
    assertEquals(1, ontologies.size());
    assertTrue(ontologies.first().equals(DocumentTagManager.SMALL_DATASET_IN_SINGLE_BLOCK));
  }

  @Test
  public void
      testGetTagsPlusOntologiesForViewableDocumentsWhenOntologiesAreEnforcedAndOntologyDocIsSharedWithGroup() {
    User any = setupUserWithOntologyDocAndDocWithTagsEnforceOntologiesAndOntologyIsShared();
    TreeSet<String> ontologies =
        (TreeSet<String>) tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "", 0);
    assertEquals(4, ontologies.size());
    assertTrue(ontologies.first().equals(DocumentTagManager.SMALL_DATASET_IN_SINGLE_BLOCK));
    assertTrue(ontologies.contains("key=value"));
    assertTrue(ontologies.contains("key=value2"));
    assertTrue(ontologies.contains("key2=value2"));
  }

  @Test
  public void
      testGetTagsPlusOntologiesForViewableDocumentsWhenOntologiesAreEnforcedAndOntologyDocIsSharedWithGroupByAnotherUser() {
    User any =
        setupUserWithOntologyDocAndDocWithTagsEnforceOntologiesAndOntologyIsSharedByAnotherUser();
    TreeSet<String> ontologies =
        (TreeSet<String>) tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "", 0);
    assertEquals(4, ontologies.size());
    assertTrue(ontologies.first().equals(DocumentTagManager.SMALL_DATASET_IN_SINGLE_BLOCK));
    assertTrue(ontologies.contains("key=value"));
    assertTrue(ontologies.contains("key=value2"));
    assertTrue(ontologies.contains("key2=value2"));
  }

  private User setupUserWithOntologyDocAndDocWithTagsOntologyNotEnforcedAndOntologyNotShared() {
    return setupUserWithOntologyDocAndDocWithTags(false, false, true);
  }

  private User setupUserTagsInDocHavingForwardSlash() {
    return setupUserWithOntologyDocAndDocWithTags(false, false, true);
  }

  private User setupUserWithOntologyDocAndDocWithTagsEnforceOntologiesAndOntologyNotShared() {
    return setupUserWithOntologyDocAndDocWithTags(true, false, true);
  }

  private User setupUserWithOntologyDocAndDocWithTagsEnforceOntologiesAndOntologyIsShared() {
    return setupUserWithOntologyDocAndDocWithTags(true, true, true);
  }

  private User
      setupUserWithOntologyDocAndDocWithTagsEnforceOntologiesAndOntologyIsSharedByAnotherUser() {
    return setupUserWithOntologyDocAndDocWithTags(true, true, false);
  }

  private User setupUserWithOntologyDocAndDocWithTags(
      boolean enforceOntologies, boolean shareWithGroup, boolean sharedBySameUser) {
    TestGroup grp1 = createTestGroup(2, new TestGroupConfig(true));
    User any = grp1.getUserByPrefix("u1");
    User other = grp1.getUserByPrefix("u2");
    logoutAndLoginAs(any);
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(any, "text");
    anyDoc.setDocTag("tag1,another, thirdTag");
    anyDoc.setTagMetaData("tag1,another, thirdTag");
    recordDao.save(anyDoc);
    StructuredDocument ontologyDoc = null;
    if (sharedBySameUser) {
      ontologyDoc =
          createOntologyDocumentInFolder(
              any,
              folderDao.getRootRecordForUser(any),
              "<p>key=value,value2</p><p>key2=value2</p>");
    } else {
      logoutAndLoginAs(other);
      ontologyDoc =
          createOntologyDocumentInFolder(
              other,
              folderDao.getRootRecordForUser(other),
              "<p>key=value,value2</p><p>key2=value2</p>");
    }
    ontologyDoc = (StructuredDocument) recordDao.save(ontologyDoc);
    if (enforceOntologies) {
      Group enforcer = grp1.getGroup();
      enforcer.setEnforceOntologies(true);
      enforcer = grpdao.save(enforcer);
      userDao.save(sharedBySameUser ? any : other);
      if (shareWithGroup) {
        shareRecordWithGroup(sharedBySameUser ? any : other, enforcer, ontologyDoc);
      }
    }
    return any;
  }

  @Test
  public void testGetTagsPlusOntologiesWithLargeontologyDocument() {
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    assertNTags(0, any, "");
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < 3065; i++) {
      String ontologyVal = i + "_anOntology";
      sb.append(ontologyVal + ",");
    }
    StructuredDocument ontologyDoc =
        createOntologyDocumentInFolder(
            any, folderDao.getRootRecordForUser(any), "<p>" + sb.toString() + "</p>");
    recordDao.save(ontologyDoc);
    TreeSet<String> ontologies = tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "", 0);
    assertEquals(1000, ontologies.size());
    assertTrue(ontologies.contains("1000_anOntology"));
    ontologies = tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "", 2);
    assertEquals(1000, ontologies.size());
    assertTrue(ontologies.contains("888_anOntology"));
    ontologies = tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "", 3);
    assertEquals(66, ontologies.size());
    assertEquals(ontologies.last(), FINAL_DATA);
    assertTrue(ontologies.contains("999_anOntology"));
  }

  // Test scenario - all of the following happened:
  // 1) Manual ontology file (so starts with <p>
  // 2) User has > 1 '=' in a line of text. We should split on the first '=' and treat subsequent as
  // just regular text
  @Test
  public void testGetTagsPlusOntologiesWhenSecondEqualsOnASingleLine() {
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    assertNTags(0, any, "");
    StringBuffer sb = new StringBuffer();
    sb.append("<p>key1=term1,term2,d=term4,term5</p>");
    StructuredDocument ontologyDoc =
        createOntologyDocumentInFolder(any, folderDao.getRootRecordForUser(any), sb.toString());
    recordDao.save(ontologyDoc);
    TreeSet<String> ontologies = tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "", 0);
    assertEquals(5, ontologies.size());
    assertEquals(
        "[=========SMALL_DATASET_IN_SINGLE_BLOCK=========, key1=d=term4, key1=term1, key1=term2,"
            + " key1=term5]",
        ontologies.toString());
  }

  // Test scenario - all of the following happened:
  // 1) uploaded an ontology csv file.
  // 2) Edited the file after upload (RSpace will insert <p> after edit)
  // 3) The file contained an '='
  @Test
  public void testGetTagsPlusOntologiesWhenKeyContainsComma() {
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    assertNTags(0, any, "");
    StringBuffer sb = new StringBuffer();
    sb.append("<p>key,key2=term1,term2,term3</p>");
    StructuredDocument ontologyDoc =
        createOntologyDocumentInFolder(any, folderDao.getRootRecordForUser(any), sb.toString());
    recordDao.save(ontologyDoc);
    TreeSet<String> ontologies = tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "", 0);
    assertEquals(5, ontologies.size());
    assertEquals(
        "[<p>key, =========SMALL_DATASET_IN_SINGLE_BLOCK=========, key2=term1, term2, term3</p>]",
        ontologies.toString());
  }

  // Test scenario -
  // 1) uploaded an ontology csv file
  // 2) The file contains an '='
  @Test
  public void testGetTagsPlusOntologiesWhenFileDoesNotStartWithHtmlP() {
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    assertNTags(0, any, "");
    StringBuffer sb = new StringBuffer();
    sb.append("key=term1,term2,term3");
    StructuredDocument ontologyDoc =
        createOntologyDocumentInFolder(any, folderDao.getRootRecordForUser(any), sb.toString());
    recordDao.save(ontologyDoc);
    TreeSet<String> ontologies = tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "", 0);
    assertEquals(4, ontologies.size());
    assertEquals(
        "[=========SMALL_DATASET_IN_SINGLE_BLOCK=========, key=term1, term2, term3]",
        ontologies.toString());
  }

  // Test scenario -
  // 1) uploaded an ontology csv file after the change which saves metadata
  @Test
  public void testGetTagsPlusOntologiesForDelimitedMetaDataOntologyData() {
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    assertNTags(0, any, "");
    StringBuffer sb = new StringBuffer();
    sb.append(
        "__RSP_EXTONT_NAME__BTO2__RSP_EXTONT_TAG_DELIM____RSP_EXTONT_VERSION__2__RSP_EXTONT_TAG_DELIM__culture"
            + " condition:camphor-grown"
            + " cell__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0006230__RSP_EXTONT_TAG_DELIM__BT-20"
            + " cell__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0001466__RSP_EXTONT_TAG_DELIM__insect"
            + " protocorm__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0006101");
    StructuredDocument ontologyDoc =
        createOntologyDocumentInFolder(any, folderDao.getRootRecordForUser(any), sb.toString());
    recordDao.save(ontologyDoc);
    TreeSet<String> ontologies = tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "", 0);
    assertEquals(4, ontologies.size());
    assertEquals(
        "[=========SMALL_DATASET_IN_SINGLE_BLOCK=========, BT-20"
            + " cell__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0001466__RSP_EXTONT_NAME_DELIM__BTO2__RSP_EXTONT_VERSION_DELIM__2,"
            + " culture condition:camphor-grown cell__RSP_EXTONT_URL_DELIM"
            + "__http://purl.obolibrary.org/obo/BTO_0006230__RSP_EXTONT_NAME_DELIM__BTO2__RSP_EXTONT_VERSION_DELIM__2,"
            + " insect protocorm__RSP_EXTONT_URL_DELIM"
            + "__http://purl.obolibrary.org/obo/BTO_0006101__RSP_EXTONT_NAME_DELIM__BTO2__RSP_EXTONT_VERSION_DELIM__2]",
        ontologies.toString());
  }

  @Test
  public void testShouldFilterGetTagsPlusOntologies() {
    User any = createAndSaveRandomUser();
    initialiseContentWithEmptyContent(any);
    assertNTags(0, any, "");
    StructuredDocument ontologyDoc =
        createOntologyDocumentInFolder(
            any,
            folderDao.getRootRecordForUser(any),
            "<p>key=value,value2,value3</p><p>key2=value,value2,value3</p>");
    recordDao.save(ontologyDoc);
    Set<String> ontologies = tagMgr.getTagsPlusOntologiesForViewableDocuments(any, "value3", 0);
    assertEquals(3, ontologies.size());
    assertTrue(ontologies.contains("key=value3"));
    assertTrue(ontologies.contains("key2=value3"));
  }

  @Test
  public void testGetTagsForViewableRecordsInGroup() throws InterruptedException {
    TestGroup grp1 = createTestGroup(2, new TestGroupConfig(true));
    User u1 = grp1.getUserByPrefix("u1");
    User u2 = grp1.getUserByPrefix("u2");
    User admin = grp1.getUserByPrefix("labAdmin");
    User pi = grp1.getPi();
    logoutAndLoginAs(u1);
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(u1, "text");
    anyDoc.setDocTag("tag1,another, thirdTag");
    anyDoc.setTagMetaData("tag1,another, thirdTag");
    recordDao.save(anyDoc);
    Notebook anyNotebook =
        createNotebookWithNEntries(u1.getRootFolder().getId(), "testNotebook", 3, u1);
    anyNotebook.setDocTag("tag12,another2, thirdTag2");
    anyNotebook.setTagMetaData("tag12,another2, thirdTag2");
    folderDao.save(anyNotebook);
    // before sharing, u2 sees no tags
    logoutAndLoginAs(u2);
    assertNTags(0, u2, "");
    // lab admin can't see all tags without view all permission
    logoutAndLoginAs(admin);
    assertNTags(0, admin, "");
    // but pi can see all tags
    logoutAndLoginAs(pi);
    assertNTags(6, pi, "");
    grpMgr.authorizeLabAdminToViewAll(admin.getId(), pi, grp1.getGroup().getId(), true);
    // lab admin can now see all tags
    logoutAndLoginAs(admin);
    assertNTags(6, admin, "");

    // u1 shares with group
    logoutAndLoginAs(u1);
    shareRecordWithGroup(u1, grp1.getGroup(), anyDoc);
    // and now u2 can see the tags
    logoutAndLoginAs(u2);
    assertNTags(3, u2, "");

    // u1 now shares 2nd doc just with u2
    logoutAndLoginAs(u1);
    shareNotebookWithGroupMember(u1, anyNotebook, u2);
    logoutAndLoginAs(u2);
    assertNTags(2, u2, "thirdTag");
  }

  @Test
  public void testGetTagsWithMetaDataForViewableDocuments() {
    TestGroup grp1 = createTestGroup(1, new TestGroupConfig(true));
    User u1 = grp1.getUserByPrefix("u1");
    logoutAndLoginAs(u1);
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(u1, "text");
    anyDoc.setDocTag("tag1,another, thirdTag");
    anyDoc.setTagMetaData(
        "tag1__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0006003__RSP_EXTONT_NAME_DELIM__BTO__RSP_EXTONT_VERSION_DELIM__1,another__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0006003__RSP_EXTONT_NAME_DELIM__BTO__RSP_EXTONT_VERSION_DELIM__1,"
            + " thirdTag__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0006003__RSP_EXTONT_NAME_DELIM__BTO__RSP_EXTONT_VERSION_DELIM__1");
    recordDao.save(anyDoc);
    assertNTagsPlusMeta(3, u1, "");
    assertNTagsPlusMeta(1, u1, "thirdTag");
  }

  @Test
  public void testGetTagsFromViewableDocumentsReplacesForwardSlashDelimAndCommaDelimInSearchTerm() {
    TestGroup grp1 = createTestGroup(1, new TestGroupConfig(true));
    User u1 = grp1.getUserByPrefix("u1");
    logoutAndLoginAs(u1);
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(u1, "text");
    anyDoc.setDocTag("tag__rspactags_forsl__1,another__rspactags_comma__abigone, thirdTag");
    anyDoc.setTagMetaData("tag__rspactags_forsl__1,another__rspactags_comma__abigone, thirdTag");
    recordDao.save(anyDoc);
    assertNTags(3, u1, "");
    assertNTags(1, u1, "tag__rspactags_forsl__1");
    assertNTags(1, u1, "tag/1");
    assertNTags(1, u1, "another__rspactags_comma__abigone");
    assertNTags(1, u1, "another,abigone");
  }

  @Test
  public void
      testGetTagsPlusMetaFromViewableDocumentsReplacesForwardSlashDelimAndCommaDelimInSearchTerm() {
    TestGroup grp1 = createTestGroup(1, new TestGroupConfig(true));
    User u1 = grp1.getUserByPrefix("u1");
    logoutAndLoginAs(u1);
    StructuredDocument anyDoc = createBasicDocumentInRootFolderWithText(u1, "text");
    anyDoc.setDocTag("tag__rspactags_forsl__1,another__rspactags_comma__abigone, thirdTag");
    anyDoc.setTagMetaData("tag__rspactags_forsl__1,another__rspactags_comma__abigone, thirdTag");
    recordDao.save(anyDoc);
    assertNTagsPlusMeta(3, u1, "");
    assertNTagsPlusMeta(1, u1, "tag__rspactags_forsl__1");
    assertNTagsPlusMeta(1, u1, "tag/1");
    assertNTagsPlusMeta(1, u1, "another__rspactags_comma__abigone");
    assertNTagsPlusMeta(1, u1, "another,abigone");
  }

  @Test
  public void testGetTagsForCommunityAdmin() {
    TestGroup grp1 = createTestGroup(4);
    User communityAdmin = createAndSaveAdminUser();
    TestCommunity community = createTestCommunity(TransformerUtils.toSet(grp1), communityAdmin);
    logoutAndLoginAs(grp1.getPi());
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(grp1.getPi(), "Any");
    sdoc.setDocTag("abc, def, ghi");
    sdoc.setTagMetaData("abc, def, ghi");
    recordDao.save(sdoc);
    logoutAndLoginAs(grp1.getUserByPrefix("u2"));
    StructuredDocument sdoc2 =
        createBasicDocumentInRootFolderWithText(grp1.getUserByPrefix("u2"), "Any");
    sdoc2.setDocTag("xyz,hello");
    sdoc2.setTagMetaData("xyz,hello");
    recordDao.save(sdoc2);
    logoutAndLoginAs(communityAdmin);
    assertNTags(5, communityAdmin, "");
    assertNTags(1, communityAdmin, "abc");
    // admin with no community returns empty, handled
    User adminWithNoCommunity = createAndSaveAdminUser();
    assertNTags(0, adminWithNoCommunity, "");
  }

  @Test
  public void testGetTagsForSysAdmin() {
    User sysadmin = logoutAndLoginAsSysAdmin();
    final int initialTotalTagCount = getTagCount(sysadmin, "");
    TestGroup grp1 = createTestGroup(1);
    TestGroup grp2 = createTestGroup(1);
    logoutAndLoginAs(grp1.getPi());
    StructuredDocument sdoc = createBasicDocumentInRootFolderWithText(grp1.getPi(), "Any");
    sdoc.setDocTag("abc, def, ghi");
    sdoc.setTagMetaData("abc, def, ghi");
    recordDao.save(sdoc);
    logoutAndLoginAs(grp2.getPi());
    StructuredDocument sdoc2 = createBasicDocumentInRootFolderWithText(grp2.getPi(), "Any");
    sdoc2.setDocTag("xyz,hello");
    sdoc2.setTagMetaData("xyz,hello");
    recordDao.save(sdoc2);
    final int EXPECTED_NEW_TAG_COUNT = 5;
    assertNTags(initialTotalTagCount + EXPECTED_NEW_TAG_COUNT, sysadmin, "");
    final int EXPECTED_FILTERED_TAG_COUNT = 1;
    assertNTags(EXPECTED_FILTERED_TAG_COUNT, sysadmin, "abc");
  }

  private void assertNTags(int expectedCount, User user, String tag) {
    assertEquals(expectedCount, getTagCount(user, tag));
  }

  private void assertNTagsPlusMeta(int expectedCount, User user, String tag) {
    assertEquals(expectedCount, getTagCountPlusMeta(user, tag));
  }

  private int getTagCountPlusMeta(User user, String tag) {
    return getTagsPlusMeta(user, tag).size();
  }

  private int getTagCount(User user, String tag) {
    return getTags(user, tag).size();
  }

  private Set<String> getTags(User user, String tag) {
    return tagMgr.getTagsForViewableDocuments(user, tag);
  }

  private Set<String> getTagsPlusMeta(User user, String tag) {
    return tagMgr.getTagsPlusMetaForViewableDocuments(user, tag);
  }
}
