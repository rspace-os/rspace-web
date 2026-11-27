package com.researchspace.service.dmp;

/** What happened, or will happen, to one ticked plan during write-back. */
public enum PlanReportStatus {
  /** A value was attempted; see {@link PlanReportResult#values()} for whether it was written. */
  REPORTED,
  /** The deposit has no DOI link and no repository link. */
  NOTHING_TO_SEND,
  /** No reporter exists for the plan's platform. */
  NOT_SUPPORTED,
  /** The plan's platform is not available or not enabled. */
  PLATFORM_OFF,
  /** The platform is on but the user has no connection to it. */
  NOT_CONNECTED,
  /** RSpace does not know which platform the plan came from. */
  UNKNOWN_SOURCE
}
