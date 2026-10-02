package com.researchspace.booking.service;

import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Isolates subscription creation from cached authorization facts in the caller's session. */
@Component
@RequiredArgsConstructor
public class BookingCalendarCreationTransaction {
  private final UserDao users;

  /**
   * Creates or rotates a subscription with a fresh subject inside a READ_COMMITTED transaction.
   *
   * <p>The item callback locks the booking configuration before resolving mutation permission; that
   * resolution acquires the shared permission fence. Keeping the fence acquisition in the callback
   * preserves the configuration-to-fence lock order used by all booking mutations.
   *
   * <p>READ_COMMITTED also lets a caller that waited on the configuration or user lock see a link
   * committed by the request it waited for, so a repeated create returns that link.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
  public BookingCalendarManager.Created create(
      Long userId, Function<User, BookingCalendarManager.Created> creation) {
    return creation.apply(users.get(userId));
  }
}
