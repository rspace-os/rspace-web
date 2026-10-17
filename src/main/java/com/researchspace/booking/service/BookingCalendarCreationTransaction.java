package com.researchspace.booking.service;

import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Runs calendar credential creation against current authorization and subscription state. */
@Component
@RequiredArgsConstructor
public class BookingCalendarCreationTransaction {
  private final UserDao users;

  /** Runs one creation or rotation with a freshly loaded subject in its own transaction. */
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public BookingCalendarManager.Created create(
      Long userId, Function<User, BookingCalendarManager.Created> creation) {
    return creation.apply(users.get(userId));
  }
}
