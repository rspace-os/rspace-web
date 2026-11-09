package com.researchspace.booking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.researchspace.booking.dao.BookingConfigurationDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.query.CollectionQueryExecutor;
import com.researchspace.model.User;
import com.researchspace.model.booking.*;
import com.researchspace.model.collection.*;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import java.util.concurrent.*;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

class BookingConfigurationSelectionIT extends RealTransactionSpringTestBase {
  private final BookingConfigurationManager configurations;
  private final BookingConfigurationDao dao;
  private final InstrumentDao instruments;

  @Autowired
  BookingConfigurationSelectionIT(
      BookingConfigurationManager configurations,
      BookingConfigurationDao dao,
      InstrumentDao instruments) {
    this.configurations = configurations;
    this.dao = dao;
    this.instruments = instruments;
  }

  @Test
  void patchCommittedAfterBulkSelectionIsPreserved() throws Exception {
    verifyPatchBetweenSelectionAndLocking(false);
  }

  @Test
  void patchMovingConfigurationOutOfFilterIsNotArchived() throws Exception {
    verifyPatchBetweenSelectionAndLocking(true);
  }

  private void verifyPatchBetweenSelectionAndLocking(boolean moveOutOfFilter) throws Exception {
    var owner = createInitAndLoginAnyUser();
    var admin = getSysAdminUser();
    var configuration = createConfiguration(owner);
    var matching = moveOutOfFilter ? createConfiguration(owner) : null;
    Object target = AopTestUtils.getUltimateTargetObject(dao);
    @SuppressWarnings("unchecked")
    var original =
        (CollectionQueryExecutor<BookingConfiguration>)
            ReflectionTestUtils.getField(target, "collectionQuery");
    var observed = spy(original);
    var selected = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            call -> {
              Object rows = call.callRealMethod();
              selected.countDown();
              assertTrue(release.await(20, TimeUnit.SECONDS));
              return rows;
            })
        .when(observed)
        .listById(any(), any(), any(), anyInt(), any());
    var executor = Executors.newSingleThreadExecutor();
    ReflectionTestUtils.setField(target, "collectionQuery", observed);
    try {
      FilterExpression filter =
          new FilterExpression.Comparison(
              moveOutOfFilter ? "timezone" : "id",
              Operator.EQUAL,
              moveOutOfFilter ? List.of("UTC") : List.of(configuration.getId()),
              false);
      if (moveOutOfFilter) {
        filter =
            new FilterExpression.And(
                List.of(
                    filter,
                    new FilterExpression.Comparison(
                        "id",
                        Operator.IN,
                        List.of(configuration.getId(), matching.getId()),
                        false)));
      }
      var request = ResourceRequest.unpaged(filter);
      var bulk = executor.submit(() -> configurations.archiveConfigurations(request, admin, admin));
      if (!selected.await(20, TimeUnit.SECONDS)) {
        bulk.get(1, TimeUnit.SECONDS);
        fail("Bulk selection did not reach the latch");
      }
      configurations.updateConfiguration(
          configuration.getId(),
          new BookingConfigurationManager.Patch(null, "Europe/Berlin"),
          owner,
          owner);
      release.countDown();
      var archived = bulk.get(30, TimeUnit.SECONDS);
      if (moveOutOfFilter) {
        assertFalse(archived.stream().anyMatch(row -> row.getId().equals(configuration.getId())));
        assertTrue(archived.stream().anyMatch(row -> row.getId().equals(matching.getId())));
        assertEquals(
            BookingConfigurationState.ARCHIVED,
            configurations
                .getConfigurationForAudit(matching.getId(), admin)
                .orElseThrow()
                .getState());
        var retained = configurations.getConfiguration(configuration.getId(), owner).orElseThrow();
        assertEquals(BookingConfigurationState.ACTIVE, retained.getState());
        assertEquals("Europe/Berlin", retained.getTimeZone());
      } else {
        assertEquals(BookingConfigurationState.ARCHIVED, archived.get(0).getState());
        assertEquals("Europe/Berlin", archived.get(0).getTimeZone());
      }
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
      ReflectionTestUtils.setField(target, "collectionQuery", original);
    }
  }

  private BookingConfiguration createConfiguration(User owner) throws Exception {
    var created = createBasicInstrumentForUser(owner, "Bulk selection regression");
    var instrument =
        new TransactionTemplate(getTxMger())
            .execute(
                status -> {
                  var loaded = instruments.get(created.getId());
                  Hibernate.initialize(loaded.getOwner());
                  return loaded;
                });
    return configurations.createConfiguration(
        new BookingConfigurationManager.Create(
            true,
            "UTC",
            new ResolvedBookableTarget(
                new BookableTargetReference(BookableTargetType.INSTRUMENT, created.getId()),
                instrument)),
        owner,
        owner);
  }
}
