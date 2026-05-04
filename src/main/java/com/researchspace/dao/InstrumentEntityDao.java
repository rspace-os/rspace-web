package com.researchspace.dao;

import com.researchspace.model.inventory.InstrumentEntity;

/** For DAO operations on Inventory InstrumentEntity. */
public interface InstrumentEntityDao<T extends InstrumentEntity> extends GenericDao<T, Long> {

  /** Should be only called in unit tests (I think). Sets stored default template owner to null. */
  void resetDefaultTemplateOwner();
}
