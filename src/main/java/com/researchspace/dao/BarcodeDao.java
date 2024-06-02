package com.researchspace.dao;

import com.researchspace.model.inventory.Barcode;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.List;

/** For DAO operations on Inventory Sample. */
public interface BarcodeDao extends GenericDao<Barcode, Long> {

  List<InventoryRecord> findItemsByBarcodeData(String barcodeData);
}
