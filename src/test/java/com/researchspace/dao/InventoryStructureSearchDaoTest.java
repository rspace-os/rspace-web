package com.researchspace.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.field.InventoryAttachmentField;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * DB-backed checks for the queries Inventory structure search uses to go from chemistry service
 * hits to the Inventory records carrying a matching Gallery chemistry file. The manager logic on
 * top is covered by {@code InventoryStructureSearchManagerImplTest}.
 */
class InventoryStructureSearchDaoTest extends SpringTransactionalTest {

  private @Autowired RSChemElementDao rsChemElementDao;
  private @Autowired InventoryFileDao inventoryFileDao;

  private User user;

  @BeforeEach
  void createUser() {
    user = createInitAndLoginAnyUser();
  }

  private EcatChemistryFile chemistryFileInGallery() throws IOException {
    return addChemistryFileToGallery("Aminoglutethimide.mol", user);
  }

  private Long chemIdOf(EcatChemistryFile galleryFile) {
    return rsChemElementDao.getChemElementFromChemistryGalleryFile(galleryFile.getId()).getId();
  }

  private InventoryFile attach(EcatChemistryFile galleryFile, String globalId) {
    return inventoryFileApiMgr.attachGalleryFileToInventoryRecord(
        new GlobalIdentifier(globalId), galleryFile.getOid(), user);
  }

  @Test
  void resolvesChemHitToGalleryFileAndOwningSample() throws IOException {
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    EcatChemistryFile galleryFile = chemistryFileInGallery();
    attach(galleryFile, sample.getGlobalId());

    List<Long> galleryFileIds =
        rsChemElementDao.getGalleryChemFileIds(List.of(chemIdOf(galleryFile)));
    assertThat(galleryFileIds).containsExactly(galleryFile.getId());

    List<InventoryFile> files = inventoryFileDao.findByMediaFileIds(galleryFileIds);
    assertThat(files).hasSize(1);
    assertEquals(sample.getGlobalId(), files.get(0).getInventoryRecord().getGlobalIdentifier());
  }

  @Test
  void skipsChemElementsThatAreNotGalleryFiles() {
    // document chemistry is linked to a text field, not to a Gallery file
    RSChemElement inDocument =
        rsChemElementDao.save(RSChemElement.builder().parentId(1L).chemElements("C").build());

    assertThat(rsChemElementDao.getGalleryChemFileIds(List.of(inDocument.getId(), -1L))).isEmpty();
  }

  @Test
  void resolvesAttachmentFieldFileToOwningSample() throws IOException {
    ApiSampleWithFullSubSamples sample = createComplexSampleForUser(user);
    ApiInventoryEntityField attachmentField = sample.getFields().get(6);
    assertEquals(ApiFieldType.ATTACHMENT, attachmentField.getType());
    EcatChemistryFile galleryFile = chemistryFileInGallery();
    attach(galleryFile, attachmentField.getGlobalId());

    List<InventoryAttachmentField> fields =
        inventoryFileDao.findAttachmentFieldsByMediaFileIds(List.of(galleryFile.getId()));

    assertThat(fields).hasSize(1);
    assertEquals(sample.getGlobalId(), fields.get(0).getInventoryRecord().getGlobalIdentifier());
  }

  @Test
  void excludesDeletedAttachments() throws IOException {
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(user);
    EcatChemistryFile galleryFile = chemistryFileInGallery();
    InventoryFile attachment = attach(galleryFile, sample.getGlobalId());

    inventoryFileApiMgr.markInventoryFileAsDeleted(attachment.getId(), user);

    assertThat(inventoryFileDao.findByMediaFileIds(List.of(galleryFile.getId()))).isEmpty();
  }

  @Test
  void findsAttachmentsOfSeveralGalleryFilesInOneQuery() throws IOException {
    ApiSampleWithFullSubSamples first = createBasicSampleForUser(user, "first");
    ApiSampleWithFullSubSamples second = createBasicSampleForUser(user, "second");
    EcatChemistryFile firstFile = chemistryFileInGallery();
    EcatChemistryFile secondFile = chemistryFileInGallery();
    attach(firstFile, first.getGlobalId());
    attach(secondFile, second.getGlobalId());

    List<InventoryFile> files =
        inventoryFileDao.findByMediaFileIds(List.of(firstFile.getId(), secondFile.getId()));

    assertThat(files)
        .extracting(file -> file.getInventoryRecord().getGlobalIdentifier())
        .containsExactlyInAnyOrder(first.getGlobalId(), second.getGlobalId());
  }
}
