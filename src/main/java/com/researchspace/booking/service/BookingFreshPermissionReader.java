package com.researchspace.booking.service;

import com.researchspace.dao.UserDao;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.service.resourceaccess.ResolvedResourceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Reads current permission facts outside a mutation's potentially older Hibernate snapshot. */
@Component
@RequiredArgsConstructor
public class BookingFreshPermissionReader {
  private final BookingItemPermissions permissions;
  private final UserDao users;

  /** The calling mutation must hold the permission fence until its own transaction commits. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public ResolvedResourceAccess resolve(BookableTargetReference target, Long subjectId) {
    return permissions.resolve(target, users.get(subjectId));
  }
}
