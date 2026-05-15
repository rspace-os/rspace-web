package com.researchspace.dao.hibernate;

import com.researchspace.dao.InstrumentEntityDao;
import com.researchspace.model.inventory.Instrument;
import org.springframework.stereotype.Repository;

@Repository(value = "instrumentDao")
public class InstrumentDaoHibernateImpl extends InventoryDaoHibernate<Instrument, Long>
    implements InstrumentEntityDao<Instrument> {

  private String defaultTemplateOwner;

  public InstrumentDaoHibernateImpl(Class<Instrument> persistentClass) {
    super(persistentClass);
  }

  public InstrumentDaoHibernateImpl() {
    super(Instrument.class);
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
