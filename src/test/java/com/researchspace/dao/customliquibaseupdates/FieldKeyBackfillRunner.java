package com.researchspace.dao.customliquibaseupdates;

import org.hibernate.SessionFactory;
import org.springframework.context.ApplicationContext;

/** Runs a field key backfill in the current transaction, as Liquibase would on upgrade. */
public final class FieldKeyBackfillRunner {

  private FieldKeyBackfillRunner() {}

  /**
   * Flushes pending changes, runs the backfill and clears the session, so entities read afterwards
   * carry the new keys.
   *
   * @return the backfill's confirmation message
   */
  public static String run(
      AbstractFieldKeyBackfill backfill,
      ApplicationContext context,
      SessionFactory sessionFactory) {
    sessionFactory.getCurrentSession().flush();
    backfill.context = context;
    backfill.sessionFactory = sessionFactory;
    backfill.doExecute(null);
    sessionFactory.getCurrentSession().clear();
    return backfill.getConfirmationMessage();
  }
}
