package com.researchspace.dao;

import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;

/** For DAO operations on Inventory InstrumentEntity. */
public interface InstrumentEntityDao extends GenericDao<InstrumentEntity, Long> {

  InstrumentEntity getInstrumentTemplate(Long templateId);

  InstrumentEntity getInstrument(Long templateId);

  /**
   * Use this method over standard 'save' to persist new InstrumentEntity without getting
   * ConstraintViolationException.
   *
   * @return saved instrumentEntity that can be an Instrument or an InstrumentTemplate
   */
  Instrument persistNewInstrument(Instrument instrument);

  /** Should be only called in unit tests (I think). Sets stored default template owner to null. */
  void resetDefaultTemplateOwner();
}
