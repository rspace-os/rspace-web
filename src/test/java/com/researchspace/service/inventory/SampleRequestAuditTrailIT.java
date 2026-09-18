package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSampleRequestPost;
import com.researchspace.api.v1.model.ApiSampleRequestStatusPut;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.inv.audit.SampleRequestAuditTrail;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.model.preference.HierarchicalPermission;
import com.researchspace.service.SystemPropertyManager;
import com.researchspace.service.SystemPropertyName;
import com.researchspace.testutils.RealTransactionSpringTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The audit listener fires only after commit, so only a committing test proves it is wired at all.
 */
public class SampleRequestAuditTrailIT extends RealTransactionSpringTestBase {

  private @Autowired SampleRequestApiManager sampleRequestApiMgr;
  private @Autowired SystemPropertyManager systemPropertyMgr;
  private @Autowired SampleRequestAuditTrail auditTrailListener;

  private final AuditTrailService mockAuditer = Mockito.mock(AuditTrailService.class);
  private AuditTrailService realAuditer;
  private String originalSampleRequestsAvailable;
  private User owner;
  private User requester;
  private ApiSampleWithFullSubSamples sample;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    owner =
        createAndSaveUser(getRandomAlphabeticString("aOwner"), com.researchspace.Constants.PI_ROLE);
    requester = createAndSaveUser(getRandomAlphabeticString("aReq"));
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
    sample = createBasicSampleForUser(owner);
    ApiSample requestable = new ApiSample();
    requestable.setId(sample.getId());
    requestable.setRequestable(true);
    sampleApiMgr.updateApiSample(requestable, owner);

    realAuditer = (AuditTrailService) ReflectionTestUtils.getField(auditTrailListener, "auditer");
    ReflectionTestUtils.setField(auditTrailListener, "auditer", mockAuditer);
  }

  @AfterEach
  public void tearDown() throws Exception {
    if (realAuditer != null) {
      ReflectionTestUtils.setField(auditTrailListener, "auditer", realAuditer);
      realAuditer = null;
    }
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
  public void raisingAndApprovingAreAuditedOnceCommitted() {
    logoutAndLoginAs(requester);
    ApiSampleRequestPost post = new ApiSampleRequestPost();
    post.setSampleGlobalId(sample.getGlobalId());
    post.setNote("needed for the assay");
    Long requestId = sampleRequestApiMgr.createRequest(post, requester).getId();

    ArgumentCaptor<GenericEvent> audited = ArgumentCaptor.forClass(GenericEvent.class);
    verify(mockAuditer).notify(audited.capture());
    assertEquals(AuditAction.REQUEST_SENT, audited.getValue().getAuditAction());
    // after commit the entity must still answer the getters the payload is built from
    SampleRequest auditedRequest = (SampleRequest) audited.getValue().getAuditedObject();
    assertEquals(sample.getGlobalId(), auditedRequest.getRequestedSampleGlobalId());
    assertEquals(requester.getUsername(), auditedRequest.getRequesterUsername());

    logoutAndLoginAs(owner);
    ApiSampleRequestStatusPut approval = new ApiSampleRequestStatusPut();
    approval.setStatus(SampleRequestStatus.APPROVED);
    sampleRequestApiMgr.updateStatus(requestId, approval, owner);

    verify(mockAuditer, Mockito.times(2)).notify(audited.capture());
    assertEquals(AuditAction.REQUEST_APPROVED, audited.getValue().getAuditAction());
  }
}
