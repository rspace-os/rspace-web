package com.researchspace.webapp.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.Constants;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.linkedelements.FieldParser;
import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.Group;
import com.researchspace.model.RecordAttachment;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.Snippet;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.RecordCopyResult;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.List;
import java.util.Set;
import org.jsoup.Jsoup;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class LinkedItemsPermissionsMVCIT extends MVCTestBase {

  private User user1;
  private User other;
  private Principal principal;

  @Autowired FieldParser fieldParser;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    user1 = createInitAndLoginAnyUser();
    principal = new MockPrincipal(user1.getUsername());
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void unauthorisedUserCantAccessURLsInTextFields() throws Exception {
    StructuredDocument sdoc = createComplexDocument(user1);
    List<String> alllinks = getLinksFromBasicDoc(sdoc);

    other = createInitAndLoginAnyUser();
    principal = new MockPrincipal(other.getUsername());
    assertAllLinksAreAuthorised(alllinks, false, false);
  }

  List<String> getLinksFromBasicDoc(StructuredDocument sdoc) throws Exception {
    Field field = sdoc.getFields().get(0);
    openTransaction();
    FieldContents contents = fieldParser.findFieldElementsInContent(field.getFieldData());
    commitTransaction();

    List<String> alllinks = contents.getAllStringLinks();
    // check links are OK for logged in user
    return alllinks;
  }

  @Test
  public void sharedNotebookWithComplexDocumentAllAttachmentsViewable() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUser(pi);
    logoutAndLoginAs(pi);
    Group grp = createGroupForUsers(pi, pi.getUsername(), "", user1, pi);

    Notebook nb = createNotebookWithNEntries(getRootFolderForUser(pi).getId(), "any", 1, pi);
    StructuredDocument doc = createComplexDocumentInFolder(pi, nb);
    shareRecordWithGroup(pi, grp, doc);

    logoutAndLoginAs(user1);
    List<String> alllinks = getLinksFromBasicDoc(doc);
    assertAllLinksAreAuthorised(alllinks, true, true);
  }

  @Test
  public void onlyUserCanAccessLinksInSnippet() throws Exception {
    StructuredDocument sdoc = createComplexDocument(user1);
    Field field = sdoc.getFields().get(0);
    String originaltext = field.getFieldData();
    String originalTextParsed = Jsoup.parse(originaltext).body().html();

    openTransaction();
    FieldContents originalContents = fieldParser.findFieldElementsInContent(originaltext);
    assertTrue(originalContents.getAllMediaFiles().size() > 0);
    List<String> originalLinks = originalContents.getAllStringLinks();
    commitTransaction();

    Snippet snip = recordMgr.createSnippet("complex", originaltext, user1);

    openTransaction();
    FieldContents snipContents = fieldParser.findFieldElementsInContent(snip.getContent());
    List<String> snipLinks = snipContents.getAllStringLinks();
    commitTransaction();

    // check contents, should have same number of elements
    assertNotEquals(originalTextParsed, snip.getContent());
    assertEquals(originalLinks.size(), snipLinks.size());
    assertEquals(
        originalContents.getAllMediaFiles().size(), snipContents.getAllMediaFiles().size());

    // snippet should have all media linked with record attachments
    assertEquals(originalContents.getAllMediaFiles().size(), snip.getLinkedMediaFiles().size());

    // check links are OK for logged in user
    assertAllLinksAreAuthorised(snipLinks, true, false);

    // check other user can't access the links
    other = createInitAndLoginAnyUser();
    principal = new MockPrincipal(other.getUsername());
    assertAllLinksAreAuthorised(snipLinks, false, false);

    // now other user tries to creates a snippet with same content (RSPAC-707)
    Snippet otherSnip = recordMgr.createSnippet("complex", originaltext, other);

    // but content shouldn't be updated as user don't have permission to any media in content
    assertEquals(originalTextParsed, otherSnip.getContent());

    // and there should be no record attachments in that new snippet
    Set<RecordAttachment> otherSnipMediaFiles = otherSnip.getLinkedMediaFiles();
    assertNotNull(otherSnipMediaFiles);
    assertEquals(0, otherSnipMediaFiles.size());
  }

  @Test
  public void onlyUserCanAccessLinksAfterCopyingDocument() throws Exception {
    StructuredDocument sdoc = createComplexDocument(user1);
    Field field = sdoc.getFields().get(0);
    String originaltext = field.getFieldData();
    String originalTextParsed = Jsoup.parse(originaltext).body().html();

    openTransaction();
    FieldContents originalContents = fieldParser.findFieldElementsInContent(originaltext);
    List<EcatMediaFile> originalContentsMediaFiles =
        originalContents.getAllMediaFiles().getElements();
    assertTrue(originalContentsMediaFiles.size() > 0); // also does lazy init
    List<String> originalLinks = originalContents.getAllStringLinks();
    commitTransaction();

    RecordCopyResult copyResult =
        recordMgr.copy(sdoc.getId(), sdoc.getName() + "copy", user1, null);

    openTransaction();
    StructuredDocument copy = (StructuredDocument) recordMgr.get(copyResult.getCopy(sdoc).getId());
    Field fieldCopy = copy.getFields().get(0);
    fieldCopy.getLinkedMediaFiles().size(); // for lazy init

    FieldContents copyContents = fieldParser.findFieldElementsInContent(fieldCopy.getFieldData());
    List<String> copyLinks = copyContents.getAllStringLinks();
    commitTransaction();

    // check contents, should have same number of elements
    assertNotEquals(originalTextParsed, fieldCopy.getFieldData());
    assertEquals(originalLinks.size(), copyLinks.size());
    assertEquals(originalContentsMediaFiles.size(), copyContents.getAllMediaFiles().size());

    // copied field should have all media linked through field attachments
    assertEquals(originalContentsMediaFiles.size(), fieldCopy.getLinkedMediaFiles().size());

    // check links are OK for logged in user
    assertAllLinksAreAuthorised(copyLinks, true, false);

    // check other user can't access copied links
    other = createInitAndLoginAnyUser();
    principal = new MockPrincipal(other.getUsername());
    assertAllLinksAreAuthorised(copyLinks, false, false);

    // other user tries to creates a document with same content
    StructuredDocument otherDoc = createBasicDocumentInRootFolderWithText(other, "toLinkFrom");
    Field otherField = otherDoc.getFields().get(0);
    otherField.setFieldData(originaltext);
    recordMgr.save(otherDoc, other);

    // now other user copies that document
    RecordCopyResult otherCopyResult =
        recordMgr.copy(otherDoc.getId(), otherDoc.getName() + "copy", other, null);
    openTransaction();
    StructuredDocument otherCopy =
        (StructuredDocument) recordMgr.get(otherCopyResult.getCopy(otherDoc).getId());
    Field otherFieldCopy = otherCopy.getFields().get(0);
    otherFieldCopy.getLinkedMediaFiles().size(); // for lazy init
    commitTransaction();

    // but content shouldn't be updated as user don't have permission to any media in content
    assertEquals(originalTextParsed, otherFieldCopy.getFieldData());

    // and there should be no field attachments in copy
    Set<FieldAttachment> otherFieldCopyMediaFiles = otherFieldCopy.getLinkedMediaFiles();
    assertNotNull(otherFieldCopyMediaFiles);
    assertEquals(0, otherFieldCopyMediaFiles.size());
  }

  // iterate over found links and assert that they are authorised ( or not).
  private void assertAllLinksAreAuthorised(
      List<String> alllinks, boolean isAuthorised, boolean skipLinkedDocs) throws Exception {
    for (String link : alllinks) {
      if (link.startsWith("/")) {
        if (skipLinkedDocs
            && (link.startsWith("/globalId/SD")
                || link.startsWith("/globalId/FL")
                || link.startsWith("/globalId/NB"))) {
          continue;
        }
        doAuthorisationCheck(isAuthorised, link);
      }
    }
  }

  private void doAuthorisationCheck(boolean isAuthorised, String link)
      throws Exception, UnsupportedEncodingException {

    System.err.println("looking at link " + link);
    String linkToCheck = link;
    if (link.contains("/globalId/")) {
      /* globalId url returns 302 redirect, so for permissions check let's resolve to full address */
      linkToCheck =
          linkToCheck.replace(
              "/globalId/SD", StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL + "/");
      linkToCheck = linkToCheck.replace("/globalId/NB", NotebookEditorController.ROOT_URL + "/");
      linkToCheck = linkToCheck.replace("/globalId/FL", WorkspaceController.ROOT_URL + "/");
    }
    MvcResult result =
        mockMvc
            .perform(get(linkToCheck).principal(principal))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    if (isAuthorised) {
      assertNull(result.getResolvedException());
    } else {
      assertAuthorizationException(result);
    }
  }
}
