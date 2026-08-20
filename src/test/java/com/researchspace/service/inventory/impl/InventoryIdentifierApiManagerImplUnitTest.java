package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRequestResponse;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.RspaceToExternalProviderAdapter;
import com.researchspace.webapp.integrations.b2inst.B2instConnectionException;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class InventoryIdentifierApiManagerImplUnitTest {

  /**
   * A {@link com.researchspace.model.inventory.DigitalObjectIdentifier} persisted before the type
   * column was populated loads with a null type. settingTypeFor must not NPE on the {@code switch}:
   * such legacy identifiers predate PIDINST and default to IGSN.
   */
  @Test
  void settingTypeForNullTypeDefaultsToIgsn() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    Method settingTypeFor =
        InventoryIdentifierApiManagerImpl.class.getDeclaredMethod(
            "settingTypeFor", IdentifierType.class);
    settingTypeFor.setAccessible(true);

    Object result = settingTypeFor.invoke(mgr, (IdentifierType) null);

    assertEquals(InventorySettingType.IGSN, result);
  }

  @Test
  void settingTypeForB2instMapsToPidinst() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    Method settingTypeFor =
        InventoryIdentifierApiManagerImpl.class.getDeclaredMethod(
            "settingTypeFor", IdentifierType.class);
    settingTypeFor.setAccessible(true);

    Object result = settingTypeFor.invoke(mgr, IdentifierType.PIDINST_B2INST);

    assertEquals(InventorySettingType.PIDINST, result);
  }

  @Test
  void createNewB2instDoiPersistsRidAsPidinstB2instIdentifier() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    RspaceToExternalProviderAdapter adapter = mock(RspaceToExternalProviderAdapter.class);
    IPropertyHolder properties = mock(IPropertyHolder.class);
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    ReflectionTestUtils.setField(mgr, "rspaceToExternalProviderAdapter", adapter);
    ReflectionTestUtils.setField(mgr, "properties", properties);

    InventoryRecord instrument = mock(InventoryRecord.class);
    User user = mock(User.class);
    when(user.getFullName()).thenReturn("Jane Doe");
    when(properties.getCustomerName()).thenReturn("Acme");
    when(adapter.buildB2instDoi(eq(instrument), nullable(String.class)))
        .thenReturn(new B2instDoi());
    B2instDraftRecord draft = new B2instDraftRecord();
    draft.setId("k2j9p-7yh21");
    when(b2instConnector.registerDoi(any(B2instDoi.class))).thenReturn(draft);

    Method createNewB2instDoi =
        InventoryIdentifierApiManagerImpl.class.getDeclaredMethod(
            "createNewB2instDoi", InventoryRecord.class, User.class);
    createNewB2instDoi.setAccessible(true);
    ApiInventoryDOI result = (ApiInventoryDOI) createNewB2instDoi.invoke(mgr, instrument, user);

    assertEquals("k2j9p-7yh21", result.getDoi());
    assertEquals("draft", result.getState());
    assertEquals(IdentifierType.PIDINST_B2INST.name(), result.getDoiType());
    assertEquals("Instrument", result.getResourceType());
  }

  /**
   * The point of RSDEV-1254: the landing page address exists BEFORE the provider call, is built
   * from the DTO's own suffix, and the same suffix stays on the DTO so the entity later adopts it.
   * The URL is payload-only: publicUrl keeps its publish-time semantics (ADR 0006).
   */
  @Test
  void createNewB2instDoiBuildsLandingPageFromGeneratedSuffixBeforeRegistering() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    RspaceToExternalProviderAdapter adapter = mock(RspaceToExternalProviderAdapter.class);
    IPropertyHolder properties = mock(IPropertyHolder.class);
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    ReflectionTestUtils.setField(mgr, "rspaceToExternalProviderAdapter", adapter);
    ReflectionTestUtils.setField(mgr, "properties", properties);

    InventoryRecord instrument = mock(InventoryRecord.class);
    User user = mock(User.class);
    when(user.getFullName()).thenReturn("Jane Doe");
    when(properties.getCustomerName()).thenReturn("Acme");
    when(properties.getServerUrl()).thenReturn("https://rspace.example.com");
    when(adapter.buildB2instDoi(eq(instrument), nullable(String.class)))
        .thenReturn(new B2instDoi());
    B2instDraftRecord draft = new B2instDraftRecord();
    draft.setId("k2j9p-7yh21");
    when(b2instConnector.registerDoi(any(B2instDoi.class))).thenReturn(draft);

    Method createNewB2instDoi =
        InventoryIdentifierApiManagerImpl.class.getDeclaredMethod(
            "createNewB2instDoi", InventoryRecord.class, User.class);
    createNewB2instDoi.setAccessible(true);
    ApiInventoryDOI result = (ApiInventoryDOI) createNewB2instDoi.invoke(mgr, instrument, user);

    assertNotNull(result.getPublicLinkSuffix());
    ArgumentCaptor<String> landingPage = ArgumentCaptor.forClass(String.class);
    verify(adapter).buildB2instDoi(eq(instrument), landingPage.capture());
    assertEquals(
        "https://rspace.example.com/public/inventory/" + result.getPublicLinkSuffix(),
        landingPage.getValue());
    assertNull(
        result.getPublicUrl(), "payload-only: the landing page URL is not persisted as publicUrl");
  }

  /**
   * The connector's message names the operation for the logs and may carry internal detail; only
   * its reason is safe to show a user. The manager must interpolate the reason, so the localized
   * sentence does not end up with an English developer prefix inside it duplicating what it already
   * says.
   *
   * <p>Constructed the way the connector really does, with a distinct message and reason. An
   * earlier version of this test used a reason-less construction and asserted the doubled output as
   * correct, which meant reverting the connector left every test green while users got the old
   * message back.
   */
  @Test
  void publishB2instInterpolatesOnlyTheProviderReasonNotTheDeveloperMessage() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    ReflectionTestUtils.setField(mgr, "messages", messages);

    DigitalObjectIdentifier doi = mock(DigitalObjectIdentifier.class);
    when(doi.getIdentifier()).thenReturn("k2j9p-7yh21");
    String reason = "instrument_type: Missing data for required field.";
    B2instConnectionException original =
        new B2instConnectionException(
            "Error submitting B2INST record k2j9p-7yh21 for community review: " + reason,
            reason,
            null);
    when(b2instConnector.publishDoi("k2j9p-7yh21")).thenThrow(original);

    Method publish =
        InventoryIdentifierApiManagerImpl.class.getDeclaredMethod(
            "createUpdateWithPublishedB2instDoi", DigitalObjectIdentifier.class);
    publish.setAccessible(true);
    InvocationTargetException wrapped =
        assertThrows(InvocationTargetException.class, () -> publish.invoke(mgr, doi));

    Throwable thrown = wrapped.getCause();
    assertTrue(thrown instanceof B2instConnectionException);
    assertEquals(
        "Could not publish the instrument PID in B2INST. instrument_type: Missing data for required"
            + " field.",
        thrown.getMessage());
    assertFalse(
        thrown.getMessage().contains("Error submitting B2INST record"),
        "the connector's developer prefix must not appear inside the localized sentence");
    assertSame(original, thrown.getCause());
  }

  @Test
  void publishB2instMapsSubmissionStatusOntoDoi() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);

    DigitalObjectIdentifier doi = mock(DigitalObjectIdentifier.class);
    when(doi.getIdentifier()).thenReturn("k2j9p-7yh21");
    when(doi.getId()).thenReturn(7L);
    B2instRequestResponse submitted =
        JacksonUtil.fromJson("{\"status\":\"submitted\"}", B2instRequestResponse.class);
    when(b2instConnector.publishDoi("k2j9p-7yh21")).thenReturn(submitted);

    Method publish =
        InventoryIdentifierApiManagerImpl.class.getDeclaredMethod(
            "createUpdateWithPublishedB2instDoi", DigitalObjectIdentifier.class);
    publish.setAccessible(true);
    ApiInventoryDOI result = (ApiInventoryDOI) publish.invoke(mgr, doi);

    assertEquals(7L, result.getId());
    assertEquals("submitted", result.getState());
  }

  @Test
  void deleteRoutesToB2instForB2instIdentifierType() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    DataCiteConnector dataCiteConnector = mock(DataCiteConnector.class);
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    mgr.setDataCiteConnector(dataCiteConnector);

    DigitalObjectIdentifier doi = mock(DigitalObjectIdentifier.class);
    when(doi.getType()).thenReturn(IdentifierType.PIDINST_B2INST);
    when(doi.getIdentifier()).thenReturn("k2j9p-7yh21");
    when(b2instConnector.deleteDoi("k2j9p-7yh21")).thenReturn(true);

    Method deleteFromDatacite =
        InventoryIdentifierApiManagerImpl.class.getDeclaredMethod(
            "deleteFromDatacite", DigitalObjectIdentifier.class);
    deleteFromDatacite.setAccessible(true);
    Object result = deleteFromDatacite.invoke(mgr, doi);

    assertEquals(true, result);
    verify(b2instConnector).deleteDoi("k2j9p-7yh21");
    verify(dataCiteConnector, never()).deleteDoi(anyString(), any());
  }

  @Test
  void registerRoutesToB2instWhenB2instIsTheEnabledPidinstProvider() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    DataCiteConnector dataCiteConnector = mock(DataCiteConnector.class);
    RspaceToExternalProviderAdapter adapter = mock(RspaceToExternalProviderAdapter.class);
    IPropertyHolder properties = mock(IPropertyHolder.class);
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    ReflectionTestUtils.setField(mgr, "rspaceToExternalProviderAdapter", adapter);
    ReflectionTestUtils.setField(mgr, "properties", properties);
    mgr.setDataCiteConnector(dataCiteConnector);

    when(b2instConnector.isConfiguredAndEnabled()).thenReturn(true);
    InventoryRecord instrument = mock(InventoryRecord.class);
    when(instrument.getName()).thenReturn("Microscope X");
    when(instrument.getGlobalIdentifier()).thenReturn("IN1");
    User user = mock(User.class);
    when(user.getFullName()).thenReturn("Jane Doe");
    when(properties.getCustomerName()).thenReturn("Acme");
    when(adapter.buildB2instDoi(eq(instrument), nullable(String.class)))
        .thenReturn(new B2instDoi());
    B2instDraftRecord draft = new B2instDraftRecord();
    draft.setId("k2j9p-7yh21");
    when(b2instConnector.registerDoi(any(B2instDoi.class))).thenReturn(draft);

    Method createUpdateWithNewDoi =
        InventoryIdentifierApiManagerImpl.class.getDeclaredMethod(
            "createUpdateWithNewDoi",
            InventoryRecord.class,
            User.class,
            InventorySettingType.class);
    createUpdateWithNewDoi.setAccessible(true);
    ApiInventoryDOI result =
        (ApiInventoryDOI)
            createUpdateWithNewDoi.invoke(mgr, instrument, user, InventorySettingType.PIDINST);

    assertEquals("k2j9p-7yh21", result.getDoi());
    assertEquals(IdentifierType.PIDINST_B2INST.name(), result.getDoiType());
    verify(b2instConnector).registerDoi(any(B2instDoi.class));
    verify(dataCiteConnector, never()).registerDoi(any(), any());
  }

  @Test
  void deleteUnassociatedIdentifierRejectsNonOwner() {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    DigitalObjectIdentifierDao doiDao = mock(DigitalObjectIdentifierDao.class);
    MessageSourceUtils messages = new MessageSourceUtils(new JsonMessageSource());
    ReflectionTestUtils.setField(mgr, "doiDao", doiDao);
    ReflectionTestUtils.setField(mgr, "messages", messages);

    User owner = new User("owner");
    User attacker = new User("attacker");
    DigitalObjectIdentifier doi = mock(DigitalObjectIdentifier.class);
    when(doi.getOwner()).thenReturn(owner);
    when(doiDao.get(5L)).thenReturn(doi);

    ApiInventoryDOI apiDoi = mock(ApiInventoryDOI.class);
    when(apiDoi.getId()).thenReturn(5L);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> mgr.deleteUnassociatedIdentifier(apiDoi, attacker));

    assertEquals("You can only delete an identifier that you own.", exception.getMessage());
    verify(doiDao, never()).save(any());
    verify(doi, never()).setDeleted(true);
  }
}
