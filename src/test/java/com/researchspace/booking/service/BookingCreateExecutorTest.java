package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.model.User;
import com.researchspace.model.booking.TimeSlotBooking;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

class BookingCreateExecutorTest {
  @Test
  void duplicateRollsBackBeforeWinnerReadAndNeverCreatesAgain() {
    TimeSlotBookingManager bookings = mock(TimeSlotBookingManager.class);
    PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    var failed = new SimpleTransactionStatus();
    var recovery = new SimpleTransactionStatus();
    when(transactions.getTransaction(any(TransactionDefinition.class)))
        .thenReturn(failed, recovery);
    User actor = new User();
    var command = mock(TimeSlotBookingManager.Create.class);
    TimeSlotBooking winner = new TimeSlotBooking();
    when(bookings.replayCreate(command, "intent", actor, actor))
        .thenReturn(Optional.empty(), Optional.of(winner));
    when(bookings.createBooking(command, "intent", actor, actor))
        .thenThrow(new DuplicateKeyException("duplicate"));
    var result =
        new BookingCreateExecutor(bookings, transactions).create(command, "intent", actor, actor);
    assertTrue(result.replayed());
    assertSame(winner, result.booking());
    var ordered = inOrder(bookings, transactions);
    ordered.verify(bookings).replayCreate(command, "intent", actor, actor);
    ordered.verify(bookings).createBooking(command, "intent", actor, actor);
    ordered.verify(transactions).rollback(failed);
    ordered.verify(bookings).replayCreate(command, "intent", actor, actor);
    ordered.verify(transactions).commit(recovery);
    verify(bookings, times(1)).createBooking(command, "intent", actor, actor);
  }
}
