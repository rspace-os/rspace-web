package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiContainer;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.api.v1.model.ApiSubSample;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.service.AuditManager;
import com.researchspace.testutils.TestFactory;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure unit tests for the user-version lookup methods of {@link InventoryAuditApiManagerImpl}, with
 * a mocked {@link AuditManager}. Envers-backed behaviour is covered by the IT.
 */
public class InventoryAuditApiManagerVersionTest {

  private final AuditManager auditManager = Mockito.mock(AuditManager.class);
  private final InventoryAuditApiManagerImpl mgr = new InventoryAuditApiManagerImpl();
  private final User user = TestFactory.createAnyUser("versionLookupUser");

  @BeforeEach
  public void setUp() {
    mgr.auditManager = auditManager;
  }

  @Test
  public void sampleVersionLookupReturnsLiveRecordForCurrentVersion() {
    Sample sample = TestFactory.createBasicSampleOutsideContainer(user);
    sample.setId(42L);
    sample.increaseVersion(); // current version 2

    ApiSample result = mgr.getApiSampleVersion(sample, 2L);

    assertNotNull(result);
    assertFalse(result.isHistoricalVersion());
    assertEquals(2L, result.getVersion());
    // current version is served from the live entity, no audit lookup
    verifyNoInteractions(auditManager);
  }

  @Test
  public void sampleVersionLookupResolvesNewestRevisionForRequestedVersion() {
    Sample current = sampleWithVersion(42L, 3L);
    Sample v2 = sampleWithVersion(42L, 2L);
    // the audit layer resolves a user version to the newest revision carrying it
    when(auditManager.getRevisionNumberForInventoryRecordVersion(current.getClass(), 42L, 2L))
        .thenReturn(380L);
    when(auditManager.getObjectForRevision(Sample.class, 42L, 380L))
        .thenReturn(new AuditedEntity<>(v2, 380L));

    ApiSample result = mgr.getApiSampleVersion(current, 2L);

    assertNotNull(result);
    assertTrue(result.isHistoricalVersion());
    assertEquals(2L, result.getVersion());
    assertEquals(380L, result.getRevisionId());
    assertEquals("SA42v2", result.getGlobalId());
  }

  @Test
  public void sampleVersionLookupSurvivesNullCurrentVersion() {
    // legacy rows can hold a null version column; the lookup must not NPE on them
    Sample current = sampleWithVersion(42L, 1L);
    ReflectionTestUtils.setField(current, "version", null);
    Sample v1 = sampleWithVersion(42L, 1L);
    when(auditManager.getRevisionNumberForInventoryRecordVersion(current.getClass(), 42L, 1L))
        .thenReturn(100L);
    when(auditManager.getObjectForRevision(Sample.class, 42L, 100L))
        .thenReturn(new AuditedEntity<>(v1, 100L));

    ApiSample result = mgr.getApiSampleVersion(current, 1L);

    assertNotNull(result);
    assertTrue(result.isHistoricalVersion());
  }

  @Test
  public void sampleVersionLookupReturnsNullForUnknownVersion() {
    Sample current = sampleWithVersion(42L, 3L);
    when(auditManager.getRevisionNumberForInventoryRecordVersion(current.getClass(), 42L, 99L))
        .thenReturn(null);

    assertNull(mgr.getApiSampleVersion(current, 99L));
  }

  @Test
  public void subSampleVersionLookupReturnsLiveRecordForCurrentVersion() {
    SubSample current = subSampleWithVersion(9L, 2L);

    ApiSubSample result = mgr.getApiSubSampleVersion(current, 2L);

    assertNotNull(result);
    assertFalse(result.isHistoricalVersion());
    assertEquals(2L, result.getVersion());
    verifyNoInteractions(auditManager);
  }

  @Test
  public void instrumentVersionLookupReturnsLiveRecordForCurrentVersion() {
    Instrument current = instrumentWithVersion(7L, 1L);

    ApiInstrument result = mgr.getApiInstrumentVersion(current, 1L);

    assertNotNull(result);
    assertFalse(result.isHistoricalVersion());
    verifyNoInteractions(auditManager);
  }

  @Test
  public void subSampleVersionLookupResolvesHistoricalSnapshot() {
    SubSample current = subSampleWithVersion(9L, 2L);
    SubSample v1 = subSampleWithVersion(9L, 1L);
    when(auditManager.getRevisionNumberForInventoryRecordVersion(current.getClass(), 9L, 1L))
        .thenReturn(100L);
    when(auditManager.getObjectForRevision(SubSample.class, 9L, 100L))
        .thenReturn(new AuditedEntity<>(v1, 100L));

    ApiSubSample result = mgr.getApiSubSampleVersion(current, 1L);

    assertNotNull(result);
    assertTrue(result.isHistoricalVersion());
    assertEquals(1L, result.getVersion());
    assertEquals(100L, result.getRevisionId());
    assertEquals("SS9v1", result.getGlobalId());
  }

  @Test
  public void containerRevisionExcludesLiveLocations() {
    Container v1 = containerWithVersion(10L, 1L);
    when(auditManager.getObjectForRevision(Container.class, 10L, 100L))
        .thenReturn(new AuditedEntity<>(v1, 100L));

    ApiContainer result = mgr.getApiContainerRevision(10L, 100L);

    assertNotNull(result);
    assertEquals(100L, result.getRevisionId());
    assertEquals("IC10v1", result.getGlobalId());
    // locations are @NotAudited: a snapshot must not serialize present-day contents
    assertNull(result.getLocations());
  }

  @Test
  public void containerVersionLookupResolvesHistoricalSnapshot() {
    Container current = containerWithVersion(10L, 3L);
    Container v2 = containerWithVersion(10L, 2L);
    when(auditManager.getRevisionNumberForInventoryRecordVersion(current.getClass(), 10L, 2L))
        .thenReturn(250L);
    when(auditManager.getObjectForRevision(Container.class, 10L, 250L))
        .thenReturn(new AuditedEntity<>(v2, 250L));

    ApiContainer result = mgr.getApiContainerVersion(current, 2L);

    assertNotNull(result);
    assertTrue(result.isHistoricalVersion());
    assertEquals(2L, result.getVersion());
    assertEquals(250L, result.getRevisionId());
    assertEquals("IC10v2", result.getGlobalId());
    assertNull(result.getLocations());
  }

  @Test
  public void containerVersionLookupReturnsLiveRecordForCurrentVersion() {
    Container current = containerWithVersion(10L, 1L);

    ApiContainer result = mgr.getApiContainerVersion(current, 1L);

    assertNotNull(result);
    assertFalse(result.isHistoricalVersion());
    verifyNoInteractions(auditManager);
  }

  @Test
  public void instrumentVersionLookupResolvesHistoricalSnapshot() {
    Instrument current = instrumentWithVersion(7L, 2L);
    Instrument v1 = instrumentWithVersion(7L, 1L);
    when(auditManager.getRevisionNumberForInventoryRecordVersion(current.getClass(), 7L, 1L))
        .thenReturn(100L);
    when(auditManager.getObjectForRevision(Instrument.class, 7L, 100L))
        .thenReturn(new AuditedEntity<>(v1, 100L));

    ApiInstrument result = mgr.getApiInstrumentVersion(current, 1L);

    assertNotNull(result);
    assertTrue(result.isHistoricalVersion());
    assertEquals(100L, result.getRevisionId());
    assertEquals("IN7v1", result.getGlobalId());
  }

  private Sample sampleWithVersion(Long id, Long version) {
    Sample sample = TestFactory.createBasicSampleOutsideContainer(user);
    sample.setId(id);
    bumpToVersion(sample, version);
    return sample;
  }

  private SubSample subSampleWithVersion(Long id, Long version) {
    SubSample subSample =
        TestFactory.createBasicSampleOutsideContainer(user).getSubSamples().get(0);
    subSample.setId(id);
    bumpToVersion(subSample, version);
    return subSample;
  }

  private Container containerWithVersion(Long id, Long version) {
    try {
      Container container = TestFactory.createListContainer(user);
      container.setId(id);
      bumpToVersion(container, version);
      return container;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private Instrument instrumentWithVersion(Long id, Long version) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    instrument.setName("test instrument");
    instrument.setOwner(user);
    bumpToVersion(instrument, version);
    return instrument;
  }

  private void bumpToVersion(InventoryRecord record, Long version) {
    for (long v = 1; v < version; v++) {
      record.increaseVersion();
    }
  }
}
