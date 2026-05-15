package com.researchspace.dao.hibernate;

import com.researchspace.dao.GenericDaoHibernate;
import com.researchspace.dao.InventoryEntityFieldDao;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryEntityField;
import javax.ws.rs.NotFoundException;
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
    InventoryEntityField field = this.get(fieldId);
    if (field == null) {
      throw new NotFoundException("No inventory entity field with id: " + fieldId);
    }
    return field.getInventoryRecord();
  }

  @Override
  public GlobalIdentifier getInstrumentEntityGlobalIdFromFieldId(Long fieldId) {
    InventoryEntityField field =
        sessionFactory
            .getCurrentSession()
            .createQuery("from InventoryEntityField where id=:fieldId", InventoryEntityField.class)
            .setParameter("fieldId", fieldId)
            .getSingleResult();
    return field.getInstrumentEntity().getOid();
  }
}
