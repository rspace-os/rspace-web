package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestStatusPut;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.api.v1.model.ApiUser;
import com.researchspace.dao.SampleDao;
import com.researchspace.dao.SampleRequestDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.service.SystemPropertyPermissionManager;
import com.researchspace.service.inventory.impl.InventoryEditLockTracker;
import com.researchspace.service.inventory.impl.SampleRequestApiManagerImpl;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import jakarta.ws.rs.NotFoundException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionTemplate;

/** Concurrent changes to a request, or to its sample, must leave a consistent result. */
public class SampleRequestConcurrencyIT extends RealTransactionSpringTestBase {

  private @Autowired SampleRequestApiManager sampleRequestApiMgr;
  private @Autowired SystemPropertyManager systemPropertyMgr;
  private @Autowired SampleRequestDao sampleRequestDao;
  private @Autowired SampleDao sampleDao;
  private @Autowired UserDao userDao;
  private @Autowired InventoryPermissionUtils invPermissions;
  private @Autowired SystemPropertyPermissionManager systemPropertyPermissions;
  private @Autowired ApplicationEventPublisher publisher;
  private @Autowired InventoryEditLockTracker editLockTracker;

  private String originalSampleRequestsAvailable;
  private User owner;
  private User requester;
  private Long requestId;
  private Long sampleId;
  private String sampleGlobalId;

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
    markSampleRequestable(sample, owner);

    logoutAndLoginAs(requester);
    ApiSampleRequestPost post = new ApiSampleRequestPost();
    post.setSampleGlobalId(sample.getGlobalId());
    post.setNote("needed for the assay");
    requestId = sampleRequestApiMgr.createRequest(post, requester).getId();
  }

  @AfterEach
  public void tearDown() throws Exception {
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
  public void fulfilByTransferWaitsForACancellationAndIsThenRefused() throws Exception {
    CountDownLatch cancelHeld = new CountDownLatch(1);
    CountDownLatch releaseCancel = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // the cancellation has locked the request but not yet committed
      Future<?> cancelling =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          tx -> {
                            ApiSampleRequestStatusPut cancel = new ApiSampleRequestStatusPut();
                            cancel.setStatus(SampleRequestStatus.CANCELLED);
                            sampleRequestApiMgr.updateStatus(requestId, cancel, requester);
                            cancelHeld.countDown();
                            awaitQuietly(releaseCancel);
                          }));
      assertTrue(cancelHeld.await(30, TimeUnit.SECONDS));

      Future<?> fulfilling =
          pool.submit(() -> sampleRequestApiMgr.fulfilByTransfer(requestId, null, owner));
      assertThrows(
          TimeoutException.class,
          () -> fulfilling.get(2, TimeUnit.SECONDS),
          "the fulfilment must wait for the cancellation");
      releaseCancel.countDown();
      cancelling.get(30, TimeUnit.SECONDS);

      ExecutionException refused =
          assertThrows(ExecutionException.class, () -> fulfilling.get(30, TimeUnit.SECONDS));
      assertInstanceOf(ApiRuntimeException.class, refused.getCause());
      assertEquals(
          owner.getUsername(),
          sampleApiMgr.getApiSampleById(sampleId, owner).getOwner().getUsername());
      ApiSampleRequest afterwards = sampleRequestApiMgr.getRequestById(requestId, owner);
      assertEquals(SampleRequestStatus.CANCELLED, afterwards.getStatus());
      assertEquals(2, afterwards.getStatusChanges().size());
    } finally {
      releaseCancel.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  public void fulfilByTransferWhoseTransferFailsLeavesTheRequestOpen() {
    // the fulfilment is recorded before the transfer runs, so only a rollback undoes it
    editLockTracker.attemptToLockForEdit(sampleGlobalId, requester);
    try {
      assertThrows(
          InventoryEditLockHeldException.class,
          () -> sampleRequestApiMgr.fulfilByTransfer(requestId, null, owner));
    } finally {
      editLockTracker.attemptToUnlock(sampleGlobalId, requester);
    }

    ApiSampleRequest afterwards = sampleRequestApiMgr.getRequestById(requestId, owner);
    assertEquals(SampleRequestStatus.PENDING, afterwards.getStatus());
    assertEquals(1, afterwards.getStatusChanges().size());
    assertEquals(
        owner.getUsername(),
        sampleApiMgr.getApiSampleById(sampleId, owner).getOwner().getUsername());
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
  public void secondTransferIsRefusedOnceTheFirstHasGivenTheSampleAway() throws Exception {
    User outsider = createAndSaveUser(getRandomAlphabeticString("cOut"));
    initUsers(outsider);
    // in a container, since moving a workbench subsample makes a stale transfer fail anyway
    ApiContainer shelf = createBasicContainerForUser(owner);
    Long subSampleId =
        sampleApiMgr.getApiSampleById(sampleId, owner).getSubSamples().get(0).getId();
    moveSubSampleIntoListContainer(subSampleId, shelf.getId(), owner);
    CountDownLatch sampleRead = new CountDownLatch(1);
    CountDownLatch releaseSecond = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(1);
    try {
      // the second transfer read the sample while the owner still owned it
      Future<?> second =
          pool.submit(
              () ->
                  new TransactionTemplate(getTxMger())
                      .executeWithoutResult(
                          tx -> {
                            sampleDao.get(sampleId);
                            sampleRead.countDown();
                            awaitQuietly(releaseSecond);
                            ApiSample toRequester = new ApiSample();
                            toRequester.setId(sampleId);
                            toRequester.setOwner(new ApiUser(requester));
                            sampleApiMgr.changeApiSampleOwner(toRequester, owner);
                          }));
      assertTrue(sampleRead.await(30, TimeUnit.SECONDS));

      ApiSample toOutsider = new ApiSample();
      toOutsider.setId(sampleId);
      toOutsider.setOwner(new ApiUser(outsider));
      sampleApiMgr.changeApiSampleOwner(toOutsider, owner);
      releaseSecond.countDown();

      // the owner gave the sample away, so can no longer see it; with innodb_snapshot_isolation
      // (MariaDB 11.6+) the locked re-read is refused instead
      ExecutionException refused =
          assertThrows(ExecutionException.class, () -> second.get(60, TimeUnit.SECONDS));
      assertTrue(
          refused.getCause() instanceof NotFoundException || hasSqlErrorCode(refused, 1020),
          () -> "unexpected refusal: " + refused.getCause());
      assertEquals(
          outsider.getUsername(),
          sampleApiMgr.getApiSampleById(sampleId, outsider).getOwner().getUsername());
    } finally {
      releaseSecond.countDown();
      pool.shutdownNow();
    }
  }

  @Test
  public void requestRaisedDuringATransferIsNotLeftWithThePreviousOwner() throws Exception {
    User newOwner = createAndSaveUser(getRandomAlphabeticString("cNew"));
    initUsers(newOwner);
    CountDownLatch createPaused = new CountDownLatch(1);
    CountDownLatch releaseCreate = new CountDownLatch(1);
    SampleRequestApiManager pausingManager =
        managerPausingAfterItsLock(createPaused, releaseCreate);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // the new request has read the sample, and still holds its lock, but is not saved yet
      Future<Long> create =
          pool.submit(
              () -> {
                ApiSampleRequestPost post = new ApiSampleRequestPost();
                post.setSampleGlobalId(sampleGlobalId);
                post.setNote("raised while the sample is being transferred");
                return new TransactionTemplate(getTxMger())
                    .execute(tx -> pausingManager.createRequest(post, requester).getId());
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
      // the request's share lock on the sample holds the transfer until the request is saved
      assertThrows(
          TimeoutException.class,
          () -> transfer.get(2, TimeUnit.SECONDS),
          "the transfer must wait for the request being raised");
      releaseCreate.countDown();
      Long raisedId = create.get(30, TimeUnit.SECONDS);

      boolean transferred = true;
      try {
        transfer.get(60, TimeUnit.SECONDS);
      } catch (ExecutionException e) {
        // with innodb_snapshot_isolation (MariaDB 11.6+) the transfer is refused instead
        assertTrue(hasSqlErrorCode(e, 1020), () -> "unexpected transfer failure: " + e.getCause());
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
   * A manager whose createRequest holds at its first permission check, which comes after it
   * share-locks the sample and before it saves the request.
   */
  private SampleRequestApiManager managerPausingAfterItsLock(
      CountDownLatch paused, CountDownLatch release) {
    SystemPropertyPermissionManager pausing =
        Mockito.mock(
            SystemPropertyPermissionManager.class,
            AdditionalAnswers.delegatesTo(systemPropertyPermissions));
    Mockito.doAnswer(
            call -> {
              if (paused.getCount() > 0) {
                paused.countDown();
                awaitQuietly(release);
              }
              return systemPropertyPermissions.isPropertyAllowed(
                  call.<User>getArgument(0), call.<SystemPropertyName>getArgument(1));
            })
        .when(pausing)
        .isPropertyAllowed(Mockito.any(User.class), Mockito.any(SystemPropertyName.class));
    return new SampleRequestApiManagerImpl(
        sampleRequestDao,
        sampleDao,
        sampleApiMgr,
        invPermissions,
        messages,
        pausing,
        userDao,
        publisher);
  }

  private static boolean hasSqlErrorCode(Throwable thrown, int code) {
    for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
      if (cause instanceof SQLException sql && sql.getErrorCode() == code) {
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

  private Callable<String> transition(
      CountDownLatch startLine, SampleRequestStatus target, String reason) {
    ApiSampleRequestStatusPut put = new ApiSampleRequestStatusPut();
    put.setStatus(target);
    put.setReason(reason);
    return attempt(startLine, () -> sampleRequestApiMgr.updateStatus(requestId, put, owner));
  }

  /** Returns WON, or the simple name of whatever refused the change. */
  private static Callable<String> attempt(CountDownLatch startLine, Runnable change) {
    return () -> {
      startLine.await();
      try {
        change.run();
        return WON;
      } catch (Exception refused) {
        return refused.getClass().getSimpleName();
      }
    };
  }
}
