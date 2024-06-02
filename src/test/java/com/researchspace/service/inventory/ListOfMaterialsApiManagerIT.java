package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.researchspace.api.v1.model.ApiListOfMaterials;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedRecord;
import com.researchspace.model.field.Field;
import com.researchspace.model.record.StructuredDocument;
import com.researchspace.service.AuditManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class ListOfMaterialsApiManagerIT extends RealTransactionSpringTestBase {

  private @Autowired ListOfMaterialsApiManager lomManager;

  private @Autowired AuditManager auditManager;

  @Test
  public void getDocumentAndLoMVersions() throws Exception {

    User testUser = createInitAndLoginAnyUser();

    StructuredDocument basicDoc = createBasicDocumentInRootFolderWithText(testUser, "test");
    String basicDocInitialName = basicDoc.getName();
    Field basicField = basicDoc.getFields().get(0);

    ApiSampleWithFullSubSamples basicSample = createBasicSampleForUser(testUser);
    ApiSubSample basicSubSample = basicSample.getSubSamples().get(0);

    // add new lom with subsample usage
    ApiListOfMaterials newLom = new ApiListOfMaterials();
    newLom.setName("newLom");
    newLom.setElnFieldId(basicField.getId());
    newLom.addMaterialUsage(
        basicSubSample, basicSubSample.getQuantity(), false); // add subsample usage
    ApiListOfMaterials createdLom = lomManager.createNewListOfMaterials(newLom, testUser);
    assertNotNull(createdLom.getId());
    assertEquals(1, createdLom.getMaterials().size());
    assertEquals("mySubSample", createdLom.getMaterials().get(0).getRecord().getName());

    // update doc field name (to add doc revision)
    basicDoc.setName("doc with lom");
    StructuredDocument updatedDoc = recordMgr.save(basicDoc, testUser).asStrucDoc();

    // update lom
    ApiListOfMaterials lomUpdate = new ApiListOfMaterials();
    lomUpdate.setId(createdLom.getId());
    lomUpdate.setName("updatedLom");
    lomUpdate.addMaterialUsage(basicSample, null, false); // switch to sample usage
    lomManager.updateListOfMaterials(lomUpdate, testUser);

    // delete lom
    lomManager.deleteListOfMaterials(createdLom.getId(), testUser);

    // update doc field name again
    updatedDoc.setName("doc without lom");
    updatedDoc = recordMgr.save(updatedDoc, testUser).asStrucDoc();

    // check doc history & retrieve loms at each revision
    List<AuditedRecord> docHistory = auditManager.getHistory(updatedDoc, null);
    assertEquals(6, docHistory.size());

    AuditedRecord docRevision1 = docHistory.get(0);
    assertEquals(
        "RENAME,FIELD_CHG-Data", docRevision1.getRecordAsDocument().getDelta().getDeltaString());
    assertEquals(basicDocInitialName, docRevision1.getRecordAsDocument().getName());
    ApiListOfMaterials lomAtRev1 =
        lomManager.getListOfMaterialsRevision(
            createdLom.getId(), docRevision1.getRevision().longValue());
    assertNull(lomAtRev1);

    AuditedRecord docRevision2 = docHistory.get(1);
    assertEquals(
        "LIST_OF_MATERIALS_CHG-Data",
        docRevision2.getRecordAsDocument().getDelta().getDeltaString());
    assertEquals(basicDocInitialName, docRevision2.getRecordAsDocument().getName());
    ApiListOfMaterials lomAtRev2 =
        lomManager.getListOfMaterialsRevision(
            createdLom.getId(), docRevision2.getRevision().longValue());
    assertNotNull(lomAtRev2);
    assertEquals("newLom", lomAtRev2.getName());
    assertEquals(1, lomAtRev2.getMaterials().size());
    assertEquals("mySubSample", lomAtRev2.getMaterials().get(0).getRecord().getName());

    AuditedRecord docRevision3 = docHistory.get(2);
    assertEquals(
        "RENAME,FIELD_CHG-Data", docRevision3.getRecordAsDocument().getDelta().getDeltaString());
    assertEquals("doc with lom", docRevision3.getRecordAsDocument().getName());
    ApiListOfMaterials lomAtRev3 =
        lomManager.getListOfMaterialsRevision(
            createdLom.getId(), docRevision3.getRevision().longValue());
    assertNotNull(lomAtRev3);
    assertEquals("newLom", lomAtRev3.getName());

    AuditedRecord docRevision4 = docHistory.get(3);
    assertEquals(
        "LIST_OF_MATERIALS_CHG-Data",
        docRevision4.getRecordAsDocument().getDelta().getDeltaString());
    ApiListOfMaterials lomAtRev4 =
        lomManager.getListOfMaterialsRevision(
            createdLom.getId(), docRevision4.getRevision().longValue());
    assertNotNull(lomAtRev4);
    assertEquals("updatedLom", lomAtRev4.getName());
    assertEquals(1, lomAtRev4.getMaterials().size());
    assertEquals("mySample", lomAtRev4.getMaterials().get(0).getRecord().getName());

    AuditedRecord docRevision5 = docHistory.get(4);
    assertEquals(
        "LIST_OF_MATERIALS_CHG-Data",
        docRevision5.getRecordAsDocument().getDelta().getDeltaString());
    ApiListOfMaterials lomAtRev5 =
        lomManager.getListOfMaterialsRevision(
            createdLom.getId(), docRevision5.getRevision().longValue());
    assertNull(lomAtRev5);

    AuditedRecord docRevision6 = docHistory.get(5);
    assertEquals(
        "RENAME,FIELD_CHG-Data", docRevision6.getRecordAsDocument().getDelta().getDeltaString());
    assertEquals("doc without lom", docRevision6.getRecordAsDocument().getName());
    ApiListOfMaterials lomAtRev6 =
        lomManager.getListOfMaterialsRevision(
            createdLom.getId(), docRevision6.getRevision().longValue());
    assertNull(lomAtRev6);
  }
}
