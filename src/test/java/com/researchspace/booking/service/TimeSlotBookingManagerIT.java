package com.researchspace.booking.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.booking.BookableTargetReference;
import com.researchspace.model.booking.BookableTargetType;
import com.researchspace.model.booking.BookingConfiguration;
import com.researchspace.model.booking.ResolvedBookableTarget;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hibernate.Hibernate;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class TimeSlotBookingManagerIT extends RealTransactionSpringTestBase {

  @Autowired private TimeSlotBookingManager bookingManager;
  @Autowired private BookingConfigurationDao configurationDao;
  @Autowired private InstrumentDao instrumentDao;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  public void configurationLockSerializesTwoOverlappingCreates() throws Exception {
    User owner = createInitAndLoginAnyUser();
    ApiInstrument created = createBasicInstrumentForUser(owner, "Concurrency scope");
    Setup setup = persistConfiguration(owner, created.getId());
    CountDownLatch holderLocked = new CountDownLatch(1);
    CountDownLatch releaseHolder = new CountDownLatch(1);
    CountDownLatch contendersStarted = new CountDownLatch(2);
    CountDownLatch startContenders = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(3);

    try {
      Future<?> holder =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          ignored -> {
                            configurationDao.lockById(setup.configurationId()).orElseThrow();
                            holderLocked.countDown();
                            await(releaseHolder);
                          }));
      assertTrue(holderLocked.await(10, TimeUnit.SECONDS));

      var create =
          new TimeSlotBookingManager.Create(
              new ResolvedBookableTarget(setup.target(), setup.instrument()),
              Date.from(Instant.parse("2026-08-17T10:00:00Z")),
              Date.from(Instant.parse("2026-08-17T11:00:00Z")),
              "Concurrent test");
      Future<Throwable> first =
          pool.submit(() -> attemptCreate(create, owner, contendersStarted, startContenders));
      Future<Throwable> second =
          pool.submit(() -> attemptCreate(create, owner, contendersStarted, startContenders));
      assertTrue(contendersStarted.await(10, TimeUnit.SECONDS));
      startContenders.countDown();
      releaseHolder.countDown();

      List<Throwable> results =
          Arrays.asList(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
      holder.get(20, TimeUnit.SECONDS);

      assertEquals(1, results.stream().filter(result -> result == null).count());
      assertEquals(1, results.stream().filter(BookingOverlapException.class::isInstance).count());
      assertEquals(
          Integer.valueOf(1),
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM TimeSlotBooking WHERE bookingConfiguration_id = ? AND deleted ="
                  + " 0",
              Integer.class,
              setup.configurationId()));
    } finally {
      releaseHolder.countDown();
      startContenders.countDown();
      pool.shutdownNow();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  private Setup persistConfiguration(User owner, Long instrumentId) {
    openTransaction();
    Instrument instrument = instrumentDao.get(instrumentId);
    Hibernate.initialize(instrument.getOwner());
    BookableTargetReference target =
        new BookableTargetReference(BookableTargetType.INSTRUMENT, instrumentId);
    BookingConfiguration configuration = new BookingConfiguration();
    configuration.setEnabled(true);
    configuration.setTimeZone("UTC");
    configuration.replaceTarget(target);
    sessionFactory.getCurrentSession().persist(configuration);
    sessionFactory.getCurrentSession().flush();
    Long configurationId = configuration.getId();
    commitTransaction();
    return new Setup(configurationId, target, instrument);
  }

  private Throwable attemptCreate(
      TimeSlotBookingManager.Create create,
      User owner,
      CountDownLatch started,
      CountDownLatch start) {
    started.countDown();
    await(start);
    try {
      bookingManager.createBooking(create, owner, owner);
      return null;
    } catch (RuntimeException exception) {
      return exception;
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(20, TimeUnit.SECONDS));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new AssertionError(exception);
    }
  }

  private record Setup(
      Long configurationId, BookableTargetReference target, Instrument instrument) {}
}
