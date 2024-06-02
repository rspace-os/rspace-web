package com.researchspace.service.audit.search;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.core.util.TransformerUtils;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditData;
import com.researchspace.model.audittrail.AuditDomain;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.audittrail.HistoricData;
import com.researchspace.model.audittrail.HistoricalEvent;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.record.TestFactory;
import com.researchspace.model.views.RSpaceDocView;
import com.researchspace.service.RecordManager;
import java.time.Instant;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class UpdateRecordNamePostProcessorTest {

  @Rule public MockitoRule mockito = MockitoJUnit.rule();
  @Mock RecordManager recMgr;
  UpdateRecordNamePostProcessor processor;

  User user;

  @Before
  public void setUp() throws Exception {
    processor = new UpdateRecordNamePostProcessor();
    processor.setRecordManager(recMgr);
    this.user = TestFactory.createAnyUser("any");
  }

  @After
  public void tearDown() throws Exception {}

  @Test
  public void testProcessModifiesCreateAudit() {
    final String newName = "newName";
    RSpaceDocView any = createARecordWithName(newName);
    final List<RSpaceDocView> records = TransformerUtils.toList(any);
    final ISearchResults<AuditTrailSearchResult> searchResult =
        createASearchHit(any, AuditAction.CREATE);
    when(recMgr.getAllFrom(Mockito.anySet())).thenReturn(records);
    processor.process(searchResult);
    assertEquals(newName, getModifiedLoggedName(searchResult));
  }

  @Test
  public void testProcessDoesNotModifyCreateAudit() {
    final String newName = "newName";
    RSpaceDocView any = createARecordWithName(newName);
    final ISearchResults<AuditTrailSearchResult> searchResult =
        createASearchHit(any, AuditAction.DUPLICATE);

    processor.process(searchResult);
    verify(recMgr, never()).getAllFrom(Mockito.anySet());
    // this is from the audit logs
    assertEquals("untitled", getModifiedLoggedName(searchResult));
  }

  private RSpaceDocView createARecordWithName(final String newName) {
    RSpaceDocView any = new RSpaceDocView();
    any.setName(newName);
    any.setId(1234L);
    return any;
  }

  private Object getModifiedLoggedName(final ISearchResults<AuditTrailSearchResult> searchResult) {
    return searchResult.getFirstResult().getEvent().getData().getData().get("name");
  }

  private ISearchResults<AuditTrailSearchResult> createASearchHit(
      RSpaceDocView audited, AuditAction action) {

    HistoricalEvent event = new GenericEvent(user, audited, action);
    AuditData data = new AuditData();
    data.getData().put("id", new GlobalIdentifier(GlobalIdPrefix.SD, audited.getId()).toString());
    data.getData().put("name", "untitled");
    HistoricData hd = new HistoricData(AuditDomain.RECORD, event, data);
    AuditTrailSearchResult res = new AuditTrailSearchResult(hd, Instant.now().toEpochMilli());
    return new SearchResultsImpl<AuditTrailSearchResult>(
        TransformerUtils.toList(res),
        PaginationCriteria.createDefaultForClass(AuditTrailSearchResult.class),
        1);
  }
}
