package com.researchspace.webapp.controller;

import static com.researchspace.core.testutil.CoreTestUtils.getRandomName;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.researchspace.dao.EcatImageAnnotationDao;
import com.researchspace.dao.EcatImageDao;
import com.researchspace.dao.FieldDao;
import com.researchspace.model.EcatImage;
import com.researchspace.model.EcatImageAnnotation;
import com.researchspace.model.FieldAttachment;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AnnotatedImageRelink;
import com.researchspace.service.AnnotatedImageRelink.LinkState;
import com.researchspace.service.AnnotatedImageRelink.Outcome;
import com.researchspace.service.DevOpsManager;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.authz.AuthorizationException;
import org.apache.shiro.subject.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class SysAdminDevOpsControllerMVCIT extends MVCTestBase {

  private static final String ENDPOINT = "/system/devops/ajax/relinkAnnotatedImages";

  private @Autowired DevOpsManager devOpsManager;
  private @Autowired FieldDao fieldDao;
  private @Autowired EcatImageDao ecatImageDao;
  private @Autowired EcatImageAnnotationDao imageAnnotationDao;
  private @Autowired PlatformTransactionManager transactionManager;

  private User owner;
  private User other;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    owner = createAndSaveUser(getRandomName(10));
    other = createAndSaveUser(getRandomName(10));
    initUsers(owner, other);
  }

  @Test
  public void reportThenRepairMissingAndDeletedLinks() throws Exception {
    EcatImage missingImage = newImage(owner);
    Field missing = fieldWithAnnotatedImage(missingImage);
    EcatImage deletedImage = newImage(owner);
    Field deleted = fieldWithAnnotatedImage(deletedImage);
    addLink(deleted, deletedImage, true);
    User sysadmin = logoutAndLoginAsSysAdmin();

    assertThat(devOpsManager.findFieldIdsWithAnnotatedImages())
        .contains(missing.getId(), deleted.getId());
    AnnotatedImageRelink missingReport =
        single(devOpsManager.reportAnnotatedImagesInField(missing.getId()), missing);
    assertEquals(LinkState.MISSING_ROW, missingReport.linkState());
    assertEquals(Outcome.TO_REPAIR, missingReport.outcome());
    assertEquals(owner.getUsername(), missingReport.ownerUsername());
    assertEquals(missingImage.getGlobalIdentifier(), missingReport.imageGlobalId());
    assertEquals(
        missing.getStructuredDocument().getGlobalIdentifier(), missingReport.documentGlobalId());
    AnnotatedImageRelink deletedReport =
        single(devOpsManager.reportAnnotatedImagesInField(deleted.getId()), deleted);
    assertEquals(LinkState.DELETED_ROW, deletedReport.linkState());
    assertEquals(Outcome.TO_REPAIR, deletedReport.outcome());
    assertThat(links(missing, missingImage)).isEmpty();
    assertThat(links(deleted, deletedImage)).singleElement().matches(FieldAttachment::isDeleted);

    assertEquals(
        Outcome.REPAIRED,
        single(devOpsManager.relinkAnnotatedImagesInField(missing.getId()), missing).outcome());
    assertEquals(
        Outcome.REPAIRED,
        single(devOpsManager.relinkAnnotatedImagesInField(deleted.getId()), deleted).outcome());
    assertThat(links(missing, missingImage)).singleElement().matches(link -> !link.isDeleted());
    assertThat(links(deleted, deletedImage)).singleElement().matches(link -> !link.isDeleted());

    // idempotent: nothing left to report or repair
    assertThat(devOpsManager.relinkAnnotatedImagesInField(missing.getId())).isEmpty();
    assertThat(devOpsManager.reportAnnotatedImagesInField(deleted.getId())).isEmpty();
    // the owner's permissions were checked without replacing the sysadmin's subject
    assertEquals(sysadmin.getUsername(), SecurityUtils.getSubject().getPrincipal());
  }

  @Test
  public void reportDoesNotWriteTheDocument() throws Exception {
    Field field = fieldWithAnnotatedImage(newImage(owner));
    Long docId = field.getStructuredDocument().getId();
    // a delta left by an earlier save; loading the document clears it in memory
    sql("update StructuredDocument set deltaString = 'seeded' where id = ?1", docId);
    List<?> before = documentState(docId);
    logoutAndLoginAsSysAdmin();

    assertEquals(
        Outcome.TO_REPAIR,
        single(devOpsManager.reportAnnotatedImagesInField(field.getId()), field).outcome());

    assertEquals(before, documentState(docId));
    assertThat(before.get(0)).hasToString("seeded");
  }

  @Test
  public void concurrentRepairsRestoreTheLinkOnce() throws Exception {
    EcatImage image = newImage(owner);
    Field field = fieldWithAnnotatedImage(image);
    logoutAndLoginAsSysAdmin();
    Subject sysadmin = SecurityUtils.getSubject();
    CountDownLatch firstRepaired = new CountDownLatch(1);
    CountDownLatch secondStarting = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      // the first repair stays uncommitted until the second has started its own
      Future<List<AnnotatedImageRelink>> first =
          executor.submit(
              sysadmin.associateWith(
                  () ->
                      new TransactionTemplate(transactionManager)
                          .execute(
                              status -> {
                                List<AnnotatedImageRelink> repaired =
                                    devOpsManager.relinkAnnotatedImagesInField(field.getId());
                                firstRepaired.countDown();
                                await(secondStarting);
                                // time for the second repair to reach the field lock
                                pause(Duration.ofSeconds(2));
                                return repaired;
                              })));
      Future<List<AnnotatedImageRelink>> second =
          executor.submit(
              sysadmin.associateWith(
                  () -> {
                    await(firstRepaired);
                    secondStarting.countDown();
                    return devOpsManager.relinkAnnotatedImagesInField(field.getId());
                  }));

      assertEquals(Outcome.REPAIRED, single(first.get(60, TimeUnit.SECONDS), field).outcome());
      // it waited for the first to commit, then found the link already restored
      assertThat(second.get(60, TimeUnit.SECONDS)).isEmpty();
    } finally {
      executor.shutdownNow();
    }
    assertThat(links(field, image)).singleElement().matches(link -> !link.isDeleted());
  }

  @Test
  public void onlyAnnotatedImageElementsAreConsidered() throws Exception {
    EcatImage annotatedImage = newImage(owner);
    EcatImage plainImage = newImage(owner);
    Field field = newField();
    String annotated = annotatedImageMarkup(field, annotatedImage);
    String src = annotated.replaceAll("(?s).*src=\"([^\"]+)\".*", "$1");
    // a plain image whose src matches the annotated one
    String plain =
        "<img id=\""
            + field.getId()
            + "-"
            + plainImage.getId()
            + "\" class=\"imageDropped\" src=\""
            + src
            + "\" />";
    setContent(field, plain + annotated);
    logoutAndLoginAsSysAdmin();

    AnnotatedImageRelink result =
        single(devOpsManager.reportAnnotatedImagesInField(field.getId()), field);

    assertEquals(annotatedImage.getGlobalIdentifier(), result.imageGlobalId());
  }

  @Test
  public void ignoresMalformedElementIds() throws Exception {
    EcatImage image = newImage(owner);
    Field field = newField();
    String markup = annotatedImageMarkup(field, image);
    String wellFormedId = "id=\"" + field.getId() + "-" + image.getId() + "\"";
    assertThat(markup).contains(wellFormedId);
    setContent(
        field,
        markup.replace(wellFormedId, "id=\"oops-" + image.getId() + "\"")
            + markup.replace(wellFormedId, "id=\"" + field.getId() + "-" + image.getId() + "-\""));
    logoutAndLoginAsSysAdmin();

    assertThat(devOpsManager.reportAnnotatedImagesInField(field.getId())).isEmpty();
    assertThat(devOpsManager.relinkAnnotatedImagesInField(field.getId())).isEmpty();
    assertThat(links(field, image)).isEmpty();
  }

  @Test
  public void skipsUnreadableImageAndLeavesItsDeletedRow() throws Exception {
    EcatImage othersImage = newImage(other);
    Field field = fieldWithAnnotatedImage(othersImage);
    addLink(field, othersImage, true);
    logoutAndLoginAsSysAdmin();

    AnnotatedImageRelink result =
        single(devOpsManager.relinkAnnotatedImagesInField(field.getId()), field);

    assertEquals(Outcome.SKIPPED, result.outcome());
    assertEquals("document owner cannot read the Gallery image", result.skipReason());
    assertThat(links(field, othersImage)).singleElement().matches(FieldAttachment::isDeleted);
  }

  @Test
  public void repairsImageReadableThroughAnActiveLinkInAnotherField() throws Exception {
    EcatImage othersImage = newImage(other);
    Field field = fieldWithAnnotatedImage(othersImage);
    addLink(newField(), othersImage, false);
    logoutAndLoginAsSysAdmin();

    assertEquals(
        Outcome.REPAIRED,
        single(devOpsManager.relinkAnnotatedImagesInField(field.getId()), field).outcome());
    assertThat(links(field, othersImage)).singleElement().matches(link -> !link.isDeleted());
  }

  @Test
  public void skipsDeletedImagesAndTemplates() throws Exception {
    EcatImage deletedImage = newImage(owner);
    Field withDeletedImage = fieldWithAnnotatedImage(deletedImage);
    sql("update BaseRecord set deleted = 1 where id = ?1", deletedImage.getId());
    EcatImage templateImage = newImage(owner);
    Field inTemplate = fieldWithAnnotatedImage(templateImage);
    sql(
        "update BaseRecord set type = 'NORMAL:TEMPLATE' where id = ?1",
        inTemplate.getStructuredDocument().getId());
    logoutAndLoginAsSysAdmin();

    AnnotatedImageRelink deleted =
        single(
            devOpsManager.relinkAnnotatedImagesInField(withDeletedImage.getId()), withDeletedImage);
    AnnotatedImageRelink template =
        single(devOpsManager.relinkAnnotatedImagesInField(inTemplate.getId()), inTemplate);

    assertEquals(Outcome.SKIPPED, deleted.outcome());
    assertEquals("Gallery image is deleted", deleted.skipReason());
    assertEquals(Outcome.SKIPPED, template.outcome());
    assertEquals("template document", template.skipReason());
    assertThat(links(withDeletedImage, deletedImage)).isEmpty();
    assertThat(links(inTemplate, templateImage)).isEmpty();
  }

  @Test
  public void deletedTemporaryAndAutosaveFieldsAreNotCandidates() throws Exception {
    Field current = fieldWithAnnotatedImage(newImage(owner));
    Field inDeletedDoc = fieldWithAnnotatedImage(newImage(owner));
    sql(
        "update BaseRecord set deleted = 1 where id = ?1",
        inDeletedDoc.getStructuredDocument().getId());
    Field inTemporaryDoc = fieldWithAnnotatedImage(newImage(owner));
    sql(
        "update StructuredDocument set temporaryDoc = 1 where id = ?1",
        inTemporaryDoc.getStructuredDocument().getId());
    Field autosaved = newField();
    doAutosaveMVC(autosaved, annotatedImageMarkup(autosaved, newImage(owner)), owner);
    Long autosaveFieldId =
        doInTransaction(() -> fieldDao.get(autosaved.getId()).getTempField().getId());
    logoutAndLoginAsSysAdmin();

    assertThat(devOpsManager.findFieldIdsWithAnnotatedImages())
        .contains(current.getId())
        .doesNotContain(
            inDeletedDoc.getId(), inTemporaryDoc.getId(), autosaveFieldId, autosaved.getId());
    // a field that stopped being a candidate after the scan is not checked
    assertThat(devOpsManager.relinkAnnotatedImagesInField(inDeletedDoc.getId())).isEmpty();
    assertThat(devOpsManager.relinkAnnotatedImagesInField(autosaveFieldId)).isEmpty();
  }

  @Test
  public void endpointReportsAndRepairsForSysadminAndRefusesOthers() throws Exception {
    EcatImage image = newImage(owner);
    Field field = fieldWithAnnotatedImage(image);
    User sysadmin = logoutAndLoginAsSysAdmin();
    String line =
        field.getStructuredDocument().getGlobalIdentifier()
            + "\t"
            + owner.getUsername()
            + "\t"
            + field.getId()
            + "\t"
            + image.getGlobalIdentifier()
            + "\tmissing row";

    String report = callEndpoint(sysadmin, false);
    assertThat(report).contains(line + "\n").contains("?update=true");
    assertThat(links(field, image)).isEmpty();

    String repair = callEndpoint(sysadmin, true);
    assertThat(repair).contains(line + ", repaired\n").contains("errors: 0");
    assertThat(links(field, image)).singleElement().matches(link -> !link.isDeleted());
    assertThat(callEndpoint(sysadmin, false)).doesNotContain(line);

    logoutAndLoginAs(owner);
    MvcResult refused = mockMvc.perform(get(ENDPOINT).principal(owner::getUsername)).andReturn();
    assertInstanceOf(AuthorizationException.class, refused.getResolvedException());
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(60, TimeUnit.SECONDS), "timed out waiting for the other repair");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void pause(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private String callEndpoint(User sysadmin, boolean update) throws Exception {
    return mockMvc
        .perform(
            get(ENDPOINT)
                .param("update", Boolean.toString(update))
                .principal(sysadmin::getUsername))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private EcatImage newImage(User imageOwner) throws Exception {
    logoutAndLoginAs(imageOwner);
    return addImageToGallery(imageOwner);
  }

  private Field newField() {
    logoutAndLoginAs(owner);
    StructuredDocument doc = createBasicDocumentInRootFolderWithText(owner, "");
    return doc.getFields().get(0);
  }

  /*
   * Recreates the pre-RSDEV-1419 damage: the field shows an annotated image, written straight to
   * the database so no FieldAttachment is created for the Gallery image.
   */
  private Field fieldWithAnnotatedImage(EcatImage image) throws Exception {
    Field field = newField();
    setContent(field, annotatedImageMarkup(field, image));
    return field;
  }

  private String annotatedImageMarkup(Field field, EcatImage image) throws Exception {
    return doInTransaction(
        () -> {
          EcatImageAnnotation annotation = new EcatImageAnnotation();
          annotation.setParentId(field.getId());
          annotation.setRecord(fieldDao.get(field.getId()).getStructuredDocument());
          annotation.setImageId(image.getId());
          imageAnnotationDao.save(annotation);
          return richTextUpdater.generateAnnotatedImageElement(
              annotation, field.getId().toString());
        });
  }

  private void setContent(Field field, String html) throws Exception {
    doInTransaction(
        () -> {
          Field loaded = fieldDao.get(field.getId());
          loaded.setFieldData(html);
          fieldDao.save(loaded);
        });
  }

  private void addLink(Field field, EcatImage image, boolean deleted) throws Exception {
    doInTransaction(
        () -> {
          Field loaded = fieldDao.get(field.getId());
          EcatImage loadedImage = ecatImageDao.get(image.getId());
          loaded.addMediaFileLink(loadedImage);
          loaded.setMediaFileLinkDeleted(loadedImage, deleted);
          fieldDao.save(loaded);
        });
  }

  private void sql(String update, Long id) throws Exception {
    doInTransaction(
        () -> {
          sessionFactory
              .getCurrentSession()
              .createNativeQuery(update)
              .setParameter(1, id)
              .executeUpdate();
        });
  }

  /* Read with SQL, so loading the document can't change what is read. */
  private List<?> documentState(Long docId) throws Exception {
    return doInTransaction(
        () ->
            Arrays.asList(
                sessionFactory
                    .getCurrentSession()
                    .createNativeQuery(
                        "select sd.deltaString, sd.version, br.modificationDate"
                            + " from StructuredDocument sd join BaseRecord br on br.id = sd.id"
                            + " where sd.id = ?1",
                        Object[].class)
                    .setParameter(1, docId)
                    .getSingleResult()));
  }

  private AnnotatedImageRelink single(List<AnnotatedImageRelink> results, Field field) {
    assertThat(results).hasSize(1);
    AnnotatedImageRelink result = results.get(0);
    assertEquals(field.getId(), result.fieldId());
    if (result.outcome() != Outcome.SKIPPED) {
      assertNull(result.skipReason());
    }
    return result;
  }

  private List<FieldAttachment> links(Field field, EcatImage image) throws Exception {
    return doInTransaction(
        () ->
            fieldDao.getFieldAttachments(field.getId()).stream()
                .filter(link -> link.getMediaFile().getId().equals(image.getId()))
                .toList());
  }
}
