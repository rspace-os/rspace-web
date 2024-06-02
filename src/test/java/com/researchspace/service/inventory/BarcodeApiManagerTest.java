package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.model.ApiBarcode;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.testutils.SpringTransactionalTest;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class BarcodeApiManagerTest extends SpringTransactionalTest {

  @Autowired private BarcodeApiManager barcodeApiMgr;

  private User testUser;

  @Before
  public void setUp() {
    testUser = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithExampleContent(testUser);
  }

  @Test
  public void searchByBarcodeData() {

    // create two containers and a sample
    ApiContainer apiContainer = createBasicContainerForUser(testUser, "c1");
    ApiContainer apiSubContainer = createBasicContainerForUser(testUser, "c2");
    ApiSampleWithFullSubSamples apiSample = createBasicSampleForUser(testUser, "s1", "ss1", null);
    ApiSubSample apiSubSample = apiSample.getSubSamples().get(0);

    // add numeric barcode to subsample and subcontainer
    final String NUMERIC_BARCODE = "1234321";
    ApiSubSample subSampleUpdate = new ApiSubSample();
    subSampleUpdate.setId(apiSubSample.getId());
    subSampleUpdate.getBarcodes().add(new ApiBarcode(NUMERIC_BARCODE));
    subSampleUpdate.getBarcodes().get(0).setNewBarcodeRequest(true);
    apiSubSample = subSampleApiMgr.updateApiSubSample(subSampleUpdate, testUser);
    ApiContainer containerUpdate = new ApiContainer();
    containerUpdate.setId(apiSubContainer.getId());
    containerUpdate.getBarcodes().add(new ApiBarcode(NUMERIC_BARCODE));
    containerUpdate.getBarcodes().get(0).setNewBarcodeRequest(true);
    apiContainer = containerApiMgr.updateApiContainer(containerUpdate, testUser);

    // confirm barcodes added
    assertEquals(1, apiSubSample.getBarcodes().size());
    ;
    assertEquals(1, apiContainer.getBarcodes().size());

    // update container's barcodes: add url barcode, remove numeric one
    final String URL_BARCODE = "https://test.two/b";
    containerUpdate = new ApiContainer();
    containerUpdate.setId(apiContainer.getId());
    containerUpdate.getBarcodes().add(new ApiBarcode(URL_BARCODE));
    containerUpdate.getBarcodes().get(0).setNewBarcodeRequest(true);
    containerUpdate.getBarcodes().add(apiContainer.getBarcodes().get(0));
    containerUpdate.getBarcodes().get(1).setDeleteBarcodeRequest(true);
    apiContainer = containerApiMgr.updateApiContainer(containerUpdate, testUser);
    assertEquals(1, apiContainer.getBarcodes().size());

    // check items found for barcode search
    List<InventoryRecord> itemsByBarcodeData =
        barcodeApiMgr.findItemsByBarcodeData(NUMERIC_BARCODE);
    assertEquals(1, itemsByBarcodeData.size());
    assertEquals(apiSubSample.getGlobalId(), itemsByBarcodeData.get(0).getOid().getIdString());

    itemsByBarcodeData = barcodeApiMgr.findItemsByBarcodeData(URL_BARCODE);
    assertEquals(1, itemsByBarcodeData.size());
    assertEquals(apiContainer.getGlobalId(), itemsByBarcodeData.get(0).getOid().getIdString());
  }
}
