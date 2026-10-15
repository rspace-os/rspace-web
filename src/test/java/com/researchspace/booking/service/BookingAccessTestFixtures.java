package com.researchspace.booking.service;

import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class BookingAccessTestFixtures {

  private BookingAccessTestFixtures() {}

  static ResolvedResourceAccess readableAccess() {
    return new ResolvedResourceAccess(
        Optional.of(BookingResourceRoleScheme.VIEWER),
        Set.of(BookingResourceRoleScheme.READ_RESOURCE),
        List.of());
  }
}
