package com.researchspace.model.audittrail;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.researchspace.core.util.BasicPaginationCriteria;
import com.researchspace.model.core.Person;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AuditTrailHistoricalEventVisitorTest {

  private static Person subject;
  AuditTrailHistoricalEventVisitor visitor;
  private AuditTrailTestObject auditTestObject;

  static HistoricData createExampleHistoryData(Person subject) {
    AuditData data = getAuditDataForTestObject();
    return new HistoricData(AuditDomain.FORM, AuditAction.CREATE, subject.getFullName(), data,
        subject.getUniqueName());
  }

  private static AuditData getAuditDataForTestObject() {
    AuditData data = new AuditData();
    data.put("property", AuditTrailTestObject.string);
    data.put("id", AuditTrailTestObject.id + "");
    return data;
  }

  @BeforeEach
  public void setUp() {
    visitor = new AuditTrailHistoricalEventVisitor();
    auditTestObject = new AuditTrailTestObject();
    subject = TestFactory.createAnyUser("any");
  }

  @Test
  public void testVisitAuditCreateEventWithSubclass() {
    HistoricData hData = createExampleHistoryData(subject);
    auditTestObject = new AuditTrailTestObjectSubClass();
    GenericEvent event = new GenericEvent(subject, auditTestObject, AuditAction.CREATE);
    event.accept(visitor);
    Assertions.assertEquals(hData, visitor.getHistoryData().get(0));
  }

  @Test
  public void testDelegatingToCollectionSubclass() {
    DelegatingToCollectionSubclass delegator = new DelegatingToCollectionSubclass();
    final int NUM_OBJECTS = 10;
    for (int i = 0; i < NUM_OBJECTS; i++) {
      delegator.getExampleCollection().add(new AuditTrailTestObject());
    }
    GenericEvent event = new GenericEvent(subject, delegator, AuditAction.CREATE);
    event.accept(visitor);
    Assertions.assertEquals(NUM_OBJECTS, visitor.getHistoryData().size());
  }

  @Test
  public void testDelegatingToCollectionSubclassThrowsISEIfIsNotActuallyACollection() {
    BadDelegatingToCollectionSubclass delegator = new BadDelegatingToCollectionSubclass();
    delegator.exampleCollection = new Object();
    GenericEvent event = new GenericEvent(subject, delegator, AuditAction.CREATE);
    assertThrows(IllegalStateException.class, () -> event.accept(visitor));
  }

  @Test
  public void testSubClassWithOverridingAuditDomainAnnotationOverridesBaseClass() {
    AuditData data = getAuditDataForTestObject();
    auditTestObject = new AuditTrailTestObjectSubClassWithOverridingDomain();
    HistoricData expectedhData = new HistoricData(AuditDomain.COMMUNITY, AuditAction.CREATE,
        subject.getFullName(), data, subject.getUniqueName());
    GenericEvent event = new GenericEvent(subject, auditTestObject, AuditAction.CREATE);
    event.accept(visitor);
    Assertions.assertEquals(expectedhData, visitor.getHistoryData().get(0));

  }

  @Test
  public void testVisitAuditCreateEventWithClassNotObject() {
    HistoricData expectedhData = new HistoricData(AuditDomain.FORM, AuditAction.CREATE,
        subject.getFullName(), null, subject.getUniqueName());
    // data  field will be null, as it is a class argument.
    GenericEvent event = new GenericEvent(subject, auditTestObject.getClass(), AuditAction.CREATE);
    event.accept(visitor);
    Assertions.assertEquals(expectedhData, visitor.getHistoryData().get(0));
  }

  @Test
  public void testVisitAuditCreateEvent() {
    HistoricData hData = createExampleHistoryData(subject);

    GenericEvent event = new GenericEvent(subject, auditTestObject, AuditAction.CREATE);
    event.accept(visitor);
    Assertions.assertEquals(hData, visitor.getHistoryData().get(0));
  }

  @Test
  public void testVisitAuditCreateEventWithAnnotationUsesDefaultData() {
    HistoricData hData = new HistoricData(AuditDomain.UNKNOWN, AuditAction.CREATE,
        subject.getFullName(), new AuditData(), subject.getUniqueName());

    NotAnnotatedForAuditing object = new NotAnnotatedForAuditing();
    GenericEvent event = new GenericEvent(subject, object, AuditAction.CREATE);
    event.accept(visitor);
    Assertions.assertEquals(hData, visitor.getHistoryData().get(0));
    // and works when declared as object, as well
    event = new GenericEvent(subject, object, AuditAction.CREATE);
    event.accept(visitor);
    Assertions.assertEquals(hData, visitor.getHistoryData().get(0));
  }

  @Test
  public void searchEvent() {
    AuditSearchEvent srchEvent = new AuditSearchEvent(subject, auditTestObject,
        BasicPaginationCriteria.createDefaultForClass(Object.class));
    srchEvent.accept(visitor);
    HistoricData EXPECTEDlogged = new HistoricData(AuditDomain.FORM, AuditAction.SEARCH,
        subject.getFullName(), new AuditData(), subject.getUniqueName());
    Assertions.assertEquals(EXPECTEDlogged, visitor.getHistoryData().get(0));
  }

  @Test
  public void testDuplicateEvent() {
    Map<Object, Object> someResult = new HashMap<>();
    someResult.put("from", new AuditTrailTestObject());
    DuplicateAuditEvent copyEvent = new DuplicateAuditEvent(subject, someResult, auditTestObject,
        "originalFile", "originalFile_Copy");
    copyEvent.accept(visitor);
    Assertions.assertEquals("from: \"originalFile\" to: \"originalFile_Copy\"",
        visitor.getHistoryData().get(0).getDescription());
    Assertions.assertEquals("originalFile",
        visitor.getHistoryData().get(0).getData().getData().get("name"));
  }

  @Test
  public void testAuditOfComplexProperty() {
    Object complex = new AuditTrailTestObject();
    GenericEvent event = new GenericEvent(subject, complex, AuditAction.CREATE);
    event.accept(visitor);
    String json = visitor.getHistoryData().get(0).getData().toJson();
    Assertions.assertTrue(json.contains("\"id\":2"));
  }

  class AuditTrailTestObjectSubClass extends AuditTrailTestObject {

  }

  @AuditTrailData(auditDomain = AuditDomain.COMMUNITY)
  class AuditTrailTestObjectSubClassWithOverridingDomain extends AuditTrailTestObject {

  }

  @AuditTrailData(auditDomain = AuditDomain.COMMUNITY, delegateToCollection = "exampleCollection")
  public class DelegatingToCollectionSubclass {

    private final List<AuditTrailTestObject> exampleCollection = new ArrayList<>();

    public List<AuditTrailTestObject> getExampleCollection() {
      return exampleCollection;
    }
  }

  @AuditTrailData(auditDomain = AuditDomain.COMMUNITY, delegateToCollection = "exampleCollection")
  class BadDelegatingToCollectionSubclass {

    Object exampleCollection = new Object();

    // this is not actually a collection!!! So will not work!
    public Object getExampleCollection() {
      return exampleCollection;
    }
  }

  class NotAnnotatedForAuditing {

  }
}
