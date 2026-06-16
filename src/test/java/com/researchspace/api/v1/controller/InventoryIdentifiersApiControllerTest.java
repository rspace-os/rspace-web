package com.researchspace.api.v1.controller;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.controller.InventoryIdentifiersApiController.ApiInventoryIdentifierPost;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.api.v1.model.ApiSample;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.service.ApiAvailabilityHandler;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.SampleApiManager;
import com.researchspace.testutils.TestFactory;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import javax.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectRetrievalFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InventoryIdentifiersApiControllerTest {

  @Mock private InventoryIdentifierApiManager mockIdentifierMgr;
  @Mock private ApiAvailabilityHandler mockApiHandler;
  @Mock private InstrumentEntityApiManager mockInstrumentApiMgr;
  @Mock private SampleApiManager mockSampleApiMgr;
  @Mock private DataCiteConnector mockDataCiteConnector;
  @Mock private MessageSourceUtils mockMessages;

  private InventoryIdentifiersApiController controller;
  private User user;

  @BeforeEach
  void setUp() {
    controller = new InventoryIdentifiersApiController();
    ReflectionTestUtils.setField(controller, "identifierMgr", mockIdentifierMgr);
    ReflectionTestUtils.setField(controller, "apiHandler", mockApiHandler);
    ReflectionTestUtils.setField(controller, "instrumentApiMgr", mockInstrumentApiMgr);
    ReflectionTestUtils.setField(controller, "sampleApiMgr", mockSampleApiMgr);
    ReflectionTestUtils.setField(controller, "dataCiteConnector", mockDataCiteConnector);
    ReflectionTestUtils.setField(controller, "messages", mockMessages);
    user = TestFactory.createAnyUser("any");
  }

  @Test
  void testIgsnConnectionTestsIgsnClient() {
    when(mockDataCiteConnector.testDataCiteConnection(InventorySettingType.IGSN)).thenReturn(true);

    assertTrue(controller.testIgsnConnection(user));
    verify(mockDataCiteConnector).testDataCiteConnection(InventorySettingType.IGSN);
  }

  @Test
  void testPdinstConnectionTestsPdinstClient() {
    when(mockDataCiteConnector.testDataCiteConnection(InventorySettingType.PDINST))
        .thenReturn(true);

    assertTrue(controller.testPdinstConnection(user));
    verify(mockDataCiteConnector).testDataCiteConnection(InventorySettingType.PDINST);
  }

  private ApiInventoryRecordInfo recordWithOneIdentifier() {
    ApiSample sample = new ApiSample();
    sample.getIdentifiers().add(new ApiInventoryDOI());
    return sample;
  }

  private InventoryRecord recordWithOid(String globalId) {
    InventoryRecord record = mock(InventoryRecord.class);
    when(record.getOid()).thenReturn(new GlobalIdentifier(globalId));
    return record;
  }

  @Test
  void registerForInstrumentGlobalIdGatesOnPdinst() {
    when(mockIdentifierMgr.registerNewIdentifier(any(), eq(user)))
        .thenReturn(recordWithOneIdentifier());
    ApiInventoryIdentifierPost registerPost = new ApiInventoryIdentifierPost();
    registerPost.setParentGlobalId("IN12345");

    controller.registerNewIdentifier(registerPost, user);

    verify(mockApiHandler)
        .assertInventoryAndIdentifierTypeEnabled(user, InventorySettingType.PDINST);
    verify(mockApiHandler, never())
        .assertInventoryAndIdentifierTypeEnabled(user, InventorySettingType.IGSN);
  }

  @Test
  void registerForSampleGlobalIdGatesOnIgsn() {
    when(mockIdentifierMgr.registerNewIdentifier(any(), eq(user)))
        .thenReturn(recordWithOneIdentifier());
    ApiInventoryIdentifierPost registerPost = new ApiInventoryIdentifierPost();
    registerPost.setParentGlobalId("SA12345");

    controller.registerNewIdentifier(registerPost, user);

    verify(mockApiHandler).assertInventoryAndIdentifierTypeEnabled(user, InventorySettingType.IGSN);
  }

  @Test
  void publishGatesByIdentifierType() {
    ApiInventoryDOI pdinstDoi = new ApiInventoryDOI();
    pdinstDoi.setDoiType("PDINST_DATACITE");
    InventoryRecord instrumentRecord = recordWithOid("IN12345");
    when(mockIdentifierMgr.getIdentifierById(5L)).thenReturn(pdinstDoi);
    when(mockIdentifierMgr.getInventoryRecordByIdentifierId(5L)).thenReturn(instrumentRecord);
    when(mockIdentifierMgr.publishIdentifier(any(), eq(user)))
        .thenReturn(recordWithOneIdentifier());

    controller.publishIdentifier(5L, user);

    verify(mockApiHandler)
        .assertInventoryAndIdentifierTypeEnabled(user, InventorySettingType.PDINST);
  }

  @Test
  void retractGatesByIdentifierType() {
    ApiInventoryDOI igsnDoi = new ApiInventoryDOI();
    igsnDoi.setDoiType("IGSN_DATACITE");
    InventoryRecord sampleRecord = recordWithOid("SA12345");
    when(mockIdentifierMgr.getIdentifierById(6L)).thenReturn(igsnDoi);
    when(mockIdentifierMgr.getInventoryRecordByIdentifierId(6L)).thenReturn(sampleRecord);
    when(mockIdentifierMgr.retractIdentifier(any(), eq(user)))
        .thenReturn(recordWithOneIdentifier());

    controller.retractIdentifier(6L, user);

    verify(mockApiHandler).assertInventoryAndIdentifierTypeEnabled(user, InventorySettingType.IGSN);
  }

  @Test
  void deleteGatesByIdentifierType() {
    ApiInventoryDOI pdinstDraftDoi = new ApiInventoryDOI();
    pdinstDraftDoi.setDoiType("PDINST_DATACITE");
    pdinstDraftDoi.setState("draft");
    when(mockIdentifierMgr.getIdentifierById(7L)).thenReturn(pdinstDraftDoi);
    when(mockIdentifierMgr.deleteUnassociatedIdentifier(pdinstDraftDoi, user)).thenReturn(true);

    controller.deleteIdentifier(7L, user);

    verify(mockApiHandler)
        .assertInventoryAndIdentifierTypeEnabled(user, InventorySettingType.PDINST);
  }

  @Test
  void deleteTranslatesMissingIdentifierTo404() {
    // getIdentifierById -> GenericDaoHibernate#get throws (never returns null) for an unknown id
    when(mockIdentifierMgr.getIdentifierById(404L))
        .thenThrow(new ObjectRetrievalFailureException(DigitalObjectIdentifier.class, 404L));

    assertThrows(NotFoundException.class, () -> controller.deleteIdentifier(404L, user));
  }

  @Test
  void publishTranslatesMissingIdentifierTo404() {
    when(mockIdentifierMgr.getIdentifierById(404L))
        .thenThrow(new ObjectRetrievalFailureException(DigitalObjectIdentifier.class, 404L));

    assertThrows(NotFoundException.class, () -> controller.publishIdentifier(404L, user));
  }
}
