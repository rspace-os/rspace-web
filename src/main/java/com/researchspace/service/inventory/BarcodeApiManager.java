package com.researchspace.service.inventory;

import com.researchspace.model.inventory.InventoryRecord;
import java.util.List;

/** Handles API actions around Barcodes. */
public interface BarcodeApiManager {

  /** Return all with matching barcode */
  List<InventoryRecord> findItemsByBarcodeData(String barcodeData);
}
