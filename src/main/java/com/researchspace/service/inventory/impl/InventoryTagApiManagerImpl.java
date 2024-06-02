package com.researchspace.service.inventory.impl;

import com.researchspace.dao.InventoryTagDao;
import com.researchspace.model.User;
import com.researchspace.service.inventory.InventoryTagApiManager;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class InventoryTagApiManagerImpl implements InventoryTagApiManager {
  @Autowired private InventoryTagDao inventoryTagsDao;

  public List<String> getTagsForUser(User user) {
    return inventoryTagsDao.getTagsForUser(user);
  }
}
