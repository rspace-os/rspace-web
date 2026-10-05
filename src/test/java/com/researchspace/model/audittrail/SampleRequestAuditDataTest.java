package com.researchspace.model.audittrail;

import static com.researchspace.testutils.TestFactory.createAnyUser;
import static com.researchspace.testutils.TestFactory.createBasicSampleOutsideContainer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleRequest;
import com.researchspace.model.inventory.SampleRequestStatus;
import org.junit.jupiter.api.Test;

/** What a sample request contributes to an audit entry. */
public class SampleRequestAuditDataTest {

  private final User owner = createAnyUser("owner");
  private final User requester = createAnyUser("requester");

  @Test
  public void requestIsAuditedUnderTheRequestDomain() {
    assertEquals(AuditDomain.REQUEST, historicDataFor(auditedRequest()).getDomain());
  }

  @Test
  public void auditEntryIsFindableByTheRequestedSamplesGlobalId() {
    SampleRequest request = auditedRequest();

    String json = historicDataFor(request).getData().toJson();

    // the oid filter is a substring match on a quoted value, see BasicLogQuerySearcher.idMatches
    assertTrue(
        json.contains("\"" + request.getSample().getGlobalIdentifier() + "\""),
        "sample global id missing from " + json);
  }

  @Test
  public void auditEntrySaysWhoAskedForWhatAndInWhichState() {
    String json = historicDataFor(auditedRequest()).getData().toJson();

    assertTrue(json.contains(requester.getUsername()), "requester missing from " + json);
    assertTrue(json.contains(SampleRequestStatus.PENDING.name()), "status missing from " + json);
    assertTrue(json.contains("\"requestId\""), "request id missing from " + json);
  }

  private SampleRequest auditedRequest() {
    Sample sample = createBasicSampleOutsideContainer(owner);
    sample.setId(7L);
    SampleRequest request = new SampleRequest(sample, requester, "need 2ml");
    request.setId(23L);
    request.recordStatus(requester, SampleRequestStatus.PENDING, null);
    return request;
  }

  private HistoricData historicDataFor(SampleRequest request) {
    AuditTrailHistoricalEventVisitor visitor = new AuditTrailHistoricalEventVisitor();
    new GenericEvent(requester, request, AuditAction.REQUEST_SENT).accept(visitor);
    return visitor.getHistoryData().get(0);
  }
}
