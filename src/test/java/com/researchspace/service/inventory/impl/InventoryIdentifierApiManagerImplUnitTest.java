package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRecordLinks;
import com.researchspace.b2inst.model.response.B2instRequestResponse;
import com.researchspace.core.util.JacksonUtil;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryUriField;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.JsonMessageSource;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InventoryRecordRetriever;
import com.researchspace.service.inventory.RspaceToExternalProviderAdapter;
import com.researchspace.webapp.integrations.b2inst.B2instConnectionException;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
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

  /**
   * A real Instrument carrying one "Landing page" URI field with the given content, since the
   * landing-page rules read getActiveFields(), which a mock cannot exercise.
   */
  private static Instrument instrumentWithLandingPage(String content) {
    Instrument instrument = new Instrument();
    instrument.setId(5L);
    instrument.setName("Microscope X");
    InventoryUriField field = new InventoryUriField("Landing page");
    field.setId(77L);
    field.setFieldData(content);
    field.setInventoryRecord(instrument);
    field.setColumnIndex(1);
    instrument.getFields().add(field);
    instrument.refreshActiveFieldsAndColumnIndex();
    return instrument;
  }

  private static ApiInventoryDOI newPidinstRegistration() {
    ApiInventoryDOI doi = new ApiInventoryDOI();
    doi.generatePublicLinkSuffix();
    doi.setRegisterIdentifierRequest(true);
    doi.setDoiType(IdentifierType.PIDINST_B2INST.name());
    return doi;
  }

  private static ApiInstrument instrumentUpdateFor(
      InventoryIdentifierApiManagerImpl mgr, InventoryRecord invRec, ApiInventoryDOI doi)
      throws Exception {
    Method m =
        InventoryIdentifierApiManagerImpl.class.getDeclaredMethod(
            "getApiInstrumentUpdateWithIdentifier", InventoryRecord.class, ApiInventoryDOI.class);
    m.setAccessible(true);
    return (ApiInstrument) m.invoke(mgr, invRec, doi);
  }

  private static InventoryIdentifierApiManagerImpl mgrWithServerUrl(String serverUrl) {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn(serverUrl);
    ReflectionTestUtils.setField(mgr, "properties", properties);
    return mgr;
  }

  private static Optional<ApiInventoryEntityField> landingPageUpdate(ApiInstrument update) {
    return update.getFields().stream().filter(f -> Long.valueOf(77L).equals(f.getId())).findFirst();
  }

  /**
   * The behaviour RSDEV-1254 was reopened for: registering a PIDINST writes the address that was
   * registered into the instrument's own Landing page field, so the field and the registered value
   * cannot drift apart (ADR 0006 item 4).
   */
  @Test
  void newPidinstRegistrationWritesThePublicLandingPageIntoABlankField() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = mgrWithServerUrl("https://rspace.example.com");
    Instrument instrument = instrumentWithLandingPage(null);
    ApiInventoryDOI doi = newPidinstRegistration();

    ApiInstrument update = instrumentUpdateFor(mgr, instrument, doi);

    assertEquals(
        "https://rspace.example.com/public/inventory/" + doi.getPublicLinkSuffix(),
        landingPageUpdate(update).orElseThrow().getContent());
  }

  /** ADR 0006 item 4: a value the user typed is theirs and survives registration untouched. */
  @Test
  void newPidinstRegistrationLeavesAUserTypedLandingPageAlone() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = mgrWithServerUrl("https://rspace.example.com");
    Instrument instrument = instrumentWithLandingPage("https://lab.example.org/aws-42");

    ApiInstrument update = instrumentUpdateFor(mgr, instrument, newPidinstRegistration());

    assertTrue(landingPageUpdate(update).isEmpty(), "a typed landing page must not be overwritten");
  }

  /**
   * A value the retired auto-fill wrote reads as an empty field, so registration replaces it: it is
   * login-walled, and leaving it would put a different address in the field than was registered.
   */
  @Test
  void newPidinstRegistrationOverwritesALegacyAutoFilledLandingPage() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = mgrWithServerUrl("https://rspace.example.com");
    Instrument instrument =
        instrumentWithLandingPage("https://old-name.example.com/globalId/IN5?from=email");
    ApiInventoryDOI doi = newPidinstRegistration();

    ApiInstrument update = instrumentUpdateFor(mgr, instrument, doi);

    assertEquals(
        "https://rspace.example.com/public/inventory/" + doi.getPublicLinkSuffix(),
        landingPageUpdate(update).orElseThrow().getContent());
  }

  /** Only a brand-new registration seeds the field; publish, assign and delete updates must not. */
  @Test
  void anIdentifierUpdateThatIsNotANewRegistrationLeavesTheFieldAlone() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = mgrWithServerUrl("https://rspace.example.com");
    ApiInventoryDOI publishUpdate = new ApiInventoryDOI();
    publishUpdate.setDoiType(IdentifierType.PIDINST_B2INST.name());

    ApiInstrument update = instrumentUpdateFor(mgr, instrumentWithLandingPage(null), publishUpdate);

    assertTrue(landingPageUpdate(update).isEmpty());
  }

  /** No server URL means no public address to write; the field is left blank rather than junk. */
  @Test
  void newPidinstRegistrationLeavesTheFieldBlankWhenNoServerUrlIsConfigured() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = mgrWithServerUrl(null);

    ApiInstrument update =
        instrumentUpdateFor(mgr, instrumentWithLandingPage(null), newPidinstRegistration());

    assertTrue(landingPageUpdate(update).isEmpty());
  }

  private Method refreshMethod() throws Exception {
    Method m =
        InventoryIdentifierApiManagerImpl.class.getDeclaredMethod(
            "createUpdateWithRefreshedB2instDoi", DigitalObjectIdentifier.class);
    m.setAccessible(true);
    return m;
  }

  private DigitalObjectIdentifier b2instDoi() {
    DigitalObjectIdentifier doi = new DigitalObjectIdentifier("k2j9p-7yh21", "instr");
    doi.setType(IdentifierType.PIDINST_B2INST);
    ReflectionTestUtils.setField(doi, "id", 12L);
    return doi;
  }

  @Test
  void refreshPersistsOpenReviewStatusVerbatim() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    B2instRequestResponse review = new B2instRequestResponse();
    review.setStatus("declined");
    when(b2instConnector.getReviewOf("k2j9p-7yh21")).thenReturn(Optional.of(review));

    ApiInventoryDOI result = (ApiInventoryDOI) refreshMethod().invoke(mgr, b2instDoi());

    assertEquals(12L, result.getId());
    assertEquals("declined", result.getState());
    verify(b2instConnector, never()).getPublishedRecord(any());
  }

  @Test
  void refreshMapsMissingReviewWithPublishedRecordToAccepted() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example.com");
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    ReflectionTestUtils.setField(mgr, "properties", properties);
    when(b2instConnector.getReviewOf("k2j9p-7yh21")).thenReturn(Optional.empty());
    B2instDraftRecord published = new B2instDraftRecord();
    published.setPids(
        Map.of("epic", Map.of("identifier", "http://hdl.handle.net/21.T11975/k2j9p-7yh21")));
    B2instRecordLinks links = new B2instRecordLinks();
    links.setSelfHtml("https://b2inst-test.gwdg.de/records/k2j9p-7yh21");
    published.setLinks(links);
    when(b2instConnector.getPublishedRecord("k2j9p-7yh21")).thenReturn(Optional.of(published));

    DigitalObjectIdentifier doi = b2instDoi();
    ApiInventoryDOI result = (ApiInventoryDOI) refreshMethod().invoke(mgr, doi);

    assertEquals("accepted", result.getState());
    assertEquals("http://hdl.handle.net/21.T11975/k2j9p-7yh21", result.getPublicUrl());
    assertEquals("https://b2inst-test.gwdg.de/records/k2j9p-7yh21", result.getProviderUrl());
    // accepted is the B2INST equivalent of findable, whose url is the RSpace landing page
    assertEquals(
        "https://rspace.example.com/public/inventory/" + doi.getPublicLink(), result.getUrl());
  }

  @Test
  void refreshFallsBackToDraftWhenOnlyTheDraftSurvives() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    when(b2instConnector.getReviewOf("k2j9p-7yh21")).thenReturn(Optional.empty());
    when(b2instConnector.getPublishedRecord("k2j9p-7yh21")).thenReturn(Optional.empty());
    when(b2instConnector.getDraftRecord("k2j9p-7yh21"))
        .thenReturn(Optional.of(new B2instDraftRecord()));

    ApiInventoryDOI result = (ApiInventoryDOI) refreshMethod().invoke(mgr, b2instDoi());

    assertEquals("draft", result.getState());
  }

  @Test
  void refreshReportsRecordGoneWhenNothingRemainsAtProvider() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    when(b2instConnector.getReviewOf("k2j9p-7yh21")).thenReturn(Optional.empty());
    when(b2instConnector.getPublishedRecord("k2j9p-7yh21")).thenReturn(Optional.empty());
    when(b2instConnector.getDraftRecord("k2j9p-7yh21")).thenReturn(Optional.empty());

    InvocationTargetException thrown =
        assertThrows(
            InvocationTargetException.class, () -> refreshMethod().invoke(mgr, b2instDoi()));

    assertInstanceOf(ApiRuntimeException.class, thrown.getCause());
  }

  @Test
  void refreshWrapsConnectorFailureWithLocalizedReason() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    MessageSourceUtils messages = mock(MessageSourceUtils.class);
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    ReflectionTestUtils.setField(mgr, "messages", messages);
    when(messages.getMessage(eq("errors.inventory.identifier.b2instRefreshFailed"), any()))
        .thenReturn("Could not refresh. reason");
    when(b2instConnector.getReviewOf("k2j9p-7yh21"))
        .thenThrow(new B2instConnectionException("dev message", "reason", null));

    InvocationTargetException thrown =
        assertThrows(
            InvocationTargetException.class, () -> refreshMethod().invoke(mgr, b2instDoi()));

    assertInstanceOf(B2instConnectionException.class, thrown.getCause());
    assertEquals("Could not refresh. reason", thrown.getCause().getMessage());
  }

  /**
   * The refresh entry point rejected a record with no identifier using hard-coded developer text,
   * which the API returns verbatim to clients. Every other refresh failure resolves an {@code
   * errors.inventory.identifier.*} key, so this one does too (Copilot review, PR 1066).
   */
  @Test
  void refreshWithoutIdentifierRaisesLocalizedErrorCode() {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    InventoryRecordRetriever retriever = mock(InventoryRecordRetriever.class);
    ReflectionTestUtils.setField(mgr, "invRecRetriever", retriever);
    GlobalIdentifier oid = new GlobalIdentifier("IT1");
    Instrument withoutIdentifier = new Instrument();
    when(retriever.getInvRecordByGlobalId(oid)).thenReturn(withoutIdentifier);

    ApiRuntimeException thrown =
        assertThrows(ApiRuntimeException.class, () -> mgr.refreshIdentifier(oid, new User("u")));

    assertEquals("errors.inventory.identifier.refreshNoIdentifier", thrown.getErrorCode());
  }

  /**
   * The url reported after acceptance must be the address the minted Handle actually resolves to,
   * which is the LandingPage B2INST holds for the record. Registration prefers a user-typed
   * institutional address over RSpace's own public page, so rebuilding the RSpace page here would
   * report an address the PID does not resolve to (Copilot review, PR 1066).
   */
  @Test
  void refreshTakesUrlFromTheRegisteredLandingPageWhenTheRecordCarriesOne() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example.com");
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    ReflectionTestUtils.setField(mgr, "properties", properties);
    when(b2instConnector.getReviewOf("k2j9p-7yh21")).thenReturn(Optional.empty());
    B2instDraftRecord published = new B2instDraftRecord();
    B2instInstrumentMetadata metadata = new B2instInstrumentMetadata();
    metadata.setLandingPage("https://institution.example.org/instruments/nmr-400");
    published.setMetadata(metadata);
    when(b2instConnector.getPublishedRecord("k2j9p-7yh21")).thenReturn(Optional.of(published));

    ApiInventoryDOI result = (ApiInventoryDOI) refreshMethod().invoke(mgr, b2instDoi());

    assertEquals("accepted", result.getState());
    assertEquals("https://institution.example.org/instruments/nmr-400", result.getUrl());
  }

  /**
   * Acceptance known from the review alone carries no record to read a LandingPage from, so the
   * identifier's own public page stays the only address available.
   */
  @Test
  void refreshFallsBackToThePublicPageWhenAcceptanceComesFromTheReviewAlone() throws Exception {
    InventoryIdentifierApiManagerImpl mgr = new InventoryIdentifierApiManagerImpl();
    B2instConnector b2instConnector = mock(B2instConnector.class);
    IPropertyHolder properties = mock(IPropertyHolder.class);
    when(properties.getServerUrl()).thenReturn("https://rspace.example.com");
    ReflectionTestUtils.setField(mgr, "b2instConnector", b2instConnector);
    ReflectionTestUtils.setField(mgr, "properties", properties);
    B2instRequestResponse review = new B2instRequestResponse();
    review.setStatus("accepted");
    when(b2instConnector.getReviewOf("k2j9p-7yh21")).thenReturn(Optional.of(review));
    when(b2instConnector.getPublishedRecord("k2j9p-7yh21")).thenReturn(Optional.empty());

    DigitalObjectIdentifier doi = b2instDoi();
    ApiInventoryDOI result = (ApiInventoryDOI) refreshMethod().invoke(mgr, doi);

    assertEquals("accepted", result.getState());
    assertEquals(
        "https://rspace.example.com/public/inventory/" + doi.getPublicLink(), result.getUrl());
  }
}
