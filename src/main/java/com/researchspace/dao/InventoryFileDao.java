package com.researchspace.dao;

import com.researchspace.model.inventory.InventoryFile;

/** For DAO operations on Inventory File. */
public interface InventoryFileDao extends GenericDao<InventoryFile, Long> {

  InventoryFile getWithInitializedFields(Long id);
}
