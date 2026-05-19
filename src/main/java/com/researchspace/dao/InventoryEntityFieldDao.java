package com.researchspace.dao;

import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryEntityField;

public interface InventoryEntityFieldDao extends GenericDao<InventoryEntityField, Long> {

  GlobalIdentifier getInstrumentEntityGlobalIdFromFieldId(Long fieldId);

  InventoryRecord getParentInventoryEntityFromFieldId(Long fieldId);
}
