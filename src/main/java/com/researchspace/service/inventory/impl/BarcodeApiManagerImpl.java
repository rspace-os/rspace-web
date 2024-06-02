package com.researchspace.service.inventory.impl;

import com.researchspace.dao.BarcodeDao;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.inventory.BarcodeApiManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("barcodeApiManager")
public class BarcodeApiManagerImpl implements BarcodeApiManager {

  @Autowired private BarcodeDao barcodeDao;

  @Override
  public List<InventoryRecord> findItemsByBarcodeData(String barcodeData) {
    return barcodeDao.findItemsByBarcodeData(barcodeData);
  }
}
