package com.researchspace.dao.customliquibaseupdates.v62;

import com.researchspace.dao.customliquibaseupdates.AbstractCustomLiquibaseUpdater;
import com.researchspace.model.preference.Preference;
import liquibase.database.Database;

/** RSPAC-1908 - delete CHAMELEON_TOURS_ENABLED_FOR_USER rows in UserPreference table */
public class DeleteUserPreferenceForChameleonTours extends AbstractCustomLiquibaseUpdater {

  private int deletedRowsCounter = 0;

  @Override
  public String getConfirmationMessage() {
    return "UserPreference table cleared from "
        + deletedRowsCounter
        + " obsolete Chameleon-related rows.";
  }

  @Override
  protected void doExecute(Database database) {

    deletedRowsCounter =
        sessionFactory
            .getCurrentSession()
            .createQuery("delete from UserPreference where preference=:preferenceId")
            .setParameter("preferenceId", Preference.CHAMELEON_TOURS_ENABLED_FOR_USER)
            .executeUpdate();
  }
}
