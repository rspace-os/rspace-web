package com.researchspace.service.inventory;

import com.researchspace.model.User;
import java.util.List;

public interface InventoryTagApiManager {
  List<String> getTagsForUser(User user);
}
