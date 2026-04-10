package com.researchspace.dao.hibernate;

import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.model.inventory.InstrumentTemplate;
import org.springframework.stereotype.Repository;

@Repository(value = "instrumentTemplateDao")
public class InstrumentTemplateDaoHibernateImpl
    extends InventoryDaoHibernate<InstrumentTemplate, Long> implements InstrumentTemplateDao {

  private String defaultTemplateOwner;

  public InstrumentTemplateDaoHibernateImpl(Class<InstrumentTemplate> persistentClass) {
    super(persistentClass);
  }

  public InstrumentTemplateDaoHibernateImpl() {
    super(InstrumentTemplate.class);
  }

  /*
   * ============
   *  for tests
   * ============
   */
  @Override
  public void resetDefaultTemplateOwner() {
    defaultTemplateOwner = null;
  }
}
