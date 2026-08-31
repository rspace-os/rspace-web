package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import org.springframework.stereotype.Component;

/** Temporary sysadmin-only maintenance policy pending booking-configuration ownership. */
@Component
public final class BookingMaintenancePolicyImpl implements BookingMaintenancePolicy {

  @Override
  public boolean canManageMaintenance(BookableTargetReference target, User subject, User actor) {
    return target != null
        && subject != null
        && actor != null
        && subject.getId() != null
        && subject.getId().equals(actor.getId())
        && subject.hasSysadminRole()
        && actor.hasSysadminRole();
  }
}
