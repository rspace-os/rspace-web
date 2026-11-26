package com.researchspace.dao;

import com.researchspace.model.inventory.InstrumentEntity;

/** For DAO operations on Inventory InstrumentEntity. */
public interface InstrumentEntityDao<T extends InstrumentEntity> extends GenericDao<T, Long> {

  /** Should be only called in unit tests (I think). Sets stored default template owner to null. */
  void resetDefaultTemplateOwner();

  /**
   * Reloads the record's current state under its row lock, keeping the session's instance; see
   * {@code InventoryDaoHibernate#refreshForUpdate}.
   */
  void refreshForUpdate(T record);
}
