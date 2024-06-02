package com.researchspace.api.v1.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.elninventory.ListOfMaterials;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.units.QuantityInfo;
import com.researchspace.model.units.RSUnitDef;
import java.math.BigDecimal;
import org.junit.Test;

public class ApiListOfMaterialsTest {

  @Test
  public void checkUpdateDBLomFromIncomingApiLom() {

    // set up pre-existing database lom
    ListOfMaterials dbLom = new ListOfMaterials();
    dbLom.setName("name");
    dbLom.setDescription("description");
    InventoryRecord dbMaterialUsed = new SubSample();
    dbMaterialUsed.setId(1L);
    QuantityInfo dbQuantityUsed = QuantityInfo.of(BigDecimal.ONE, RSUnitDef.GRAM);
    dbLom.addMaterial(dbMaterialUsed, dbQuantityUsed);
    assertEquals("1 g", dbLom.getMaterials().get(0).getUsedQuantity().toPlainString());

    // set up incoming api changes request
    ApiListOfMaterials incomingLom = new ApiListOfMaterials();
    incomingLom.setName("newName");
    ApiInventoryRecordInfo incomingMaterial = new ApiSubSampleInfo();
    incomingMaterial.setId(dbMaterialUsed.getId());
    ApiQuantityInfo incomingQuantity = new ApiQuantityInfo(BigDecimal.TEN, RSUnitDef.GRAM);

    // apply incoming changes to db lom
    incomingLom.addMaterialUsage(incomingMaterial, incomingQuantity, false);

    // verify modified db lom
    incomingLom.applyChangesToDatabaseListOfMaterials(dbLom, null, null);
    assertEquals("newName", dbLom.getName());
    assertEquals("description", dbLom.getDescription());
    assertEquals(1, dbLom.getMaterials().size());
    assertEquals("10 g", dbLom.getMaterials().get(0).getUsedQuantity().toPlainString());
  }
}
