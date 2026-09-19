package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleRequest;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestStatusPut;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.model.User;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Two clients transitioning the same request at once must not both succeed. */
public class SampleRequestConcurrencyIT extends RealTransactionSpringTestBase {

  private @Autowired SampleRequestApiManager sampleRequestApiMgr;
  private @Autowired SystemPropertyManager systemPropertyMgr;

  private String originalSampleRequestsAvailable;
  private User owner;
  private User requester;
  private Long requestId;

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
  public void concurrentApproveAndRejectLeaveExactlyOneWinner() throws Exception {
    CountDownLatch startLine = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<String> approve =
          pool.submit(transition(startLine, SampleRequestStatus.APPROVED, null));
      Future<String> reject =
          pool.submit(transition(startLine, SampleRequestStatus.REJECTED, "no material left"));

      startLine.countDown();
      List<String> outcomes =
          List.of(approve.get(30, TimeUnit.SECONDS), reject.get(30, TimeUnit.SECONDS));

      assertEquals(
          1,
          outcomes.stream().filter(WON::equals).count(),
          "exactly one of two concurrent transitions must be applied");

      // the loser must be refused by the row lock and the normal transition rule, not by the
      // unique constraint backstop, which would mean the lock is not doing its job
      assertEquals(
          ApiRuntimeException.class.getSimpleName(),
          outcomes.stream().filter(o -> !WON.equals(o)).findFirst().orElseThrow(),
          "the loser should be refused by the transition rule, not by a constraint violation");

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
