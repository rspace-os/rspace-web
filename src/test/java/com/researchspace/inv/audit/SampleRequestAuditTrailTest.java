package com.researchspace.inv.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.events.SampleRequestStatusEvent;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestStatus;
import com.researchspace.testutils.TestFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SampleRequestAuditTrailTest {

  @Captor ArgumentCaptor<GenericEvent> loggedEvent;

  @Mock AuditTrailService auditTrail;
  @InjectMocks SampleRequestAuditTrail listener;

  User owner = TestFactory.createAnyUser("owner");
  User requester = TestFactory.createAnyUser("requester");

  @Test
  public void loggedEventCarriesTheRequestAndTheActor() {
    SampleRequest request = requestInStatus(SampleRequestStatus.PENDING, requester);

    listener.sampleRequestStatusRecorded(new SampleRequestStatusEvent(request, requester));

    verify(auditTrail).notify(loggedEvent.capture());
    assertEquals(request, loggedEvent.getValue().getAuditedObject());
    assertEquals(requester, loggedEvent.getValue().getSubject());
  }

  @ParameterizedTest
  @CsvSource({
    "PENDING, REQUEST_SENT",
    "APPROVED, REQUEST_APPROVED",
    "REJECTED, REQUEST_REJECTED",
    "CANCELLED, REQUEST_CANCELLED",
    "FULFILLED, REQUEST_FULFILLED"
  })
  public void eachStatusIsLoggedAsItsOwnAction(
      SampleRequestStatus status, AuditAction expectedAction) {
    SampleRequest request = requestInStatus(status, owner);

    listener.sampleRequestStatusRecorded(new SampleRequestStatusEvent(request, owner));

    verify(auditTrail).notify(loggedEvent.capture());
    assertEquals(expectedAction, loggedEvent.getValue().getAuditAction());
  }

  private SampleRequest requestInStatus(SampleRequestStatus status, User actor) {
    Sample sample = TestFactory.createBasicSampleOutsideContainer(owner);
    SampleRequest request = new SampleRequest(sample, requester, "need 2ml");
    request.recordStatus(actor, status, null);
    return request;
  }
}
