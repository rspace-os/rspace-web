package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.InventoryEntityFieldDao;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryEntityField;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryEntityFieldDaoHibernateImpl
    extends GenericDaoHibernate<InventoryEntityField, Long> implements InventoryEntityFieldDao {

  public InventoryEntityFieldDaoHibernateImpl(Class<InventoryEntityField> persistentClass) {
    super(persistentClass);
  }

  public InventoryEntityFieldDaoHibernateImpl() {
    super(InventoryEntityField.class);
  }

  public InventoryRecord getParentInventoryEntityFromFieldId(Long fieldId) {
    return this.get(fieldId).getInventoryRecord();
  }
}
