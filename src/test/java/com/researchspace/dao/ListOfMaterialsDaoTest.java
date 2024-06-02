package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.field.Field;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;

public class ListOfMaterialsDaoTest extends SpringTransactionalTest {

  @Test
  public void createReadUpdateDeleteNewSample() {
    int initialCount = listOfMaterialsDao.getAllDistinct().size();

    User user = createInitAndLoginAnyUser();

    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(user, "test");
    Field basicField = basicDoc.getFields().get(0);
    ApiSampleWithFullSubSamples createdSample = createBasicSampleForUser(user);
    Sample basicSample = sampleDao.get(createdSample.getId());
    SubSample basicSubSample = basicSample.getSubSamples().get(0);

    ListOfMaterials lom = new ListOfMaterials();
    lom.setName("test lom");
    lom.addMaterial(basicSample, null);
    lom.addMaterial(basicSubSample, basicSubSample.getQuantity());
    basicField.addListOfMaterials(lom);
    ListOfMaterials savedLom = listOfMaterialsDao.save(lom);
    assertNotNull(savedLom.getId());

    ListOfMaterials retrievedLom = listOfMaterialsDao.get(savedLom.getId());
    assertEquals(savedLom, retrievedLom);
    assertEquals(2, retrievedLom.getMaterials().size());

    savedLom.getMaterials().remove(0);
    savedLom = listOfMaterialsDao.save(savedLom);

    retrievedLom = listOfMaterialsDao.get(savedLom.getId());
    assertEquals(1, retrievedLom.getMaterials().size());

    int finalCount = listOfMaterialsDao.getAllDistinct().size();
    assertEquals(initialCount + 1, finalCount);
  }
}
