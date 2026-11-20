package com.researchspace.dao.customliquibaseupdates;

/**
 * Gives every existing sample and instrument template field a key, so any field an author can pick
 * in a display rule has one. Fields of samples and instruments are copies and keep a null key until
 * they are updated to the latest template version.
 */
public class BackfillInventoryTemplateFieldKeys_RSDEV1706 extends AbstractFieldKeyBackfill {

  @Override
  protected String table() {
    return "InventoryEntityField";
  }

  @Override
  protected String pageOfIdsToKeySql() {
    return "select f.id from InventoryEntityField f"
        + " left join Sample s on s.id = f.sample_id"
        + " left join InstrumentEntity i on i.id = f.instrumentEntity_id"
        + " where f.id > ? and f.fieldKey is null"
        + " and (s.DTYPE = 'SampleTemplate' or i.DTYPE = 'InstrumentTemplate')"
        + " order by f.id limit ?";
  }
}
