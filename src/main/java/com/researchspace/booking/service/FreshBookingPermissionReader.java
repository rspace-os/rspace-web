package com.researchspace.booking.service;

import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;

/** Reads current Booking permission facts in an isolated transaction. */
public interface FreshBookingPermissionReader {

  /** Resolves the subject's current inherited access for the target. */
  ResolvedResourceAccess resolve(BookableTargetReference target, Long subjectId);
}
