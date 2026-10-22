package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestStatusPut;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/** Concurrent changes to a request, or to its sample, must leave a consistent result. */
public class SampleRequestConcurrencyIT extends RealTransactionSpringTestBase {

  private @Autowired SampleRequestApiManager sampleRequestApiMgr;
  private @Autowired SystemPropertyManager systemPropertyMgr;

  private String originalSampleRequestsAvailable;
  private User owner;
  private User requester;
  private Long requestId;
  private Long sampleId;
  private String sampleGlobalId;
  private final List<Runnable> restoreSwappedDependencies = new ArrayList<>();

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    owner =
        createAndSaveUser(getRandomAlphabeticString("cOwner"), com.researchspace.Constants.PI_ROLE);
    requester = createAndSaveUser(getRandomAlphabeticString("cReq"));
    initUsers(owner, requester);
    createGroupForUsersWithDefaultPi(owner, requester);

    logoutAndLoginAsSysAdmin();
    originalSampleRequestsAvailable =
        systemPropertyMgr.findByName(SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE).getValue();
    systemPropertyMgr.save(
        SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
        HierarchicalPermission.ALLOWED,
        getSysAdminUser());

    logoutAndLoginAs(owner);
    ApiSampleWithFullSubSamples sample = createBasicSampleForUser(owner);
    sampleId = sample.getId();
    sampleGlobalId = sample.getGlobalId();
    ApiSample requestable = new ApiSample();
    requestable.setId(sample.getId());
    requestable.setRequestable(true);
    sampleApiMgr.updateApiSample(requestable, owner);

    logoutAndLoginAs(requester);
    ApiSampleRequestPost post = new ApiSampleRequestPost();
    post.setSampleGlobalId(sample.getGlobalId());
    post.setNote("needed for the assay");
    requestId = sampleRequestApiMgr.createRequest(post, requester).getId();
  }

  @AfterEach
  public void tearDown() throws Exception {
    restoreSwappedDependencies.forEach(Runnable::run);
    restoreSwappedDependencies.clear();
    if (originalSampleRequestsAvailable != null) {
      logoutAndLoginAsSysAdmin();
      systemPropertyMgr.save(
          SystemPropertyName.SAMPLE_REQUESTS_AVAILABLE,
          originalSampleRequestsAvailable,
          getSysAdminUser());
      originalSampleRequestsAvailable = null;
    }
    super.tearDown();
  }

  @Test
  public void concurrentRejectAndFulfilLeaveExactlyOneWinner() throws Exception {
    CountDownLatch startLine = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // both are terminal, so whichever commits first makes the other illegal
      Future<String> fulfil =
          pool.submit(transition(startLine, SampleRequestStatus.FULFILLED, null));
      Future<String> reject =
          pool.submit(transition(startLine, SampleRequestStatus.REJECTED, "no material left"));

      startLine.countDown();
      List<String> outcomes =
          List.of(fulfil.get(30, TimeUnit.SECONDS), reject.get(30, TimeUnit.SECONDS));

      assertEquals(
          1,
          outcomes.stream().filter(WON::equals).count(),
          "exactly one of two concurrent transitions must be applied");

      // the loser waited on the row lock, then was refused by the normal transition rule; without
      // the lock it would also win, or fail with a deadlock instead
      assertEquals(
          ApiRuntimeException.class.getSimpleName(),
          outcomes.stream().filter(o -> !WON.equals(o)).findFirst().orElseThrow(),
          "the loser should be refused by the transition rule");

      // PENDING plus exactly one transition, never both
      ApiSampleRequest afterwards = sampleRequestApiMgr.getRequestById(requestId, owner);
      assertEquals(
          2,
          afterwards.getStatusChanges().size(),
          "history must record the creation and exactly one transition");
      assertEquals(
          afterwards.getStatus(),
          afterwards.getStatusChanges().get(1).getStatus(),
          "current status must match the last history entry");
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void requestFulfilledDuringATransferStaysFulfilled() throws Exception {
    User newOwner = createAndSaveUser(getRandomAlphabeticString("cNew"));
    initUsers(newOwner);
    CountDownLatch fulfilmentHeld = new CountDownLatch(1);
    CountDownLatch releaseFulfilment = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // the fulfilment has locked the row but not yet committed
      Future<?> fulfilment =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          tx -> {
                            ApiSampleRequestStatusPut fulfil = new ApiSampleRequestStatusPut();
                            fulfil.setStatus(SampleRequestStatus.FULFILLED);
                            sampleRequestApiMgr.updateStatus(requestId, fulfil, owner);
                            fulfilmentHeld.countDown();
                            awaitQuietly(releaseFulfilment);
                          }));
      assertTrue(fulfilmentHeld.await(30, TimeUnit.SECONDS));

      Future<?> transfer =
          pool.submit(
              () -> {
                ApiSample toNewOwner = new ApiSample();
                toNewOwner.setId(sampleId);
                toNewOwner.setOwner(new ApiUser(newOwner));
                sampleApiMgr.changeApiSampleOwner(toNewOwner, owner);
              });
      // long enough for the transfer to read the request before the fulfilment commits
      Thread.sleep(2000);
      releaseFulfilment.countDown();
      fulfilment.get(30, TimeUnit.SECONDS);
      try {
        transfer.get(60, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        // with innodb_snapshot_isolation (MariaDB 11.6+) the transfer is refused instead, which is
        // equally safe; any other failure is not
        assertTrue(hasSqlErrorCode(e, 1020), () -> "unexpected transfer failure: " + e.getCause());
        // the API maps a conflict to 409 only when it arrives as a DataAccessException
        assertInstanceOf(DataAccessException.class, e.getCause());
      }

      ApiSampleRequest afterwards = sampleRequestApiMgr.getRequestById(requestId, owner);
      assertEquals(SampleRequestStatus.FULFILLED, afterwards.getStatus());
      assertEquals(
          2,
          afterwards.getStatusChanges().size(),
          "the transfer must not reject a request that was fulfilled while it ran");
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  public void requestRaisedDuringATransferIsNotLeftWithThePreviousOwner() throws Exception {
    User newOwner = createAndSaveUser(getRandomAlphabeticString("cNew"));
    initUsers(newOwner);
    CountDownLatch createPaused = new CountDownLatch(1);
    CountDownLatch releaseCreate = new CountDownLatch(1);
    pauseCreateRequestAfterItsLock(createPaused, releaseCreate);
    CountDownLatch transferClosing = new CountDownLatch(1);
    CountDownLatch transferClosed = new CountDownLatch(1);
    watchTransferClosingRequests(transferClosing, transferClosed);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // the new request has read the sample, and still holds its lock, but is not saved yet
      Future<Long> create =
          pool.submit(
              () -> {
                ApiSampleRequestPost post = new ApiSampleRequestPost();
                post.setSampleGlobalId(sampleGlobalId);
                post.setNote("raised while the sample is being transferred");
                return sampleRequestApiMgr.createRequest(post, requester).getId();
              });
      assertTrue(createPaused.await(30, TimeUnit.SECONDS));

      Future<?> transfer =
          pool.submit(
              () -> {
                ApiSample toNewOwner = new ApiSample();
                toNewOwner.setId(sampleId);
                toNewOwner.setOwner(new ApiUser(newOwner));
                sampleApiMgr.changeApiSampleOwner(toNewOwner, owner);
              });
      // the transfer has reached the step that competes with the new request, and the request's
      // lock holds it there until the request is saved
      assertTrue(transferClosing.await(30, TimeUnit.SECONDS));
      assertFalse(
          transferClosed.await(2, TimeUnit.SECONDS),
          "the transfer must wait for the request being raised");
      releaseCreate.countDown();
      Long raisedId = create.get(30, TimeUnit.SECONDS);

      boolean transferred = true;
      try {
        transfer.get(60, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        // with innodb_snapshot_isolation (MariaDB 11.6+) the transfer is refused instead
        assertTrue(
            hasSqlErrorCode(e, 1020, 1213), () -> "unexpected transfer failure: " + e.getCause());
        transferred = false;
      }

      ApiSampleRequest raised = sampleRequestApiMgr.getRequestById(raisedId, requester);
      if (transferred) {
        assertEquals(
            SampleRequestStatus.REJECTED,
            raised.getStatus(),
            "the transfer must close a request raised while it ran");
      } else {
        assertEquals(SampleRequestStatus.PENDING, raised.getStatus());
        assertEquals(
            owner.getUsername(),
            sampleApiMgr.getApiSampleById(sampleId, owner).getOwner().getUsername());
      }
    } finally {
      releaseCreate.countDown();
      pool.shutdownNow();
    }
  }

  /**
   * Holds createRequest at its first permission check, which comes after it share-locks the sample
   * and before it saves the request.
   */
  private void pauseCreateRequestAfterItsLock(CountDownLatch paused, CountDownLatch release) {
    SystemPropertyPermissionManager realPermissions =
        swapDependency(
            sampleRequestApiMgr,
            "systemPropertyPermissions",
            SystemPropertyPermissionManager.class);
    SystemPropertyPermissionManager pausing =
        currentDependency(sampleRequestApiMgr, "systemPropertyPermissions");
    Mockito.doAnswer(
            call -> {
              if (paused.getCount() > 0) {
                paused.countDown();
                awaitQuietly(release);
              }
              return realPermissions.isPropertyAllowed(
                  call.<User>getArgument(0), call.<SystemPropertyName>getArgument(1));
            })
        .when(pausing)
        .isPropertyAllowed(Mockito.any(User.class), Mockito.any(SystemPropertyName.class));
  }

  /** Counts the transfer into and out of closing the sample's open requests. */
  private void watchTransferClosingRequests(CountDownLatch entered, CountDownLatch finished) {
    SampleRequestApiManager realRequests =
        swapDependency(sampleApiMgr, "sampleRequestApiManager", SampleRequestApiManager.class);
    SampleRequestApiManager watching = currentDependency(sampleApiMgr, "sampleRequestApiManager");
    Mockito.doAnswer(
            call -> {
              entered.countDown();
              realRequests.closeActiveRequestsForTransferredSample(
                  call.getArgument(0), call.getArgument(1), call.getArgument(2));
              finished.countDown();
              return null;
            })
        .when(watching)
        .closeActiveRequestsForTransferredSample(Mockito.any(), Mockito.any(), Mockito.any());
  }

  /**
   * Replaces a manager's dependency with a Mockito mock that delegates to the real one, for the
   * test to stub, and restores it after the test. Returns the real dependency.
   */
  private <T> T swapDependency(Object manager, String field, Class<T> type) {
    Object target = AopTestUtils.getUltimateTargetObject(manager);
    T real = type.cast(ReflectionTestUtils.getField(target, field));
    ReflectionTestUtils.setField(
        target, field, Mockito.mock(type, AdditionalAnswers.delegatesTo(real)));
    restoreSwappedDependencies.add(() -> ReflectionTestUtils.setField(target, field, real));
    return real;
  }

  @SuppressWarnings("unchecked")
  private static <T> T currentDependency(Object manager, String field) {
    Object target = AopTestUtils.getUltimateTargetObject(manager);
    return (T) ReflectionTestUtils.getField(target, field);
  }

  /** MariaDB's "record has changed since last read" (1020) or "deadlock found" (1213). */
  private static boolean hasSqlErrorCode(Throwable thrown, int... codes) {
    for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql
          && Arrays.stream(codes).anyMatch(code -> code == sql.getErrorCode())) {
        return true;
      }
    }
    return false;
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static final String WON = "won";

  /** Returns WON, or the simple name of whatever refused this transition. */
  private Callable<String> transition(
      CountDownLatch startLine, SampleRequestStatus target, String reason) {
    return () -> {
      startLine.await();
      ApiSampleRequestStatusPut put = new ApiSampleRequestStatusPut();
      put.setStatus(target);
      put.setReason(reason);
      try {
        sampleRequestApiMgr.updateStatus(requestId, put, owner);
        return WON;
      } catch (Exception refused) {
        return refused.getClass().getSimpleName();
      }
    };
  }
}
