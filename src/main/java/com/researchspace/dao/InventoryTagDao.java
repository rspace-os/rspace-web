package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.inventory.InventoryRecord;
import java.util.List;

public interface InventoryTagDao extends GenericDao<InventoryRecord, Long> {
  List<String> getTagsForUser(User user);
}
