package com.researchspace.dao.hibernate;

import com.researchspace.dao.InstrumentEntityDao;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import org.springframework.stereotype.Repository;

// TODO[nik]: implement this for current RSDEV-1062
@Repository
public class InstrumentDaoHibernateImpl extends InventoryDaoHibernate<InstrumentEntity, Long>
    implements InstrumentEntityDao {

  private static final String PARENT_TEMPLATE_ID = "parentTemplateId";
  private static final String FROM_SAMPLE_WHERE = "from Instrument where ";
  private String defaultTemplateOwner;

  public InstrumentDaoHibernateImpl(Class<InstrumentEntity> persistentClass) {
    super(persistentClass);
  }

  public InstrumentDaoHibernateImpl() {
    super(InstrumentEntity.class);
  }

  /*
   * ============
   *  for tests
   * ============
   */

  @Override
  public InstrumentEntity getInstrumentTemplate(Long templateId) {
    return null;
  }

  @Override
  public InstrumentEntity getInstrument(Long templateId) {
    return null;
  }

  @Override
  public Instrument persistNewInstrument(Instrument instrument) {
    return null;
  }

  @Override
  public void resetDefaultTemplateOwner() {
    defaultTemplateOwner = null;
  }
}
