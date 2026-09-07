package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;

/** Authorization seam for resource-scoped maintenance-event management. */
public interface BookingMaintenancePolicy {

  /** Returns whether a direct caller may manage maintenance for the target. */
  boolean canManageMaintenance(BookableTargetReference target, User subject, User actor);
}
