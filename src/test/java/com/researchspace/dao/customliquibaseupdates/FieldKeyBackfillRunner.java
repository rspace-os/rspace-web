package com.researchspace.dao.customliquibaseupdates;

import org.hibernate.SessionFactory;

/** Runs a field key backfill in the current transaction, as Liquibase would on upgrade. */
public final class FieldKeyBackfillRunner {

  private FieldKeyBackfillRunner() {}

  /**
   * Flushes pending changes, runs the backfill on the session's connection and clears the session,
   * so entities read afterwards carry the new keys.
   *
   * @return the backfill's confirmation message
   */
  public static String run(AbstractFieldKeyBackfill backfill, SessionFactory sessionFactory) {
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().doWork(backfill::backfill);
    sessionFactory.getCurrentSession().clear();
    return backfill.getConfirmationMessage();
  }
}
