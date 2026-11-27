package com.researchspace.service.dmp;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;

/** Writes a deposit's link onto a plan held by one DMP platform. */
public interface DmpReporter {

  /**
   * @param source a plan's platform
   * @return whether this reporter writes to plans from that platform
   */
  boolean supports(DMPSource source);

  /**
   * Sends one value to the plan: the DOI link if the deposit has one, otherwise its access URL.
   * Never throws.
   *
   * @param plan the plan to update
   * @param deposit the deposit's links, at least one of which is non-null
   * @param user the user who owns the plan and made the deposit
   * @return the outcome, with status {@link PlanReportStatus#REPORTED}
   */
  PlanReportResult report(DMPUser plan, DepositReport deposit, User user);
}
