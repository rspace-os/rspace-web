package com.researchspace.api.v1.controller;

import static com.researchspace.api.v1.controller.BaseApiController.DOCUMENTS_ENDPOINT;
import static com.researchspace.api.v1.controller.BaseApiController.FOLDERS_ENDPOINT;
import static com.researchspace.api.v1.controller.BaseApiController.FOLDER_TREE_ENDPOINT;
import static com.researchspace.core.testutil.CoreTestUtils.assertIllegalArgumentException;
import static com.researchspace.core.util.TransformerUtils.toSet;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiFolder;
import com.researchspace.api.v1.model.ApiRecordTreeItemListing;
import com.researchspace.api.v1.model.ApiRecordType;
import com.researchspace.api.v1.model.LinkableApiObject;
import com.researchspace.api.v1.model.RecordTreeItemInfo;
import com.researchspace.core.testutil.CoreTestUtils;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.core.util.progress.ProgressMonitor;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.CompositeRecordOperationResult;
import com.researchspace.model.views.RecordTypeFilter;
import com.researchspace.model.views.ServiceOperationResultCollection;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.DefaultRecordContext;
import com.researchspace.service.FolderManager;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.RecordContext;
import com.researchspace.service.RecordDeletionManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.UserFolderSetup;
import com.researchspace.service.impl.RecordDeletionManagerImpl.DeletionSettings;
import com.researchspace.session.UserSessionTracker;
import com.researchspace.testutils.FolderTestUtils;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;
import javax.ws.rs.NotFoundException;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;

public class FolderApiControllerTest {

  public @Rule MockitoRule rule = MockitoJUnit.rule();
  @Mock FolderManager folderMgr;
  @Mock RecordManager recordMgr;
  @Mock RecordDeletionManager deletionMgr;
  @Mock IPropertyHolder properties;

  @InjectMocks FolderApiController controller;
  User subject;
  Folder root = null;
  Folder existingFolder = null;
  Notebook createdNotebook, existingNotebook = null;
  Folder createdFolder;
  Folder topLevelGalleryFolder;
  StaticMessageSource msg = new StaticMessageSource();
  MockServletContext context = new MockServletContext();

  @Before
  public void setUp() throws Exception {
    this.subject = TestFactory.createAnyUser("any");
    this.root = TestFactory.createAFolder(subject.getUsername(), subject);
    this.root.setId(1L);
    subject.setRootFolder(root);
    this.existingFolder = TestFactory.createAFolder("subfolder", subject);
    this.existingFolder.setId(2L);
    this.createdFolder = TestFactory.createAFolder("createdFolder", subject);
    this.createdFolder.setId(6L);
    topLevelGalleryFolder = TestFactory.createASystemFolder("rootmedia", subject);
    topLevelGalleryFolder.addType(RecordType.ROOT_MEDIA);
    this.topLevelGalleryFolder.setId(6L);

    this.createdNotebook = TestFactory.createANotebook("created", subject);
    this.createdNotebook.setId(3L);

    this.existingNotebook = TestFactory.createANotebook("existing", subject);
    this.existingNotebook.setId(4L);
    controller.setMessageSource(new MessageSourceUtils(msg));
    controller.setServletContext(context);
    msg.addMessage("record.inaccessible", Locale.getDefault(), "inaccessible");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void createNotebookInRootFolder() throws BindException {
    root.addChild(createdNotebook, subject);
    ApiFolder toCreate = createApiNotebookToPost();
    mockGetRootFolder();
    Mockito.when(
            folderMgr.createNewNotebook(
                Mockito.eq(root.getId()),
                Mockito.eq(toCreate.getName()),
                Mockito.any(RecordContext.class),
                Mockito.eq(subject)))
        .thenReturn(createdNotebook);
    mockBaseUrl();
    ApiFolder created =
        controller.createNewFolder(
            toCreate, new BeanPropertyBindingResult(toCreate, "bean"), subject);
    assertEquals(root.getId(), created.getParentFolderId());
    assertTargetFolderNotCalled();
  }

  @Test
  public void createNotebookInSpecifiedFolder() throws BindException {
    ApiFolder toCreate = createApiNotebookToPost();
    toCreate.setParentFolderId(existingFolder.getId());
    existingFolder.addChild(createdNotebook, subject);

    Mockito.when(
            folderMgr.createNewNotebook(
                Mockito.eq(existingFolder.getId()),
                Mockito.eq(toCreate.getName()),
                Mockito.any(DefaultRecordContext.class),
                Mockito.eq(subject)))
        .thenReturn(createdNotebook);
    Mockito.when(folderMgr.getFolder(existingFolder.getId(), subject)).thenReturn(existingFolder);
    mockBaseUrl();
    ApiFolder created =
        controller.createNewFolder(
            toCreate, new BeanPropertyBindingResult(toCreate, "bean"), subject);
    assertTrue(created.isNotebook());
    assertEquals(existingFolder.getId(), created.getParentFolderId());
    assertGetRootFolderNotCalled();
  }

  @Test(expected = BindException.class)
  public void createNestedNotebookNotAllowed() throws BindException {
    ApiFolder toCreate = createApiNotebookToPost();
    toCreate.setParentFolderId(existingNotebook.getId()); // this should not be allowed
    Mockito.when(folderMgr.getFolder(existingNotebook.getId(), subject))
        .thenReturn(existingNotebook);
    controller.createNewFolder(toCreate, new BeanPropertyBindingResult(toCreate, "bean"), subject);
  }

  @Test(expected = BindException.class)
  public void createTopLevelGalleryFolder() throws BindException {
    ApiFolder toCreate = createApiFolderToPost();
    toCreate.setParentFolderId(topLevelGalleryFolder.getId()); // this should not be allowed
    Mockito.when(folderMgr.getFolder(topLevelGalleryFolder.getId(), subject))
        .thenReturn(topLevelGalleryFolder);
    controller.createNewFolder(toCreate, new BeanPropertyBindingResult(toCreate, "bean"), subject);
  }

  @Test(expected = BindException.class)
  public void createNestedFolderInNotebookNotAllowed() throws BindException {
    ApiFolder toCreate = createApiFolderToPost();
    toCreate.setParentFolderId(existingNotebook.getId()); // this should not be allowed
    Mockito.when(folderMgr.getFolder(existingNotebook.getId(), subject))
        .thenReturn(existingNotebook);
    controller.createNewFolder(toCreate, new BeanPropertyBindingResult(toCreate, "bean"), subject);
  }

  @Test(expected = BindException.class)
  public void bindExceptionThrownIfValidationFails() throws BindException {
    ApiFolder toCreate = createApiFolderToPost();
    // validation is not actually performed in this test, this is just an example
    toCreate.setName(RandomStringUtils.randomAlphabetic(300));
    BeanPropertyBindingResult errors = new BeanPropertyBindingResult(toCreate, "bean");
    errors.reject("some.value");
    controller.createNewFolder(toCreate, errors, subject);
    verify(folderMgr, never())
        .createNewFolder(Mockito.anyLong(), Mockito.anyString(), Mockito.eq(subject));
  }

  @Test
  public void createSubFolder() throws BindException {
    ApiFolder toCreate = createApiFolderToPost();
    toCreate.setParentFolderId(existingFolder.getId()); // this should not be allowed
    when(folderMgr.getFolder(existingFolder.getId(), subject)).thenReturn(existingFolder);
    existingFolder.addChild(createdFolder, subject);
    when(folderMgr.createNewFolder(existingFolder.getId(), toCreate.getName(), subject))
        .thenReturn(createdFolder);
    mockBaseUrl();
    ApiFolder created =
        controller.createNewFolder(
            toCreate, new BeanPropertyBindingResult(toCreate, "bean"), subject);
    assertFalse(created.isNotebook());

    assertEquals(existingFolder.getId(), created.getParentFolderId());
  }

  private void assertGetRootFolderNotCalled() {
    verify(folderMgr, never()).getRootFolderForUser(Mockito.any(User.class));
  }

  private ApiFolder createApiNotebookToPost() {
    ApiFolder toCreate = createApiFolderToPost();
    toCreate.setNotebook(true);
    return toCreate;
  }

  private ApiFolder createApiFolderToPost() {
    ApiFolder toCreate = new ApiFolder();
    toCreate.setName("any");
    return toCreate;
  }

  private void assertTargetFolderNotCalled() {
    Mockito.verify(folderMgr, Mockito.never()).getFolder(Mockito.anyLong(), Mockito.eq(subject));
  }

  private void mockGetRootFolder() {
    Mockito.when(folderMgr.getRootFolderForUser(subject)).thenReturn(root);
  }

  private void mockBaseUrl() {
    Mockito.when(properties.getServerUrl()).thenReturn("http://somewhere.com");
  }

  @Test(expected = NotFoundException.class)
  public void getFolderThrowsNotFoundExIfNoExists() throws BindException {
    when(folderMgr.getFolderSafe(1L, subject)).thenReturn(Optional.empty());
    controller.getFolder(1L, false, subject);
  }

  @Test
  public void getFolder() throws BindException {
    mockBaseUrl();
    when(folderMgr.getFolderSafe(1L, subject)).thenReturn(Optional.of(createdFolder));
    ApiFolder created = controller.getFolder(1L, false, subject);
    assertNotNull(created);
  }

  @Test
  public void recordFilterWorkspace() throws Exception {
    RecordTypeFilter actualFilter = controller.generateRecordFilter(Collections.emptySet(), false);
    assertEquals(EnumSet.allOf(RecordType.class), actualFilter.getWantedTypes());
    actualFilter = controller.generateRecordFilter(toSet("notebook"), false);
    assertThat(actualFilter.getWantedTypes(), hasItem(RecordType.NOTEBOOK));
    assertThat(actualFilter.getExcludedTypes(), hasItems(RecordType.NORMAL, RecordType.FOLDER));

    actualFilter = controller.generateRecordFilter(toSet("document", "folder"), false);
    assertThat(actualFilter.getWantedTypes(), hasItems(RecordType.NORMAL, RecordType.FOLDER));
    assertThat(actualFilter.getExcludedTypes(), hasItems(RecordType.NOTEBOOK));
  }

  @Test
  public void recordFilterGallery() throws Exception {
    RecordTypeFilter actualFilter = controller.generateRecordFilter(Collections.emptySet(), true);
    assertEquals(EnumSet.allOf(RecordType.class), actualFilter.getWantedTypes());
    actualFilter = controller.generateRecordFilter(toSet("folder"), true);
    assertThat(actualFilter.getWantedTypes(), hasItem(RecordType.FOLDER));
    assertThat(actualFilter.getExcludedTypes(), hasItems(RecordType.NORMAL, RecordType.MEDIA_FILE));

    actualFilter = controller.generateRecordFilter(toSet("document", "folder"), true);
    assertThat(actualFilter.getWantedTypes(), hasItems(RecordType.MEDIA_FILE, RecordType.FOLDER));
    assertThat(actualFilter.getExcludedTypes(), hasItems(RecordType.NOTEBOOK));
  }

  @Test
  public void rejectInvalidFolderTreeFilter() throws BindException {
    DocumentApiPaginationCriteria pgCriteria = new DocumentApiPaginationCriteria();
    CoreTestUtils.assertIllegalArgumentException(
        () ->
            controller.rootFolderTree(
                TransformerUtils.toSet("unknown"), pgCriteria, errorsObject(pgCriteria), subject));
  }

  @Test
  public void listRootFolderConvertsToCorrectApiTypesAndVerifySelfLinks() throws BindException {
    DocumentApiPaginationCriteria pgCriteria = new DocumentApiPaginationCriteria();

    mockBaseUrl();
    final Long docId = 2L;
    final Long folderId = 3L;
    final Long nbId = 4L;
    ISearchResults<BaseRecord> mockResults =
        createSearchResults_1OfEachWorkspaceType(docId, folderId, nbId);
    Mockito.when(
            recordMgr.listFolderRecords(
                Mockito.eq(subject.getRootFolder().getId()),
                Mockito.any(PaginationCriteria.class),
                Mockito.any(RecordTypeFilter.class)))
        .thenReturn(mockResults);
    Mockito.when(folderMgr.getRootFolderForUser(subject)).thenReturn(root);
    ApiRecordTreeItemListing listing =
        controller.rootFolderTree(null, pgCriteria, errorsObject(pgCriteria), subject);
    assertEquals(3, listing.getRecords().size());
    assertNull(listing.getParentId());
    assertSelfLink(FOLDER_TREE_ENDPOINT, listing);

    RecordTreeItemInfo docInfo = findResultById(docId, listing);
    assertEquals(ApiRecordType.DOCUMENT, docInfo.getType());
    assertSelfLink(DOCUMENTS_ENDPOINT, docInfo);

    RecordTreeItemInfo folderInfo = findResultById(folderId, listing);
    assertEquals(ApiRecordType.FOLDER, folderInfo.getType());
    assertSelfLink(FOLDERS_ENDPOINT, folderInfo);

    RecordTreeItemInfo nbInfo = findResultById(nbId, listing);
    assertEquals(ApiRecordType.NOTEBOOK, nbInfo.getType());
    assertSelfLink(FOLDERS_ENDPOINT, nbInfo);
  }

  @Test
  public void listSubfolderHasNonNullParentFolderLink() throws BindException {
    DocumentApiPaginationCriteria pgCriteria = new DocumentApiPaginationCriteria();
    mockBaseUrl();
    Folder subFolder = TestFactory.createAFolder("any", subject);
    subFolder.setId(3L);
    root.addChild(subFolder, subject);
    ISearchResults<BaseRecord> mockResults = createEmptySearchResults();
    Mockito.when(
            recordMgr.listFolderRecords(
                Mockito.eq(subFolder.getId()),
                Mockito.any(PaginationCriteria.class),
                Mockito.any(RecordTypeFilter.class)))
        .thenReturn(mockResults);
    mockFolderLoad(subFolder);
    ApiRecordTreeItemListing listing =
        controller.folderTreeById(
            subFolder.getId(), null, pgCriteria, errorsObject(pgCriteria), subject);
    assertEquals(0, listing.getRecords().size());
    assertEquals(root.getId(), listing.getParentId());
  }

  private void mockFolderLoad(Folder folder) {
    Mockito.when(folderMgr.getFolderSafe(folder.getId(), subject)).thenReturn(Optional.of(folder));
  }

  @Test
  public void listGalleryFolderConvertsToCorrectApiTypesAndVerifySelfLinks() throws BindException {
    DocumentApiPaginationCriteria pgCriteria = new DocumentApiPaginationCriteria();
    mockBaseUrl();
    // set up GallerySetup
    UserFolderSetup folderSetup = FolderTestUtils.createDefaultFolderStructure(subject);
    EcatMediaFile imgFile = TestFactory.createEcatImage(1L);
    imgFile.setOwner(subject);
    // image folder has an image
    folderSetup.getMediaImgExamples().addChild(imgFile, subject);
    ISearchResults<BaseRecord> mockResults = createMediaResults(imgFile);
    // set up mocks
    Mockito.when(
            recordMgr.listFolderRecords(
                Mockito.eq(folderSetup.getMediaImgExamples().getId()),
                Mockito.any(PaginationCriteria.class),
                Mockito.any(RecordTypeFilter.class)))
        .thenReturn(mockResults);
    mockFolderLoad(folderSetup.getMediaImgExamples());

    ApiRecordTreeItemListing listing =
        controller.folderTreeById(
            folderSetup.getMediaImgExamples().getId(),
            null,
            pgCriteria,
            errorsObject(pgCriteria),
            subject);
    assertEquals(1, listing.getRecords().size());
    assertEquals(folderSetup.getMediaImgExamples().getParent().getId(), listing.getParentId());
  }

  private ISearchResults<BaseRecord> createMediaResults(EcatMediaFile imgFile) {
    List<BaseRecord> resultsBaseRecords = TransformerUtils.toList(imgFile);
    return new SearchResultsImpl<>(resultsBaseRecords, 0, 1);
  }

  private ISearchResults<BaseRecord> createEmptySearchResults() {
    return new SearchResultsImpl<>(Collections.emptyList(), 0, 0);
  }

  private void assertSelfLink(String expectedPathMatch, LinkableApiObject nbInfo) {
    assertThat(nbInfo.getLinks().get(0).getLink(), containsString(expectedPathMatch));
  }

  private RecordTreeItemInfo findResultById(final Long docId, ApiRecordTreeItemListing listing) {
    return listing.getRecords().stream()
        .filter(info -> info.getId().equals(docId))
        .findFirst()
        .get();
  }

  // 1 folder, 1 notebook and 1 document
  private ISearchResults<BaseRecord> createSearchResults_1OfEachWorkspaceType(
      Long docId, Long folderId, Long nbId) {
    StructuredDocument document = TestFactory.createAnySD();
    document.setId(docId);
    Folder anyFolder = TestFactory.createAFolder("any", subject);
    anyFolder.setId(folderId);
    Notebook nbNotebook = TestFactory.createANotebook("nb1", subject);
    nbNotebook.setId(nbId);
    List<BaseRecord> resultsBaseRecords = TransformerUtils.toList(document, nbNotebook, anyFolder);
    return new SearchResultsImpl<>(resultsBaseRecords, 0, 3);
  }

  private BeanPropertyBindingResult errorsObject(Object toValidate) {
    return new BeanPropertyBindingResult(toValidate, "bean");
  }

  @Test
  public void deleteFolderValidation() throws Exception {
    mockBaseUrl();
    // root folder
    createdFolder.addType(RecordType.ROOT);
    when(folderMgr.getFolderSafe(1L, subject)).thenReturn(Optional.of(createdFolder));
    assertIllegalArgumentException(() -> controller.deleteFolder(1L, subject));

    // or any system folder
    createdFolder.removeType(RecordType.ROOT);
    createdFolder.setSystemFolder(true);
    assertIllegalArgumentException(() -> controller.deleteFolder(1L, subject));
    // not a folder or unauth
    when(folderMgr.getFolderSafe(1L, subject)).thenReturn(Optional.empty());
    CoreTestUtils.assertExceptionThrown(
        () -> controller.deleteFolder(1L, subject), NotFoundException.class);

    // happy case
    ServiceOperationResultCollection<CompositeRecordOperationResult, Long> result = successResult();
    context.setAttribute(UserSessionTracker.USERS_KEY, new UserSessionTracker());
    createdFolder.setSystemFolder(false);
    when(folderMgr.getFolderSafe(1L, subject)).thenReturn(Optional.of(createdFolder));
    when(deletionMgr.doDeletion(
            Mockito.any(Long[].class),
            Mockito.any(Supplier.class),
            Mockito.any(DeletionSettings.class),
            Mockito.eq(subject),
            Mockito.any(ProgressMonitor.class)))
        .thenReturn(result);
    controller.deleteFolder(1L, subject);

    // deletion fails internally
    result.addFailure(10L); // simulate a failure
    CoreTestUtils.assertExceptionThrown(
        () -> controller.deleteFolder(1L, subject), RuntimeException.class);
  }

  private ServiceOperationResultCollection<CompositeRecordOperationResult, Long> successResult() {
    ServiceOperationResultCollection<CompositeRecordOperationResult, Long> result =
        new ServiceOperationResultCollection<CompositeRecordOperationResult, Long>();
    result.addResult(new CompositeRecordOperationResult(null, null, null));
    return result;
  }
}
