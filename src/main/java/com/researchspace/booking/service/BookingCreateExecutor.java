package com.researchspace.booking.service;

import com.researchspace.model.User;
import com.researchspace.model.booking.TimeSlotBooking;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Owns rollback before recovery from a concurrent duplicate create. */
@Service
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class BookingCreateExecutor {
  public record Result(TimeSlotBooking booking, boolean replayed) {}

  private final TimeSlotBookingManager bookings;
  private final TransactionTemplate transaction;

  public BookingCreateExecutor(
      TimeSlotBookingManager bookings, PlatformTransactionManager transactions) {
    this.bookings = bookings;
    transaction = new TransactionTemplate(transactions);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** Commits one create, or returns the committed winner after a duplicate insert rolls back. */
  public Result create(TimeSlotBookingManager.Create create, String key, User subject, User actor) {
    if (key == null) {
      return new Result(bookings.createBooking(create, subject, actor), false);
    }
    if (!key.matches("[A-Za-z0-9._:-]{1,64}")) {
      throw new IllegalArgumentException(
          "Idempotency-Key must contain 1 to 64 ASCII token characters");
    }
    try {
      return transaction.execute(
          status -> {
            Optional<TimeSlotBooking> replay = bookings.replayCreate(create, key, subject, actor);
            return replay
                .map(booking -> new Result(booking, true))
                .orElseGet(
                    () -> new Result(bookings.createBooking(create, key, subject, actor), false));
          });
    } catch (DataIntegrityViolationException | BookingOverlapException exception) {
      log.warn("Booking create rolled back; checking for a committed retry identity", exception);
      // The failed transaction has ended. Only recover if this identity actually won.
      Optional<TimeSlotBooking> winner =
          transaction.execute(status -> bookings.replayCreate(create, key, subject, actor));
      if (winner.isPresent()) {
        return new Result(winner.get(), true);
      }
      throw exception;
    }
  }
}
