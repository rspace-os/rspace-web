package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.researchspace.booking.dao.TimeSlotBookingDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookingState;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

public class BookingCreateIdempotencyIT extends RealTransactionSpringTestBase {
  private final TimeSlotBookingManager bookings;
  private final BookingConfigurationManager configurations;
  private final InstrumentDao instruments;
  private final TimeSlotBookingDao bookingDao;

  @Autowired
  BookingCreateIdempotencyIT(
      TimeSlotBookingManager bookings,
      BookingConfigurationManager configurations,
      InstrumentDao instruments,
      TimeSlotBookingDao bookingDao) {
    this.bookings = bookings;
    this.configurations = configurations;
    this.instruments = instruments;
    this.bookingDao = bookingDao;
  }

  private TimeSlotBookingManager.Create command(User owner) {
    var item = createBasicInstrumentForUser(owner, "Retry safe instrument");
    var setup =
        TimeSlotBookingManagerIT.persistConfiguration(
            configurations,
            instruments,
            this::openTransaction,
            this::commitTransaction,
            owner,
            item.getId(),
            true,
            0,
            0);
    Instant start = TimeSlotBookingManagerIT.sevenDaysFromNow();
    return new TimeSlotBookingManager.Create(
        new ResolvedBookableTarget(setup.target(), setup.instrument()),
        Date.from(start),
        Date.from(start.plus(1, ChronoUnit.HOURS)),
        "original");
  }

  @Test
  void replayUsesOriginalFingerprintAfterEditAndCancellation() {
    User owner = createInitAndLoginAnyUser();
    var command = command(owner);
    var executor = new BookingCreateExecutor(bookings, getTxMger());
    var first = executor.create(command, "intent-1", owner, owner);
    bookings.updateBooking(
        first.booking().getId(),
        new TimeSlotBookingManager.Patch(null, null, true, "edited", BookingState.CANCELLED),
        owner,
        owner);
    var replay = executor.create(command, "intent-1", owner, owner);
    assertTrue(replay.replayed());
    assertEquals(first.booking().getId(), replay.booking().getId());
    assertEquals(BookingState.CANCELLED, replay.booking().getState());
    assertEquals("edited", replay.booking().getPurpose());
    var changed =
        new TimeSlotBookingManager.Create(
            command.target(), command.start(), command.end(), "different");
    assertThrows(
        BookingIdempotencyConflictException.class,
        () -> executor.create(changed, "intent-1", owner, owner));
  }

  @Test
  void noKeyKeepsAllowedOverlappingBookings() {
    User owner = createInitAndLoginAnyUser();
    var command = command(owner);
    var executor = new BookingCreateExecutor(bookings, getTxMger());
    assertNotEquals(
        executor.create(command, null, owner, owner).booking().getId(),
        executor.create(command, null, owner, owner).booking().getId());
  }

  @Test
  void concurrentMissingKeyReadsInsertOnceAndRecoverInFreshTransaction() throws Exception {
    User owner = createInitAndLoginAnyUser();
    var command = command(owner);
    CountDownLatch missingReads = new CountDownLatch(2);
    TimeSlotBookingManager controlled = mock(TimeSlotBookingManager.class, delegatesTo(bookings));
    doAnswer(
            invocation -> {
              var result =
                  bookings.replayCreate(
                      invocation.getArgument(0), invocation.getArgument(1), owner, owner);
              if (result.isEmpty()) {
                missingReads.countDown();
                assertTrue(
                    missingReads.await(20, TimeUnit.SECONDS),
                    "Both transactions must observe the missing identity");
              }
              return result;
            })
        .when(controlled)
        .replayCreate(any(), eq("racing-intent"), any(), any());
    var executor = new BookingCreateExecutor(controlled, getTxMger());
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Callable<BookingCreateExecutor.Result> create =
          () -> executor.create(command, "racing-intent", owner, owner);
      var one = pool.submit(create);
      var two = pool.submit(create);
      var first = one.get(40, TimeUnit.SECONDS);
      var second = two.get(40, TimeUnit.SECONDS);
      assertEquals(first.booking().getId(), second.booking().getId());
      assertNotEquals(first.replayed(), second.replayed());
      new TransactionTemplate(getTxMger())
          .executeWithoutResult(
              status ->
                  assertEquals(
                      first.booking().getId(),
                      bookingDao
                          .findByCreatorAndKey(owner.getId(), "racing-intent")
                          .orElseThrow()
                          .getId()));
    } finally {
      pool.shutdownNow();
    }
  }
}
