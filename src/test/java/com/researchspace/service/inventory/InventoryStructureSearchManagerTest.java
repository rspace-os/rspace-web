package com.researchspace.service.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.dao.RSChemElementDao;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.testutils.RSpaceTestUtils;
import com.researchspace.testutils.SpringTransactionalTest;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Structure hits after Gallery replacement and record duplication, through both the Inventory
 * search and the Workspace listing. The chem element ids stand in for the chemistry service.
 */
class InventoryStructureSearchManagerTest extends SpringTransactionalTest {

  private @Autowired InventoryStructureSearchManager structureSearchManager;
  private @Autowired RSChemElementDao rsChemElementDao;

  private User user;
  private ApiSampleWithFullSubSamples sample;
  private EcatChemistryFile galleryFile;
  private List<Long> chemIds;

  @BeforeEach
  void attachGalleryChemistryToASampleAndItsField() throws IOException {
    user = createInitAndLoginAnyUser();
    sample = createComplexSampleForUser(user);
    galleryFile = addChemistryFileToGallery("Aminoglutethimide.mol", user);
    attach(sample.getGlobalId());
    attach(sample.getFields().get(6).getGlobalId());
    chemIds =
        List.of(
            rsChemElementDao.getChemElementFromChemistryGalleryFile(galleryFile.getId()).getId());
  }

  private void attach(String globalId) {
    inventoryFileApiMgr.attachGalleryFileToInventoryRecord(
        new GlobalIdentifier(globalId), galleryFile.getOid(), user);
  }

  private List<String> inventorySearchHits() {
    return structureSearchManager
        .searchByStructure(
            chemIds,
            null,
            null,
            null,
            null,
            null,
            PaginationCriteria.createDefaultForClass(InventoryRecord.class),
            user)
        .getRecords()
        .stream()
        .map(ApiInventoryRecordInfo::getGlobalId)
        .toList();
  }

  private List<String> workspaceHits() {
    return structureSearchManager.findReadableStructureHits(chemIds, user).stream()
        .map(InventoryStructureHit::globalId)
        .toList();
  }

  @Test
  void replacedGalleryFileNoLongerFindsAttachmentsOfTheOldVersion() throws IOException {
    try (InputStream newVersion =
        RSpaceTestUtils.getInputStreamOnFromTestResourcesFolder("Amfetamine.mol")) {
      mediaMgr.updateMediaFile(galleryFile.getId(), newVersion, "Amfetamine.mol", user, null);
    }
    sessionFactory.getCurrentSession().flush();

    assertThat(inventorySearchHits()).isEmpty();
    assertThat(workspaceHits()).isEmpty();
  }

  @Test
  void duplicatedSampleIsFound() {
    ApiSampleWithFullSubSamples copy = sampleApiMgr.duplicate(sample.getId(), user);
    sessionFactory.getCurrentSession().flush();

    assertThat(inventorySearchHits())
        .containsExactlyInAnyOrder(sample.getGlobalId(), copy.getGlobalId());
    assertThat(workspaceHits()).containsExactlyInAnyOrder(sample.getGlobalId(), copy.getGlobalId());
  }
}
