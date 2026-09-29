package com.researchspace.api.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.api.v1.model.ApiMaterialUsage;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.BindException;

public class ListOfMaterialsApiControllerTest extends SpringTransactionalTest {

  private @Autowired ListOfMaterialsApiController lomApiController;

  private User testUser;

  @BeforeEach
  public void setUp() {
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(testUser);
    assertTrue(testUser.isContentInitialized());
  }

  @Test
  public void createRetrieveDeleteListOfMaterials() throws BindException {

    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(testUser, "test");
    Field basicDocField = basicDoc.getFields().get(0);

    List<ApiListOfMaterials> foundDocLoms =
        lomApiController.getListOfMaterialsForDocument(basicDoc.getId(), testUser);
    assertThat(foundDocLoms).isEmpty();

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiListOfMaterials createdApiLom =
        createBasicListOfMaterialsForUserAndDocField(
            testUser, basicDocField, List.of(new ApiMaterialUsage(basicSample, null)));

    Long createdLomId = createdApiLom.getId();
    boolean canEdit = lomApiController.canUserEditListOfMaterials(createdLomId, testUser);
    assertTrue(canEdit);

    // loms searched by doc/field id doesn't have elnDocument details
    foundDocLoms = lomApiController.getListOfMaterialsForDocument(basicDoc.getId(), testUser);
    assertThat(foundDocLoms).hasSize(1);
    List<ApiListOfMaterials> foundFieldLoms =
        lomApiController.getListOfMaterialsForField(basicDocField.getId(), testUser);
    assertEquals(foundDocLoms, foundFieldLoms);
    ApiListOfMaterials apiLomFoundWithDocOrFieldSearch = foundFieldLoms.get(0);
    assertEquals(createdApiLom.getGlobalId(), apiLomFoundWithDocOrFieldSearch.getGlobalId());
    assertThat(apiLomFoundWithDocOrFieldSearch.getMaterials()).hasSize(1);
    assertFalse(apiLomFoundWithDocOrFieldSearch.getMaterials().get(0).getRecord().isDeleted());
    assertNull(apiLomFoundWithDocOrFieldSearch.getElnDocument()); // doesn't have eln doc details

    // deleting the item doesn't break lom view (PRT-605)
    sampleApiMgr.markSampleAsDeleted(basicSample.getId(), true, testUser);

    // loms searched by inventory item/lom id do have elnDocument details
    ApiListOfMaterials fullRetrievedLom =
        lomApiController.getListOfMaterialsById(createdLomId, testUser);
    assertEquals(createdApiLom.getGlobalId(), fullRetrievedLom.getGlobalId());
    assertThat(fullRetrievedLom.getMaterials()).hasSize(1);
    assertTrue(fullRetrievedLom.getMaterials().get(0).getRecord().isDeleted());
    assertNotNull(fullRetrievedLom.getElnDocument()); // does have eln doc details
    List<ApiListOfMaterials> foundItemLoms =
        lomApiController.getListOfMaterialsForInventoryItem(basicSample.getGlobalId(), testUser);
    assertThat(foundDocLoms).hasSize(1);
    assertThat(foundDocLoms.get(0).getMaterials()).hasSize(1);
    assertTrue(foundItemLoms.get(0).getMaterials().get(0).getRecord().isDeleted());
    assertEquals(fullRetrievedLom.getElnDocument(), foundItemLoms.get(0).getElnDocument());

    lomApiController.deleteListOfMaterials(createdLomId, testUser);
    foundDocLoms = lomApiController.getListOfMaterialsForDocument(basicDoc.getId(), testUser);
    assertThat(foundDocLoms).isEmpty();
  }
}
