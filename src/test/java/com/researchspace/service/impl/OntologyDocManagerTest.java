package com.researchspace.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.FolderManager;
import com.researchspace.service.FormManager;
import com.researchspace.service.RecordManager;
import com.researchspace.service.RecordSigningManager;
import com.researchspace.service.UserManager;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class OntologyDocManagerTest {
  public static final long ONTOLOGY_FOLDER_ID = 111L;
  public static final long USER_ROOT_FOLDER_ID = 666L;
  public static final long ICON_ID = 777L;
  public static final long ONTOLOGY_FORM_ID = 1001L;
  @Rule public MockitoRule rule = MockitoJUnit.rule();
  @Mock private RecordManager recordMgr;
  @Mock private UserManager userManager;
  @Mock private FolderManager folderManagerMock;
  @Mock private RecordSigningManager signingManagerMock;
  @Mock private FormManager formManager;
  @Mock private User userMock;
  @InjectMocks private OntologyDocManager testee;
  @Mock private Folder rootFolderMock;
  @Mock private StructuredDocument ontologyDocumentCreatedInWorkspaceMock;
  @Mock private Field firstFieldInontologyDocumentMock;
  @Mock private Field secondFieldInontologyDocumentMock;
  private final String lineBreakMidQuotedStringResourceName =
      "TestResources/ontology_files/line_break_mid_quotedstring_ontology_file.csv";
  private File lineBreakMidQuotedStringontologyDocument;
  private final String multipleLinesResourceName =
      "TestResources/ontology_files/multiple_lines_ontology_file.csv";
  private File multipleLinesontologyDocument;
  private final String tooLargeResourceName =
      "TestResources/ontology_files/toomany_lines_ontology_file.csv";
  private File tooLargeResourceFile;
  private final String singleColumnNoCommasSpecifiedResourceName =
      "TestResources/ontology_files/single_column_no_commas_file.csv";
  private File singleColumnNoCommasSpecifiedDocument;
  private RSForm ontologyForm;
  @Mock private Folder ontologyFolderMock;
  private String[] witnesses;

  @Before
  public void setUp() {
    ClassLoader classLoader = getClass().getClassLoader();
    lineBreakMidQuotedStringontologyDocument =
        new File(classLoader.getResource(lineBreakMidQuotedStringResourceName).getFile());
    multipleLinesontologyDocument =
        new File(classLoader.getResource(multipleLinesResourceName).getFile());
    singleColumnNoCommasSpecifiedDocument =
        new File(classLoader.getResource(singleColumnNoCommasSpecifiedResourceName).getFile());
    tooLargeResourceFile = new File(classLoader.getResource(tooLargeResourceName).getFile());
    when(ontologyDocumentCreatedInWorkspaceMock.getId()).thenReturn(1L);
    witnesses = new String[] {"NoWitnesses"};
  }

  @Test
  public void shouldWriteCSVWithLineBreakMidQuotedTextToontologyDocument() throws IOException {
    setUpMocksForCreatingontologyDocumentToWriteTo();
    ArgumentCaptor<String> captor = setupMocksForWritingDataToOntologyDocument();
    testee.writeImportToOntologyDoc(
        new FileInputStream(lineBreakMidQuotedStringontologyDocument), 2, 1, "name", "version");
    verify(firstFieldInontologyDocumentMock).setData(captor.capture());
    assertEquals(
        "__RSP_EXTONT_NAME__name__RSP_EXTONT_TAG_DELIM____RSP_EXTONT_VERSION__version__RSP_EXTONT_TAG_DELIM__HUP-T3"
            + " cell__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0006547",
        captor.getValue());
    verify(ontologyDocumentCreatedInWorkspaceMock).setName(eq("name_version_version.ontology"));
    verify(recordMgr, times(1)).save(eq(ontologyDocumentCreatedInWorkspaceMock), eq(userMock));
    verify(signingManagerMock)
        .signRecordNoPublishEvent(
            1L, userMock, witnesses, "Signed as the ontology name, version version");
  }

  @Test
  public void shouldWriteCSVWithSingleColumnNoCommasToOntologyDocument() throws IOException {
    setUpMocksForCreatingontologyDocumentToWriteTo();
    ArgumentCaptor<String> captor = setupMocksForWritingDataToOntologyDocument();
    testee.writeImportToOntologyDoc(
        new FileInputStream(singleColumnNoCommasSpecifiedDocument), 1, 1, "name", "version");
    verify(firstFieldInontologyDocumentMock).setData(captor.capture());
    assertEquals(
        "__RSP_EXTONT_NAME__name__RSP_EXTONT_TAG_DELIM____RSP_EXTONT_VERSION__version__RSP_EXTONT_TAG_DELIM__audi"
            + "__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_TAG_DELIM__BMW__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_TAG_DELIM__honda__RSP_EXTONT_URL_DELIM__NONE"
            + "__RSP_EXTONT_TAG_DELIM__volvo__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_TAG_DELIM__tesla__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_TAG_DELIM"
            + "__ford__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_TAG_DELIM__chevy__RSP_EXTONT_URL_DELIM__NONE__RSP_EXTONT_TAG_DELIM__RAM__RSP_EXTONT_URL_DELIM__NONE",
        captor.getValue());
    verify(ontologyDocumentCreatedInWorkspaceMock).setName(eq("name_version_version.ontology"));
    verify(recordMgr, times(1)).save(eq(ontologyDocumentCreatedInWorkspaceMock), eq(userMock));
    verify(signingManagerMock)
        .signRecordNoPublishEvent(
            1L, userMock, witnesses, "Signed as the ontology name, version version");
  }

  @Test
  public void shouldWriteCSVWithMultipleLinesToontologyDocument() throws IOException {
    setUpMocksForCreatingontologyDocumentToWriteTo();
    ArgumentCaptor<String> captor = setupMocksForWritingDataToOntologyDocument();
    testee.writeImportToOntologyDoc(
        new FileInputStream(multipleLinesontologyDocument), 2, 1, "name", "version");
    verify(firstFieldInontologyDocumentMock).setData(captor.capture());
    assertEquals(
        "__RSP_EXTONT_NAME__name__RSP_EXTONT_TAG_DELIM____RSP_EXTONT_VERSION__version__RSP_EXTONT_TAG_DELIM__culture"
            + " condition:camphor-grown"
            + " cell__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0006230__RSP_EXTONT_TAG_DELIM__BT-20"
            + " cell__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0001466__RSP_EXTONT_TAG_DELIM__insect"
            + " protocorm__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0006101",
        captor.getValue());
    verify(signingManagerMock)
        .signRecordNoPublishEvent(
            1L, userMock, witnesses, "Signed as the ontology name, version version");
  }

  @Test
  public void shouldThrowExceptionWhenIncorrectUrlColumnSpecified() {
    setUpMocksForCreatingontologyDocumentToWriteTo();
    ArgumentCaptor<String> captor = setupMocksForWritingDataToOntologyDocument();
    Exception thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                testee.writeImportToOntologyDoc(
                    new FileInputStream(multipleLinesontologyDocument), 2, 2, "name", "version"));
    assertEquals(
        "Import failed: You have specified a url column which does not contain urls.",
        thrown.getMessage());
    verify(signingManagerMock, never())
        .signRecordNoPublishEvent(
            1L, userMock, witnesses, "Signed as the ontology name, version version");
  }

  @Test
  public void shouldWriteCSVWithMultipleLinesToMultipleFieldsOfontologyDocument()
      throws IOException {
    ArgumentCaptor<String> captor = setupMocksForWritingDataToOntologyDocument();
    OntologyDocManager.OntologyDocWriter writer = new OntologyDocManager.OntologyDocWriter(2);
    BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(multipleLinesontologyDocument)));
    writer.writeToOntologyDoc(
        ontologyDocumentCreatedInWorkspaceMock, reader, 2, 1, "name", "version");
    verify(firstFieldInontologyDocumentMock).setData(captor.capture());
    assertEquals(
        "__RSP_EXTONT_NAME__name__RSP_EXTONT_TAG_DELIM____RSP_EXTONT_VERSION__version__RSP_EXTONT_TAG_DELIM__culture"
            + " condition:camphor-grown cell__RSP_EXTONT_URL_DELIM__"
            + "http://purl.obolibrary.org/obo/BTO_0006230__RSP_EXTONT_TAG_DELIM__BT-20"
            + " cell__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0001466",
        captor.getValue());
    verify(secondFieldInontologyDocumentMock).setData(captor.capture());
    assertEquals(
        "__RSP_EXTONT_NAME__name__RSP_EXTONT_TAG_DELIM____RSP_EXTONT_VERSION__version__RSP_EXTONT_TAG_DELIM__insect"
            + " protocorm__RSP_EXTONT_URL_DELIM__http://purl.obolibrary.org/obo/BTO_0006101",
        captor.getValue());
  }

  @Test
  public void shouldThrowExceptionWhenColumnOutOfRange() throws IOException {
    OntologyDocManager.OntologyDocWriter writer = new OntologyDocManager.OntologyDocWriter();
    BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(multipleLinesontologyDocument)));
    Exception thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                writer.writeToOntologyDoc(
                    ontologyDocumentCreatedInWorkspaceMock, reader, 1000, 1, "name", "version"));
    assertEquals(
        "Import failed: There are fewer columns in this csv file than the datacolumn you specified"
            + " at column 1001",
        thrown.getMessage());
  }

  @Test
  public void shouldThrowExceptionWhenTooManyTermsInImportFile() throws IOException {
    ArgumentCaptor<String> captor = setupMocksForWritingDataToOntologyDocument();
    OntologyDocManager.OntologyDocWriter writer = new OntologyDocManager.OntologyDocWriter(1);
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(new FileInputStream(tooLargeResourceFile)));
    Exception thrown =
        assertThrows(
            RuntimeException.class,
            () ->
                writer.writeToOntologyDoc(
                    ontologyDocumentCreatedInWorkspaceMock, reader, 2, 1, "name", "version"));
    assertEquals(
        "Import failed: Your ontology file was too large and could not be fully stored in RSpace,"
            + " please try splitting the file",
        thrown.getMessage());
  }

  @Test
  public void shouldWriteTagsToUserOntologyDocument() {
    Set<String> tags = Set.of("tag1", "tag2", "tag3");
    ArgumentCaptor<String> captor = setupMocksForWritingDataToOntologyDocument();
    setUpMocksForGettingUsersOwnOntologyDocument();
    testee.writeTagsToUsersOntologyTagDoc(userMock, tags);
    verify(firstFieldInontologyDocumentMock, times(2)).setData(captor.capture());
    List<String> tagsWritten = captor.getAllValues();
    // assertsThat all fields are cleared before writing new terms
    assertEquals("", tagsWritten.get(0));
    assertEquals("<p>tag1</p><p>tag2</p><p>tag3</p>", tagsWritten.get(1));
    verify(secondFieldInontologyDocumentMock).setData(captor.capture());
    tagsWritten = captor.getAllValues();
    // assertsThat all fields are cleared before writing new terms
    assertEquals("", tagsWritten.get(0));
  }

  @Test
  public void shouldCreateUserOntologyDocumentAndFolderWhenDocumentAndOntologiesFolderDoNotExist() {
    Set<String> tags = Set.of("tag1", "tag2", "tag3");
    setupMocksForWritingDataToOntologyDocument();
    setUpMocksForUsersOwnOntologyDocumentDoesNotExist();
    setupMocksForOntologyFolderAndFileCreation();
    testee.writeTagsToUsersOntologyTagDoc(userMock, tags);
    verify(folderManagerMock)
        .createNewFolder(eq(USER_ROOT_FOLDER_ID), eq("Ontologies"), eq(userMock));
    verify(recordMgr)
        .createNewStructuredDocument(eq(ONTOLOGY_FOLDER_ID), eq(ONTOLOGY_FORM_ID), eq(userMock));
  }

  private void setupMocksForOntologyFolderAndFileCreation() {
    when(folderManagerMock.createNewFolder(eq(USER_ROOT_FOLDER_ID), eq("Ontologies"), eq(userMock)))
        .thenReturn(ontologyFolderMock);
    when(ontologyFolderMock.getId()).thenReturn(ONTOLOGY_FOLDER_ID);
    when(recordMgr.createNewStructuredDocument(
            eq(ONTOLOGY_FOLDER_ID), eq(ONTOLOGY_FORM_ID), eq(userMock)))
        .thenReturn(ontologyDocumentCreatedInWorkspaceMock);
    when(ontologyFolderMock.getName()).thenReturn("Ontologies");
  }

  @Test
  public void
      shouldCreateUserOntologyDocumentAndFolderWhenDocumentDoesNotExistAndOntologiesFolderIsDeleted() {
    Set<String> tags = Set.of("tag1", "tag2", "tag3");
    setupMocksForWritingDataToOntologyDocument();
    setUpMocksForUsersOwnOntologyDocumentDoesNotExist();
    setupMocksForOntologyFolderAndFileCreation();
    when(rootFolderMock.getChildrens()).thenReturn(Set.of(ontologyFolderMock));
    when(ontologyFolderMock.isDeleted()).thenReturn(true);
    testee.writeTagsToUsersOntologyTagDoc(userMock, tags);
    verify(folderManagerMock)
        .createNewFolder(eq(USER_ROOT_FOLDER_ID), eq("Ontologies"), eq(userMock));
    verify(recordMgr)
        .createNewStructuredDocument(eq(ONTOLOGY_FOLDER_ID), eq(ONTOLOGY_FORM_ID), eq(userMock));
  }

  @Test
  public void
      shouldCreateUserOntologyDocumentButNotFolderWhenDocumentDoesNotExistButOntologiesFolderExists() {
    Set<String> tags = Set.of("tag1", "tag2", "tag3");
    setupMocksForWritingDataToOntologyDocument();
    setUpMocksForUsersOwnOntologyDocumentDoesNotExist();
    when(rootFolderMock.getChildrens()).thenReturn(Set.of(ontologyFolderMock));
    when(ontologyFolderMock.getName()).thenReturn("Ontologies");
    when(ontologyFolderMock.getId()).thenReturn(ONTOLOGY_FOLDER_ID);
    when(recordMgr.createNewStructuredDocument(
            eq(ONTOLOGY_FOLDER_ID), eq(ONTOLOGY_FORM_ID), eq(userMock)))
        .thenReturn(ontologyDocumentCreatedInWorkspaceMock);
    testee.writeTagsToUsersOntologyTagDoc(userMock, tags);
    verify(folderManagerMock, never())
        .createNewFolder(eq(USER_ROOT_FOLDER_ID), eq("Ontologies"), eq(userMock));
    verify(recordMgr)
        .createNewStructuredDocument(eq(ONTOLOGY_FOLDER_ID), eq(ONTOLOGY_FORM_ID), eq(userMock));
  }

  @Test
  public void shouldWriteTagsToUserOntologyDocumentClearingAllTags() {
    Set<String> tags = new HashSet<>();
    ArgumentCaptor<String> captor = setupMocksForWritingDataToOntologyDocument();
    setUpMocksForGettingUsersOwnOntologyDocument();
    testee.writeTagsToUsersOntologyTagDoc(userMock, tags);
    verify(firstFieldInontologyDocumentMock, times(2)).setData(captor.capture());
    List<String> tagsWritten = captor.getAllValues();
    // assertsThat all fields are cleared before writing new terms
    assertEquals("", tagsWritten.get(0));
    assertEquals("", tagsWritten.get(1));
    verify(secondFieldInontologyDocumentMock).setData(captor.capture());
    tagsWritten = captor.getAllValues();
    // assertsThat all fields are cleared before writing new terms
    assertEquals("", tagsWritten.get(0));
  }

  @Test
  public void shouldUpdateImportedOntologiesWithCorrectForm() {
    setUpMocksForImportedontologyDocumentsToGiveCorrectFormAndIconId("username");
    testee.updateImportedOntologiesWithCorrectForm("username");
    verify(ontologyDocumentCreatedInWorkspaceMock).setForm(ontologyForm);
    verify(ontologyDocumentCreatedInWorkspaceMock).setIconId(ICON_ID);
    verify(recordMgr).save(ontologyDocumentCreatedInWorkspaceMock, userMock);
  }

  @NotNull
  private ArgumentCaptor<String> setupMocksForWritingDataToOntologyDocument() {
    ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
    when(userManager.getAuthenticatedUserInSession()).thenReturn(userMock);
    when(ontologyDocumentCreatedInWorkspaceMock.getFields())
        .thenReturn(List.of(firstFieldInontologyDocumentMock, secondFieldInontologyDocumentMock));
    return captor;
  }

  private void setUpMocksForGettingUsersOwnOntologyDocument() {
    ontologyForm = new RSForm();
    ontologyForm.setId(ONTOLOGY_FORM_ID);
    when(formManager.findOldestFormByName(eq(CustomFormAppInitialiser.ONTOLOGY_FORM_NAME)))
        .thenReturn(ontologyForm);
    when(folderManagerMock.getRootRecordForUser(userMock, userMock)).thenReturn(rootFolderMock);
    when(rootFolderMock.getId()).thenReturn(USER_ROOT_FOLDER_ID);
    when(recordMgr.getOntologyTagsFilesForUserCalled(
            eq(userMock), eq(OntologyDocManager.USER_TAGS_ONTOLOGY_FILE)))
        .thenReturn(List.of(ontologyDocumentCreatedInWorkspaceMock));
  }

  private void setUpMocksForUsersOwnOntologyDocumentDoesNotExist() {
    ontologyForm = new RSForm();
    ontologyForm.setId(ONTOLOGY_FORM_ID);
    when(formManager.findOldestFormByName(eq(CustomFormAppInitialiser.ONTOLOGY_FORM_NAME)))
        .thenReturn(ontologyForm);
    when(folderManagerMock.getRootRecordForUser(userMock, userMock)).thenReturn(rootFolderMock);
    when(rootFolderMock.getId()).thenReturn(USER_ROOT_FOLDER_ID);
    when(recordMgr.getOntologyTagsFilesForUserCalled(
            eq(userMock), eq(OntologyDocManager.USER_TAGS_ONTOLOGY_FILE)))
        .thenReturn(new ArrayList<>());
  }

  private void setUpMocksForCreatingontologyDocumentToWriteTo() {
    ontologyForm = new RSForm();
    ontologyForm.setId(ONTOLOGY_FORM_ID);
    when(formManager.findOldestFormByName(eq(CustomFormAppInitialiser.ONTOLOGY_FORM_NAME)))
        .thenReturn(ontologyForm);
    when(folderManagerMock.getRootRecordForUser(userMock, userMock)).thenReturn(rootFolderMock);
    when(rootFolderMock.getId()).thenReturn(USER_ROOT_FOLDER_ID);
    when(recordMgr.createNewStructuredDocument(
            eq(rootFolderMock.getId()), eq(ontologyForm.getId()), eq(userMock)))
        .thenReturn(ontologyDocumentCreatedInWorkspaceMock);
  }

  private void setUpMocksForImportedontologyDocumentsToGiveCorrectFormAndIconId(String uName) {
    ontologyForm = new RSForm();
    ontologyForm.setId(ONTOLOGY_FORM_ID);
    ontologyForm.setIconId(ICON_ID);
    when(userManager.getUserByUsername(eq(uName), eq(true))).thenReturn(userMock);
    when(formManager.findOldestFormByName(eq(CustomFormAppInitialiser.ONTOLOGY_FORM_NAME)))
        .thenReturn(ontologyForm);
    when(folderManagerMock.getRootRecordForUser(userMock, userMock)).thenReturn(rootFolderMock);
    when(rootFolderMock.getId()).thenReturn(USER_ROOT_FOLDER_ID);
    when(recordMgr.getontologyDocumentsCreatedInPastThirtyMinutesByCurrentUser(eq(uName)))
        .thenReturn(List.of(ontologyDocumentCreatedInWorkspaceMock));
  }
}
