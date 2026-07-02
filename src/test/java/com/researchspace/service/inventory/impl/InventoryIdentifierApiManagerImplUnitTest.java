package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.model.User;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.inventory.RspaceToExternalProviderAdapter;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
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
    when(adapter.buildB2instDoi(instrument)).thenReturn(new B2instDoi());
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
    when(adapter.buildB2instDoi(instrument)).thenReturn(new B2instDoi());
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
}
