package com.researchspace.webapp.controller;

import static com.researchspace.testutils.RSpaceTestUtils.getResource;
import static com.researchspace.webapp.controller.StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL;
import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.fileUpload;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.axiope.service.cfg.RSDevConfig.DummyWord2HTMLConverter;
import com.researchspace.Constants;
import com.researchspace.core.util.PaginationObject;
import com.researchspace.core.util.PaginationUtil;
import com.researchspace.core.util.Transformer;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.linkedelements.FieldContents;
import com.researchspace.model.EcatImage;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.Group;
import com.researchspace.model.IFieldLinkableElement;
import com.researchspace.model.SignatureHash;
import com.researchspace.model.SignatureHashInfo;
import com.researchspace.model.SignatureHashType;
import com.researchspace.model.SignatureInfo;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.dtos.RevisionSearchCriteria;
import com.researchspace.model.field.ErrorList;
import com.researchspace.model.field.Field;
import com.researchspace.model.permissions.PermissionType;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.EditInfo;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Notebook;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.RecordInformation;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.model.views.RecordCopyResult;
import com.researchspace.service.AuditManager;
import com.researchspace.service.DefaultRecordContext;
import com.researchspace.service.DocumentCopyManager;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.TestGroup;
import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.hibernate.criterion.Projections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MvcResult;

@WebAppConfiguration
public class SDocControllerMVCIT extends MVCTestBase {

  private @Autowired DummyWord2HTMLConverter dummyConverter;
  private @Autowired AuditManager auditMgr;
  @Autowired DocumentCopyManager docCopyMgr;

  @Before
  public void setup() throws Exception {
    super.setUp();
  }

  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Test
  public void createFromWordFile() throws Exception {
    User anyUser = createInitAndLoginAnyUser();

    Folder target = folderMgr.getRootFolderForUser(anyUser);

    MvcResult result =
        mockMvc
            .perform(
                fileUpload(
                        STRUCTURED_DOCUMENT_EDITOR_URL + "/ajax/createFromWord/{parentId}",
                        target.getId())
                    .file(dummyConverter.getMultiFile()))
            .andExpect(jsonPath("$.data[0].id").isNotEmpty())
            .andExpect(jsonPath("$.data[0].name").isNotEmpty())
            .andExpect(jsonPath("$.error.errorMessages").isEmpty())
            .andReturn();
    List<Map> data = getFromJsonAjaxReturnObject(result, List.class);
    Long id = getIdOfDoc(data);
    String name = getNameOfDoc(data);

    assertNotNull(id);
    assertThat(dummyConverter.getWordHtml().getName(), containsString(name));
    Predicate<Boolean> inv =
        t ->
            fieldMgr
                .getFieldsByRecordId(id, anyUser)
                .get(0)
                .getFieldData()
                .contains("brown fox jumps over");

    // ,may take some time to get loaded?
    Predicate<Boolean> test = inv;
    assertTrue(waitForConditionTrue(test, null, 5000L));

    // assert invalid file rejected, we can't import png files to RSpace docs:
    File invalidFile = getResource("mainLogoN2.png");
    MockMultipartFile invalid =
        new MockMultipartFile(
            "wordXfile", invalidFile.getName(), "unknown", readFileToByteArray(invalidFile));
    MvcResult result2 =
        mockMvc
            .perform(
                fileUpload(
                        STRUCTURED_DOCUMENT_EDITOR_URL + "/ajax/createFromWord/{parentId}",
                        target.getId())
                    .file(invalid))
            .andExpect(jsonPath("$.data").isEmpty())
            .andExpect(jsonPath("$.error.errorMessages").isNotEmpty())
            .andReturn();
  }

  @Test
  public void replaceFromWordFileRSPAC931() throws Exception {

    User anyUser = createInitAndLoginAnyUser();

    StructuredDocument docToUpdate = createBasicDocumentInRootFolderWithText(anyUser, "replaceMe");

    Folder target = folderMgr.getRootFolderForUser(anyUser);

    MvcResult result =
        mockMvc
            .perform(
                fileUpload(
                        STRUCTURED_DOCUMENT_EDITOR_URL + "/ajax/createFromWord/{parentId}",
                        target.getId())
                    .file(dummyConverter.getMultiFile())
                    .param("recordToReplaceId", docToUpdate.getId() + ""))
            .andReturn();
    final int maxTries = 3;
    // this assert is fragile on Jenkins, try several times in case it's a timing issue.
    int tryCount = 0;
    String fieldText = "";
    final String expected = "The quick brown fox";
    while (tryCount <= maxTries) {
      tryCount++;
      fieldText = fieldMgr.getFieldsByRecordId(docToUpdate.getId(), anyUser).get(0).getFieldData();
      if (!StringUtils.isBlank(fieldText)) {
        assertThat(fieldText, containsString(expected));
      } else {
        log.warn("Field text is empty: trying again: attempt {} failed", tryCount);
      }
    }
    if (tryCount == maxTries && StringUtils.isEmpty(fieldText)) {
      fail(String.format("Field text was empty but should contain string '%s'", expected));
    }

    assertThat(fieldText, not(containsString("replaceMe")));
    // check revision history is updated following the replace
    openTransaction();
    AuditedEntity<StructuredDocument> latest =
        auditMgr.getNewestRevisionForEntity(StructuredDocument.class, docToUpdate.getId());
    assertThat(
        latest.getEntity().getFields().get(0).getFieldData(),
        containsString("The quick brown fox"));
    commitTransaction();
  }

  private String getNameOfDoc(List<Map> data) {
    return data.get(0).get("name").toString();
  }

  private long getIdOfDoc(List<Map> data) {
    return Long.parseLong(data.get(0).get("id").toString());
  }

  @Test
  public void testSharedTemplateFromComplexDocPermissions_RSPAC972_RSPAC1760() throws Exception {
    User u1 = createAndSaveUser(getRandomAlphabeticString("u1"));
    User u2 = createAndSaveUser(getRandomAlphabeticString("u2"));
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    initUsers(u1, u2, pi);
    Group grp = createGroupForUsers(pi, pi.getUsername(), null, pi, u1, u2);

    logoutAndLoginAs(u1);
    StructuredDocument doc = createComplexDocument(u1);
    Long docFieldId = doc.getFields().get(0).getId();
    Set<FieldAttachment> docFieldAttachments =
        fieldMgr.getWithLoadedMediaLinks(docFieldId, u1).get().getLinkedMediaFiles();
    int docFieldAttachmentsCount = docFieldAttachments.size();

    openTransaction();
    StructuredDocument template = createTemplateFromDocumentAndAddtoTemplateFolder(doc.getId(), u1);
    Long templateFieldId = template.getFields().get(0).getId();
    commitTransaction();
    String templateContent = template.getFirstFieldData();
    assertEquals(
        "template content: "
            + templateContent
            + " shouldn't contain orginal field id: "
            + docFieldId,
        0,
        StringUtils.countMatches(templateContent, "id=\"" + docFieldId + "-"));
    Set<FieldAttachment> templateFieldAttachments =
        fieldMgr.getWithLoadedMediaLinks(templateFieldId, u1).get().getLinkedMediaFiles();
    assertEquals(
        "template should have as many FieldAttachments as original doc",
        docFieldAttachmentsCount,
        templateFieldAttachments.size()
            - 1); // template creation adds image from annotation as extra FieldAttachment

    shareRecordWithGroup(u1, grp, template);

    logoutAndLoginAs(u2);
    StructuredDocument fromTemplate = createFromTemplate(template, u2);
    Long fromTemplateFieldId = fromTemplate.getFields().get(0).getId();
    String fromTemplateContent = fromTemplate.getFirstFieldData();
    assertEquals(
        "fromTemplate content: "
            + fromTemplateContent
            + " shouldn't contain template field id: "
            + templateFieldId,
        0,
        StringUtils.countMatches(fromTemplateContent, "id=\"" + templateFieldId + "-"));
    Set<FieldAttachment> fromTemplateFieldAttachments =
        fieldMgr.getWithLoadedMediaLinks(fromTemplateFieldId, u1).get().getLinkedMediaFiles();
    assertEquals(
        "fromTemplate should have as many FieldAttachments as original doc",
        templateFieldAttachments.size(),
        fromTemplateFieldAttachments.size());

    assertAllLinkedElementsNotLinkedDocsCanBeRead(u2, fromTemplate);

    logoutAndLoginAs(u1);
    unshareRecordORNotebookWithGroup(u1, template, grp, "read");

    logoutAndLoginAs(u2);
    assertAllLinkedElementsNotLinkedDocsCanBeRead(u2, fromTemplate);
  }

  @Test
  public void testSharedTemplateFromDocWithImage_RSPAC1760() throws Exception {
    User pi = createAndSaveUser(getRandomAlphabeticString("pi"), Constants.PI_ROLE);
    User u1 = createAndSaveUser(getRandomAlphabeticString("u1"));
    initUsers(pi, u1);
    Group grp = createGroupForUsers(pi, pi.getUsername(), null, pi, u1);

    logoutAndLoginAs(pi);

    // create doc with image
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(pi, "sharingTemplateTest");
    Field docField = doc.getFields().get(0);
    EcatImage image = addImageToField(docField, pi);
    assertEquals(1, mediaMgr.getIdsOfLinkedDocuments(image.getId()).size());

    // create template from doc
    openTransaction();
    StructuredDocument template = createTemplateFromDocumentAndAddtoTemplateFolder(doc.getId(), pi);
    Field templateField = template.getFields().get(0);
    String templateContent = templateField.getFieldData();
    commitTransaction();
    assertEquals(
        "template content: "
            + templateContent
            + " shouldn't contain orginal field id: "
            + docField.getId(),
        0,
        StringUtils.countMatches(templateContent, "id=\"" + docField.getId() + "-"));
    assertEquals(2, mediaMgr.getIdsOfLinkedDocuments(image.getId()).size());

    // share template with group
    shareRecordWithGroup(pi, grp, template);

    // as a group member use shared template to create doc
    logoutAndLoginAs(u1);
    StructuredDocument fromTemplate = createFromTemplate(template, u1);
    Field fromTemplateField = fromTemplate.getFields().get(0);
    String fromTemplateContent = fromTemplateField.getFieldData();
    assertEquals(
        "fromTemplate content: "
            + fromTemplateContent
            + " shouldn't contain template field id: "
            + templateField.getId(),
        0,
        StringUtils.countMatches(fromTemplateContent, "id=\"" + templateField.getId() + "-"));
    assertEquals(3, mediaMgr.getIdsOfLinkedDocuments(image.getId()).size());
  }

  private void assertAllLinkedElementsNotLinkedDocsCanBeRead(User user, StructuredDocument doc)
      throws Exception {
    doInTransaction(
        () -> {
          StructuredDocument docWithFields = recordMgr.get(doc.getId()).asStrucDoc();
          FieldContents fc =
              fieldParser.findFieldElementsInContent(
                  docWithFields.getFields().get(0).getFieldData());
          for (IFieldLinkableElement el : fc.getAllLinks().getElements()) {
            log.info("Looking at {}", el.getOid());
            if (!(el instanceof RecordInformation)) {
              assertTrue(
                  permissionUtils.isPermittedViaMediaLinksToRecords(el, PermissionType.READ, user));
            }
          }
        });
  }

  private StructuredDocument createFromTemplate(StructuredDocument template, User user) {
    Long folderId = folderMgr.getRootFolderForUser(user).getId();
    RecordCopyResult fromTemplateResult =
        recordMgr.createFromTemplate(template.getId(), "from" + template.getName(), user, folderId);
    return fromTemplateResult.getCopy(template).asStrucDoc();
  }

  @Test
  public void testCreateViewAndUseTemplates() throws Exception {
    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    String[] idArray = getFieldsFromDocument(sd);
    long originalChildCount = totalAllChildrenInFolder(sd.getParent().getId());
    int originalTemplateCount = totalTemplateCount().intValue();

    // create a template usign all fields
    this.mockMvc
        .perform(
            post(STRUCTURED_DOCUMENT_EDITOR_URL + "/saveTemplate")
                .param("fieldCompositeIds[]", idArray)
                .param("templateName", "new template")
                .param("recordId", sd.getId() + "")
                .principal(mockPrincipal))
        .andExpect(status().isOk())
        .andReturn();
    assertEquals(originalTemplateCount + 1, totalTemplateCount().intValue());

    // chekc is in media gallery
    Folder templateMediaFolder = folderMgr.getTemplateFolderForUser(piUser);
    StructuredDocument template = null;
    List<BaseRecord> its =
        recordMgr
            .listFolderRecords(templateMediaFolder.getId(), DEFAULT_RECORD_PAGINATION)
            .getResults();
    for (BaseRecord br : its) {
      if (br.isStructuredDocument()) {
        template = (StructuredDocument) br;
        break;
      }
    }

    // create a record from the template
    this.mockMvc
        .perform(
            post(StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                    + "/createFromTemplate/"
                    + sd.getParent().getId())
                .param("template", template.getId() + "")
                .principal(mockPrincipal))
        .andReturn();
    // check 1 more record added to original doc's parent
    assertEquals(
        originalChildCount + 1, totalAllChildrenInFolder(sd.getParent().getId()).longValue());
  }

  private String[] getFieldsFromDocument(StructuredDocument sd) {
    List<Field> fields = fieldMgr.getFieldsByRecordId(sd.getId(), piUser);
    String[] idArray = new String[fields.size()];
    TransformerUtils.transform(
            fields,
            new Transformer<String, Field>() {
              @Override
              public String transform(Field toTransform) {
                return "somestring_" + toTransform.getId();
              }
              ;
            })
        .toArray(idArray);
    return idArray;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Test
  public void testListRevisions() throws Exception {
    final int ntimes = 12;

    StructuredDocument sd = setUpLoginAsPIUserAndCreateADocument();
    renameDocumentNTimes(sd, ntimes);
    // test for equality with this object
    RevisionSearchCriteria rsc = new RevisionSearchCriteria();
    rsc.setModifiedBy(piUser.getUsername());
    MvcResult result =
        this.mockMvc
            .perform(
                get("/workspace/revisionHistory/list/" + sd.getId())
                    .param("modifiedBy", piUser.getUsername())
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andExpect(model().attribute("recordId", sd.getId()))
            // check has search value passed in
            .andExpect(model().attribute("searchCriteria", rsc))
            .andReturn();

    // only 10 records returned, as is paginated.
    final int nresult = 10;
    List<AuditedRecord> docs =
        (List<AuditedRecord>) result.getModelAndView().getModelMap().get("history");
    assertEquals(nresult, docs.size());

    // 2 pages
    List<PaginationObject> pgObs =
        (List<PaginationObject>)
            result
                .getModelAndView()
                .getModelMap()
                .get(PaginationUtil.PAGINATION_LIST_MODEL_ATTR_NAME);
    assertEquals(2, pgObs.size());
  }

  @Test
  public void testPrceedWitness() throws Exception {
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    MvcResult res2 = postDocumentSign(setup, setup.structuredDocument.getId());
    logoutAndLoginAs(setup.user);
    MvcResult res3 =
        mockMvc
            .perform(
                post(STRUCTURED_DOCUMENT_EDITOR_URL + "/ajax/proceedWitnessing/")
                    .param("option", Boolean.TRUE.toString())
                    .param("recordId", setup.structuredDocument.getId() + "")
                    .param("password", TESTPASSWD)
                    .principal(new MockPrincipal(setup.user.getUsername())))
            .andExpect(status().isOk())
            .andReturn();
    SignatureInfo sign2 = getFromJsonAjaxReturnObject(res3, SignatureInfo.class);
    assertThat(sign2.getWitnesses().keySet(), contains(setup.user.getFullName()));
  }

  @Test
  public void testSign() throws Exception {
    int initialSignaturehasCount = getSigHashCount();
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    MvcResult res1 = postDocumentSign(setup, setup.structuredDocument.getId());

    SignatureInfo sign = getFromJsonAjaxReturnObject(res1, SignatureInfo.class);
    assertNotNull(sign);
    assertNotNull(sign.getId());
    assertEquals(piUser.getFullName(), sign.getSignerFullName());
    assertNotNull(sign.getSignDate());
    assertEquals(1, sign.getWitnesses().size());
    int EXPECTED_NEW_SIG_HASHES = 4;
    assertEquals(EXPECTED_NEW_SIG_HASHES, sign.getHashes().size());

    int finalSigHashCount = getSigHashCount();
    assertEquals(
        "Correct number of signature hashes weren't generated ",
        initialSignaturehasCount + EXPECTED_NEW_SIG_HASHES,
        finalSigHashCount);

    openTransaction();
    Folder root = folderDao.getRootRecordForUser(piUser);
    commitTransaction();
    StructuredDocument newdoc = recordMgr.createBasicDocument(root.getId(), piUser);
    MvcResult res2 = postDocumentSign(setup, newdoc.getId());

    SignatureInfo sign2 = getFromJsonAjaxReturnObject(res2, SignatureInfo.class);
    assertNotNull(sign2);
    assertEquals(piUser.getFullName(), sign2.getSignerFullName());
    assertNotNull(sign2.getSignDate());
    assertEquals(1, sign2.getWitnesses().size());
    assertEquals(4, sign.getHashes().size());

    // find content hash in signature
    SignatureHashInfo[] hashes = sign2.getHashes().toArray(new SignatureHashInfo[] {});
    SignatureHashInfo contentHashFromSignature = null;
    for (SignatureHashInfo hash : hashes) {
      if (hash.getType().equals(SignatureHashType.CONTENT.toString())) {
        contentHashFromSignature = hash;
      }
    }

    // compare with latest content hash, it should match
    MvcResult res3 =
        this.mockMvc
            .perform(
                get(STRUCTURED_DOCUMENT_EDITOR_URL + "/ajax/currentContentHash/" + newdoc.getId())
                    .principal(mockPrincipal))
            .andReturn();
    String currentHashValue = res3.getResponse().getContentAsString();
    assertNotNull(currentHashValue);
    assertEquals(currentHashValue, contentHashFromSignature.getHexValue());
  }

  private MvcResult postDocumentSign(GroupSetUp setup, Long docToSignId) throws Exception {
    MvcResult res1 =
        this.mockMvc
            .perform(
                post(STRUCTURED_DOCUMENT_EDITOR_URL + "/ajax/proceedSigning/")
                    .param("recordId", docToSignId + "")
                    .param("statement", "any")
                    .param("witnesses[]", new String[] {setup.user.getUsername()})
                    .param("username", piUser.getUsername())
                    .param("password", TESTPASSWD)
                    .principal(mockPrincipal))
            .andReturn();
    return res1;
  }

  private int getSigHashCount() throws Exception {
    return doInTransaction(
        () ->
            ((Long)
                    sessionFactory
                        .getCurrentSession()
                        .createCriteria(SignatureHash.class)
                        .setProjection(Projections.rowCount())
                        .uniqueResult())
                .intValue());
  }

  @Test
  public void testGetPotentialWitnesses() throws Exception {
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();

    MvcResult result =
        this.mockMvc
            .perform(
                get(StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/getPotentialWitnesses")
                    .param("recordId", setup.structuredDocument.getId() + "")
                    .principal(mockPrincipal))
            .andReturn();

    Map jsonResponse = parseJSONObjectFromResponseStream(result);
    assertNotNull(jsonResponse.get("data"));
  }

  @Test
  public void testSignFail() throws Exception {
    GroupSetUp setup = setUpDocumentGroupForPIUserAndShareRecord();
    MvcResult result1 =
        this.mockMvc
            .perform(
                post(StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/ajax/proceedSigning/")
                    .param("recordId", setup.structuredDocument.getId() + "")
                    .param("statement", "any")
                    .param("witnesses[]", new String[] {setup.user.getUsername()})
                    .param("username", piUser.getUsername())
                    .param("password", "WRONG_PASSWORD")
                    .principal(mockPrincipal))
            .andReturn();

    ErrorList errorList = getErrorListFromAjaxReturnObject(result1);
    assertNotNull(errorList);
    assertEquals(1, errorList.getErrorMessages().size());
  }

  @Test
  public void createFromForm() throws Exception {
    User u = createInitAndLoginAnyUser();
    RSForm form = createAnyForm(u);
    Long rootFolder = folderMgr.getRootRecordForUser(u, u).getId();
    final int initialRecordsCount = (int) getRecordCountInFolderForUser(rootFolder);
    this.mockMvc
        .perform(
            post(
                    StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/create/{recordId}",
                    rootFolder + "")
                .param("template", form.getId() + "")
                .principal(new MockPrincipal(u.getUsername())))
        .andExpect(status().is3xxRedirection())
        .andReturn();
    assertEquals(initialRecordsCount + 1, getRecordCountInFolderForUser(rootFolder));
  }

  @Test
  public void createIntoSharedFolder() throws Exception {
    TestGroup group = createTestGroup(2);
    User u = group.u1();
    initUser(u);
    RSpaceTestUtils.login(u.getUsername(), TESTPASSWD);
    RSForm form = createAnyForm(u);

    openTransaction();
    Long sharedFolderId = group.getGroup().getCommunalGroupFolderId();
    commitTransaction();
    Long rootFolderId = folderMgr.getRootRecordForUser(u, u).getId();
    final int initialRecordsCount = (int) getRecordCountInFolderForUser(rootFolderId);
    MvcResult resultUrl =
        this.mockMvc
            .perform(
                post(
                        StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                            + "/create/{recordId}",
                        sharedFolderId + "")
                    .param("template", form.getId() + "")
                    .principal(new MockPrincipal(u.getUsername())))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    String redirectUrl = resultUrl.getResponse().getHeader("Location");

    assertEquals(initialRecordsCount + 1, getRecordCountInFolderForUser(rootFolderId));
    assertEquals(1, getRecordCountInFolderForUser(sharedFolderId));
    assertTrue(redirectUrl.contains("&sharedWithGroup=" + group.getGroup().getDisplayName()));
  }

  @Test
  public void createIntoSharedNotebook() throws Exception {
    TestGroup group = createTestGroup(2);
    User u = group.u1();
    initUser(u);
    RSpaceTestUtils.login(u.getUsername(), TESTPASSWD);
    RSForm form = createAnyForm(u);

    openTransaction();
    Long sharedFolderId = group.getGroup().getCommunalGroupFolderId();
    commitTransaction();
    Long rootFolderId = folderMgr.getRootRecordForUser(u, u).getId();
    final int initialRecordsCount = (int) getRecordCountInFolderForUser(rootFolderId);
    MvcResult resultUrl =
        this.mockMvc
            .perform(
                post("/workspace/create_notebook/{recordid}", sharedFolderId + "")
                    .param("notebookNameField", "sharedNotebook")
                    .principal(new MockPrincipal(u.getUsername())))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    String redirectUrl = resultUrl.getResponse().getHeader("Location");
    String notebookId = redirectUrl.split("/")[2].split("\\?")[0];

    assertEquals(initialRecordsCount + 1, getRecordCountInFolderForUser(rootFolderId));
    assertEquals(1, getRecordCountInFolderForUser(sharedFolderId));
    assertTrue(redirectUrl.contains("sharedWithGroup=" + group.getGroup().getDisplayName()));

    resultUrl =
        this.mockMvc
            .perform(
                post(
                        StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                            + "/create/{recordId}",
                        notebookId)
                    .param("template", form.getId() + "")
                    .principal(new MockPrincipal(u.getUsername())))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    redirectUrl = resultUrl.getResponse().getHeader("Location");

    assertEquals(1, getRecordCountInFolderForUser(Long.valueOf(notebookId)));
    assertTrue(redirectUrl.contains("fromNotebook=" + notebookId));
  }

  @Test
  public void createEntry() throws Exception {
    User u = createInitAndLoginAnyUser();

    Long rootFolder = folderMgr.getRootRecordForUser(u, u).getId();
    Folder notebook =
        folderMgr.createNewNotebook(rootFolder, "NewNotebook", new DefaultRecordContext(), u);

    final int initialEntriesCount = (int) getRecordCountInFolderForUser(notebook.getId());
    this.mockMvc
        .perform(
            post(
                    StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/createEntry/{notebookid}",
                    notebook.getId() + "")
                .principal(new MockPrincipal(u.getUsername())))
        .andExpect(status().is3xxRedirection())
        .andReturn();
    assertEquals(initialEntriesCount + 1, getRecordCountInFolderForUser(notebook.getId()));
  }

  @Test
  public void testNotebookEntriesWithAjax() throws Exception {
    User user = createInitAndLoginAnyUser();
    Long rootFolderId = folderMgr.getRootRecordForUser(user, user).getId();
    Folder notebook =
        folderMgr.createNewNotebook(
            rootFolderId, "NewAjaxNotebook", new DefaultRecordContext(), user);

    MvcResult defaultEntry =
        mockMvc
            .perform(
                post(StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/ajax/createEntry")
                    .param("notebookId", notebook.getId().toString())
                    .principal(user::getUsername))
            .andReturn();
    Long defaultEntryId = getFromJsonResponseBody(defaultEntry, Long.class);
    assertNotNull(defaultEntryId);

    String testName = "newTestEntry";
    MvcResult namedEntry =
        mockMvc
            .perform(
                post(StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/ajax/createEntry")
                    .param("notebookId", notebook.getId().toString())
                    .param("entryName", testName)
                    .principal(user::getUsername))
            .andReturn();
    Long namedEntryId = getFromJsonResponseBody(namedEntry, Long.class);
    assertNotNull(namedEntryId);

    openTransaction();
    Folder refreshedNotebook = folderMgr.getNotebook(notebook.getId());
    List<BaseRecord> createdEntries = new ArrayList<>(refreshedNotebook.getChildrens());
    commitTransaction();
    assertTrue(createdEntries.stream().allMatch(BaseRecord::isNotebookEntry));
    Collections.sort(createdEntries, BaseRecord.CREATION_DATE_COMPARATOR);

    assertEquals("two new entries were expected ", 2, createdEntries.size());
    BaseRecord createdEntry1 = createdEntries.get(0);

    assertEquals(defaultEntryId, createdEntry1.getId());
    assertEquals(StructuredDocument.DEFAULT_NAME, createdEntry1.getName());

    BaseRecord createdEntry2 = createdEntries.get(1);
    ;

    assertEquals(namedEntryId, createdEntry2.getId());
    assertEquals(testName, createdEntry2.getName());

    RSpaceTestUtils.logout();
  }

  @Test
  public void openNotebookEntryWithDocumentEditor() throws Exception {

    User user = createInitAndLoginAnyUser();

    Long rootFolderId = folderMgr.getRootRecordForUser(user, user).getId();
    Notebook notebook = createNotebookWithNEntries(rootFolderId, "any", 1, user);

    // find first entry
    Long notebookEntryId = folderMgr.getRecordIds(notebook).get(0);
    assertNotNull(notebookEntryId);

    // opening notebook entry for edit should process document editor page
    this.mockMvc
        .perform(
            get(
                    StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/{entryId}?fromNotebook={notebookId}",
                    notebookEntryId + "",
                    notebook.getId() + "")
                .principal(user::getUsername))
        .andExpect(status().is2xxSuccessful())
        .andReturn();

    // ...but opening notebook entry for view should redirect to notebook view (RSPAC-501)
    MvcResult redirectResult =
        this.mockMvc
            .perform(
                get(
                        StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL + "/{entryId}",
                        notebookEntryId + "")
                    .principal(user::getUsername))
            .andExpect(status().is3xxRedirection())
            .andReturn();

    String expectedUrl =
        "redirect:"
            + NotebookEditorController.getNotebookViewUrl(notebook.getId(), notebookEntryId, "");
    assertEquals(expectedUrl, redirectResult.getModelAndView().getViewName());
  }

  @Test
  public void setDescription() throws Exception {
    User u = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u, "any");
    String desc = getRandomAlphabeticString("desc");
    MvcResult result =
        this.mockMvc
            .perform(
                post(StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/ajax/description")
                    .param("recordId", doc.getId() + "")
                    .param("description", desc)
                    .principal(u::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals(desc, baseRecordMgr.get(doc.getId(), u).getDescription());
    assertNull(result.getResolvedException());

    // ensure long description is fine
    String longDesc = StringUtils.repeat("*", EditInfo.DESCRIPTION_LENGTH);
    result =
        this.mockMvc
            .perform(
                post(StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/ajax/description")
                    .param("recordId", doc.getId() + "")
                    .param("description", longDesc)
                    .principal(u::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertEquals(longDesc, baseRecordMgr.get(doc.getId(), u).getDescription());
    assertNull(result.getResolvedException());

    // ensure too long description is rejected
    String tooLongDesc = StringUtils.repeat("*", EditInfo.DESCRIPTION_LENGTH + 1);
    result =
        this.mockMvc
            .perform(
                post(StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/ajax/description")
                    .param("recordId", doc.getId() + "")
                    .param("description", tooLongDesc)
                    .principal(u::getUsername))
            .andExpect(status().is2xxSuccessful())
            .andReturn();
    assertNotNull(result.getResolvedException());
    assertEquals(
        "description too long, should be max 250 chars",
        result.getResolvedException().getMessage());
    assertEquals(
        longDesc, baseRecordMgr.get(doc.getId(), u).getDescription()); // stays with previous value

    // try setting description while not being an owner (RSPAC-851)
    logoutAndLoginAs(piUser);
    MvcResult unauthorisedResult =
        this.mockMvc
            .perform(
                post(StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/ajax/description")
                    .param("recordId", doc.getId() + "")
                    .param("description", desc)
                    .principal(piUser::getUsername))
            .andReturn();

    assertEquals(
        "Sorry, you don't have permission to edit description",
        unauthorisedResult.getResolvedException().getMessage());
  }

  @Test
  public void testLogAutosave_1647() throws Exception {
    int initialRows = getFieldAutosaveLogRowCount();
    User u = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u, "any");
    Field field = doc.getFields().get(0);
    for (int i = 0; i < 5; i++) {
      doAutosaveMVC(field, "text" + i, u);
    }
    assertEquals(initialRows + 5, getFieldAutosaveLogRowCount());
    doAutosaveAndSaveMVC(field, "text-final", u);
    // saving does *NOT* delete autosaved logs
    assertEquals(initialRows + 6, getFieldAutosaveLogRowCount());
  }

  @Test
  public void modificationDateNotChangedAfterCancel_1641() throws Exception {
    User u = createInitAndLoginAnyUser();
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(u, "any");
    Long initialModifiedTime = doc.getModificationDateMillis();
    Field field = doc.getFields().get(0);
    doAutosaveAndCancelMVC(field, "text-final", u);

    doc = recordMgr.get(doc.getId()).asStrucDoc();
    assertEquals(initialModifiedTime, doc.getModificationDateMillis());

    // this should increment modification date
    doAutosaveAndSaveMVC(field, "text-final", u);
    doc = recordMgr.get(doc.getId()).asStrucDoc();
    assertTrue(initialModifiedTime < doc.getModificationDateMillis());
  }

  private int getFieldAutosaveLogRowCount() throws Exception {
    return doInTransaction(
        () -> {
          return ((BigInteger)
                  sessionFactory
                      .getCurrentSession()
                      .createNativeQuery("select count(*) from FieldAutosaveLog")
                      .uniqueResult())
              .intValue();
        });
  }

  @Test
  public void sysadminCannotSignDocWhenOperatingAs() throws Exception {
    // only document owner can sign - see rspac2223
    User docOwner = createInitAndLoginAnyUser();
    StructuredDocument docToSign = createBasicDocumentInRootFolderWithText(docOwner, "some text");
    logoutAndLoginAsSysAdmin();
    // this is sufficient here to cause operate-as mechanism to work
    permissionUtils.doRunAs(new MockHttpSession(), getSysAdminUser(), docOwner);
    MvcResult res1 =
        this.mockMvc
            .perform(
                post(STRUCTURED_DOCUMENT_EDITOR_URL + "/ajax/proceedSigning/")
                    .param("recordId", docToSign.getId() + "")
                    .param("statement", "any")
                    .param("username", docOwner.getUsername())
                    .param("witnesses[]", new String[] {docOwner.getUsername()})
                    .param("password", TESTPASSWD)
                    .principal(docOwner::getUsername))
            .andReturn();

    ErrorList errors = getErrorListFromAjaxReturnObject(res1);
    assertTrue(errors.hasErrorMessages());
    assertTrue(
        errors.getErrorMessages().get(0).contains("Only the  record owner can sign the record"));
  }

  @Test
  public void testGetUpdated() throws Exception {
    logoutAndLoginAs(piUser);
    initUser(piUser);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(piUser, "text1");
    Field field = doc.getFields().get(0);
    User other = createAndSaveUser(getRandomAlphabeticString("other"));
    initUser(other);
    Group group = createGroupForUsersWithDefaultPi(piUser, other);
    shareRecordWithGroup(piUser, group, doc);

    doAutosaveAndSaveMVC(field, "text2", piUser);

    logoutAndLoginAs(other);
    mockPrincipal = new MockPrincipal(other.getUsername());
    // more recently modified on cliet, won't updated from server
    Date newerDate = DateUtils.addMinutes(new Date(), 1);

    MvcResult result = getUpdatedFields(doc, newerDate);
    List<Field> rc = getFromJsonAjaxReturnObject(result, List.class);
    assertTrue("field list wasn't empty but should be", rc.isEmpty());
    assertFalse(result.getResponse().getContentAsString().contains("text2"));
    // simulates an older modification date on client, so client data is stale and needs refreshing
    Date olderDate = DateUtils.addMinutes(new Date(), -5);
    result = getUpdatedFields(doc, olderDate);
    rc = getFromJsonAjaxReturnObject(result, List.class);
    assertFalse("field list was empty but should contain updated fields", rc.isEmpty());
    assertTrue(result.getResponse().getContentAsString().contains("text2"));
  }

  private MvcResult getUpdatedFields(StructuredDocument doc, Date modifiedDate) throws Exception {
    MvcResult result =
        this.mockMvc
            .perform(
                get(StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/ajax/getUpdatedFields")
                    .param("recordId", doc.getId() + "")
                    .param("modificationDate", modifiedDate.getTime() + "")
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    return result;
  }

  @Test
  public void testDelete() throws Exception {
    setUpDocumentGroupForPIUserAndShareRecord();
    Folder root = folderMgr.getRootFolderForUser(piUser);
    StructuredDocument structuredDocument = recordMgr.createBasicDocument(root.getId(), piUser);

    MvcResult result =
        this.mockMvc
            .perform(
                post(StructuredDocumentController.STRUCTURED_DOCUMENT_EDITOR_URL
                        + "/ajax/deleteStructuredDocument/"
                        + structuredDocument.getId())
                    .principal(mockPrincipal))
            .andExpect(status().isOk())
            .andReturn();
    assertTrue(result.getResponse().getContentAsString().contains(root.getId() + ""));
  }
}
