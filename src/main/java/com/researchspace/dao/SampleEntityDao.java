package com.researchspace.dao;

import com.researchspace.model.inventory.SampleEntity;

/** For DAO operations common to all Inventory {@link SampleEntity} subtypes. */
public interface SampleEntityDao<T extends SampleEntity> extends GenericDao<T, Long> {

  /**
   * Updates the icon stored for the given entity.
   *
   * <p>The DML targets {@code SampleEntity} (the mapped superclass table) rather than a concrete
   * subclass, so it applies correctly to both samples and templates. A subclass-scoped bulk update
   * would silently skip rows belonging to the other discriminator value.
   *
   * @param sample the entity whose icon to update (only its id is used)
   * @param iconId the new icon id to set
   * @return the number of rows updated (0 or 1)
   */
  int saveIconId(SampleEntity sample, Long iconId);

  /**
   * Use instead of plain {@code save} when subsample rows have not been directly modified (so
   * Hibernate will not cascade index updates to them) but they still need re-indexing, e.g. after
   * an owner transfer on the parent.
   *
   * @param sample the entity to save
   * @return the saved entity
   */
  T saveAndReindexSubSamples(T sample);
}
