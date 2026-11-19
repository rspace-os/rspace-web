package com.researchspace.dao.customliquibaseupdates;

/**
 * Gives every existing ELN form field a key, so that any field can be picked as a rule source or
 * target. This includes deleted fields and the unsaved draft copies of published forms' fields; a
 * later changeset gives each draft copy its original's key.
 */
public class BackfillFormFieldKeys_RSDEV1708 extends AbstractFieldKeyBackfill {

  @Override
  protected String table() {
    return "FieldForm";
  }

  @Override
  protected String idsToKeySql() {
    return "select id from FieldForm where fieldKey is null order by id";
  }
}
