package com.researchspace.dao;

import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryEntityField;

public interface InventoryEntityFieldDao extends GenericDao<InventoryEntityField, Long> {

  InventoryRecord getParentInventoryEntityFromFieldId(Long fieldId);
}
