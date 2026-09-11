package com.researchspace.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiSampleWithFullSubSamples;
import com.researchspace.b2inst.model.metadata.B2instRelatedIdentifier;
import com.researchspace.datacite.model.DataCiteDoiAttributes;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.testutils.SpringTransactionalTest;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.b2inst.B2instConnectorDummy;
import com.researchspace.webapp.integrations.datacite.DataCiteConnectorDummy;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The PIDINST related-identifier mapping (RSDEV-1253, ADR 0007) against real persistence and the
 * real adapter bean, which {@code RspaceToExternalProviderAdapterImplTest} cannot reach: it builds
 * instruments and links by hand, so it never exercises the link resolved through Hibernate, nor the
 * manager's choice of what to send at each stage of a DOI's life.
 *
 * <p>Guards the wiring specifically. Publish and retract both resend full metadata, so both must
 * route through {@code buildDataCiteDoi}; a caller reverting either to {@code
 * convertToDataCiteDoi()} would leave every unit test green while the registered related
 * identifiers silently regressed.
 */
public class InventoryIdentifierApiManagerRelatedIdentifierTest extends SpringTransactionalTest {

  private static final String MEASUREMENT_TECHNIQUE = "Measurement technique";
  private static final String CALIBRATION = "Calibration";

  private @Autowired InventoryIdentifierApiManager inventoryIdentifierApiMgr;
  private @Autowired IPropertyHolder propertyHolder;
  private User user;
  private DataCiteConnectorDummy dataCiteConnectorDummy;
  private Object realB2instConnector;

  @BeforeEach
  public void setUp() throws Exception {
    super.setUp();
    dataCiteConnectorDummy = new DataCiteConnectorDummy();
    inventoryIdentifierApiMgr.setDataCiteConnector(dataCiteConnectorDummy);
    /*
     * Instruments take the B2INST path whenever B2INST is configured AND enabled
     * (createUpdateWithNewDoi), which on a developer machine depends on the local deployment
     * settings. Disabling it here pins this test to the DataCite branch, the one this class is
     * about, so it cannot pass or fail according to how the machine happens to be configured.
     */
    B2instConnector b2instOff = mock(B2instConnector.class);
    when(b2instOff.isConfiguredAndEnabled()).thenReturn(false);
    realB2instConnector =
        ReflectionTestUtils.getField(inventoryIdentifierApiMgr, "b2instConnector");
    ReflectionTestUtils.setField(inventoryIdentifierApiMgr, "b2instConnector", b2instOff);
    /*
     * The expected addresses below are derived from the live server URL. Without an http(s) scheme
     * the mapping omits every entry by design, and the failures would then read as "the caller
     * bypassed the adapter", pointing at the wrong cause. Fail with the real reason instead.
     */
    String serverUrl = propertyHolder.getServerUrl();
    assertTrue(
        StringUtils.startsWithIgnoreCase(serverUrl, "http://")
            || StringUtils.startsWithIgnoreCase(serverUrl, "https://"),
        "this test needs rs.serverurl to carry an http(s) scheme, but it is: " + serverUrl);
    user = createAndSaveUserIfNotExists(getRandomAlphabeticString("api"));
    initialiseContentWithEmptyContent(user);
  }

  @AfterEach
  public void tearDown() throws Exception {
    // the base tearDown re-enables custom content initialisation for the classes that run after
    // this one in the same cached Spring context; skipping it would starve them of content
    super.tearDown();
    // the manager is a singleton in that same cached context, so the real connector has to go
    // back or later tests silently run against the mock
    ReflectionTestUtils.setField(inventoryIdentifierApiMgr, "b2instConnector", realB2instConnector);
  }

  /** An instrument carrying the two PIDINST link fields, each pointing at a real sample. */
  private ApiInstrument instrumentWithBothLinks() {
    ApiInstrumentTemplatePost templatePost = new ApiInstrumentTemplatePost();
    templatePost.setName("pidinst-links-" + getRandomAlphabeticString("n"));
    templatePost.getFields().add(linkFieldDefinition(MEASUREMENT_TECHNIQUE));
    templatePost.getFields().add(linkFieldDefinition(CALIBRATION));
    ApiInstrumentTemplate template = instrumentApiMgr.createInstrumentTemplate(templatePost, user);

    ApiInstrument request = new ApiInstrument();
    request.setName("Microscope X");
    request.setTemplateId(template.getId());
    ApiInstrument instrument = instrumentApiMgr.createNewApiInstrument(request, user);

    ApiSampleWithFullSubSamples technique = createBasicSampleForUser(user, "technique-doc");
    ApiSampleWithFullSubSamples calibration = createBasicSampleForUser(user, "calibration-cert");
    linkTo(instrument, MEASUREMENT_TECHNIQUE, technique.getGlobalId(), "IsDocumentedBy");
    linkTo(instrument, CALIBRATION, calibration.getGlobalId(), "IsCalibratedBy");
    return instrumentApiMgr.getApiInstrumentById(instrument.getId(), user);
  }

  private ApiInventoryEntityField linkFieldDefinition(String name) {
    ApiInventoryEntityField field = new ApiInventoryEntityField();
    field.setName(name);
    field.setType(ApiFieldType.LINK);
    return field;
  }

  /** Fills one named link field of an existing instrument, by field id. */
  private void linkTo(
      ApiInstrument instrument, String fieldName, String targetGlobalId, String relationType) {
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setTargetGlobalId(targetGlobalId);
    apiLink.setRelationType(relationType);
    ApiInventoryEntityField fieldUpdate = new ApiInventoryEntityField();
    fieldUpdate.setId(fieldByName(instrument, fieldName).getId());
    fieldUpdate.setLink(apiLink);

    ApiInstrument update = new ApiInstrument();
    update.setId(instrument.getId());
    update.setFields(List.of(fieldUpdate));
    instrumentApiMgr.updateApiInstrument(update, user);
  }

  private ApiInventoryEntityField fieldByName(ApiInstrument instrument, String name) {
    Optional<ApiInventoryEntityField> field =
        instrument.getFields().stream().filter(f -> name.equals(f.getName())).findFirst();
    return field.orElseThrow(() -> new AssertionError("no field named " + name));
  }

  private List<DataCiteDoiAttributes.RelatedIdentifier> lastSentRelatedIdentifiers() {
    return dataCiteConnectorDummy.getDoiSentToDatacite().getAttributes().getRelatedIdentifiers();
  }

  /**
   * The related identifiers of the last payload DataCite received, failing with the reason rather
   * than a bare NPE: absent entries mean the caller bypassed the adapter, which is the specific
   * regression this class exists to catch.
   */
  private List<DataCiteDoiAttributes.RelatedIdentifier> requireSentRelatedIdentifiers(
      String stage) {
    List<DataCiteDoiAttributes.RelatedIdentifier> sent = lastSentRelatedIdentifiers();
    assertNotNull(
        sent,
        "no related identifiers reached DataCite on "
            + stage
            + "; the caller most likely converted the DOI directly instead of routing through"
            + " RspaceToExternalProviderAdapter.buildDataCiteDoi");
    return sent;
  }

  private void assertNamesTheLinkedRecord(
      DataCiteDoiAttributes.RelatedIdentifier entry, String label, String targetGlobalId) {
    assertEquals(label, entry.getRelationTypeInformation());
    // IsDescribedBy whatever the link stores (IsDocumentedBy / IsCalibratedBy): PIDINST's
    // vocabulary has neither, see ADR 0007
    assertEquals("IsDescribedBy", entry.getRelationType());
    assertEquals("URL", entry.getRelatedIdentifierType());
    assertEquals(
        propertyHolder.getServerUrl() + "/globalId/" + targetGlobalId,
        entry.getRelatedIdentifier());
  }

  @Test
  public void publishAndRetractBothSendTheInstrumentsRelatedIdentifiers() {
    ApiInstrument instrument = instrumentWithBothLinks();
    String techniqueId =
        fieldByName(instrument, MEASUREMENT_TECHNIQUE).getLink().getTargetGlobalId();
    String calibrationId = fieldByName(instrument, CALIBRATION).getLink().getTargetGlobalId();
    GlobalIdentifier instrumentOid = instrument.getOid();

    // Register sends an empty DataCiteDoi by design (the metadata follows on publish), so there is
    // nothing to carry yet; asserted so the deliberate emptiness is not mistaken for a regression.
    inventoryIdentifierApiMgr.registerNewIdentifier(instrumentOid, user);
    assertNull(lastSentRelatedIdentifiers());

    // detach everything built above so publish resolves the instrument and its links through
    // Hibernate rather than the session cache, making the "against real persistence" claim real
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();
    dataCiteConnectorDummy.doiSentToDatacite = null;

    ApiInventoryRecordInfo published =
        inventoryIdentifierApiMgr.publishIdentifier(instrumentOid, user);
    assertEquals("findable", published.getIdentifiers().get(0).getState());

    List<DataCiteDoiAttributes.RelatedIdentifier> onPublish =
        requireSentRelatedIdentifiers("publish");
    assertEquals(2, onPublish.size());
    assertNamesTheLinkedRecord(onPublish.get(0), "Measurement Technique", techniqueId);
    assertNamesTheLinkedRecord(onPublish.get(1), "Calibration", calibrationId);

    /*
     * Retract resends the full metadata, so it has to resend these too. Nothing persists them on
     * the DOI: they are recomputed from the instrument each time, which is exactly why a retract
     * that skipped the adapter would quietly strip them from the registered record.
     */
    dataCiteConnectorDummy.doiSentToDatacite = null;
    inventoryIdentifierApiMgr.retractIdentifier(instrumentOid, user);

    List<DataCiteDoiAttributes.RelatedIdentifier> onRetract =
        requireSentRelatedIdentifiers("retract");
    assertEquals(2, onRetract.size());
    assertNamesTheLinkedRecord(onRetract.get(0), "Measurement Technique", techniqueId);
    assertNamesTheLinkedRecord(onRetract.get(1), "Calibration", calibrationId);
  }

  @Test
  public void publishOmitsALinkFieldThatHasSinceBeenCleared() {
    ApiInstrument instrument = instrumentWithBothLinks();
    String calibrationId = fieldByName(instrument, CALIBRATION).getLink().getTargetGlobalId();
    GlobalIdentifier instrumentOid = instrument.getOid();
    inventoryIdentifierApiMgr.registerNewIdentifier(instrumentOid, user);

    /*
     * Clearing a link field is an update carrying the field with no link payload
     * (InstrumentEntityApiManagerImpl.applyLinkFieldValue), which detaches the row via
     * orphanRemoval. Worth proving from the API inwards: the entries are recomputed from the
     * instrument on every publish, so an emptied field has to stop being registered rather than
     * lingering because it was present when the identifier was created.
     */
    ApiInventoryEntityField cleared = new ApiInventoryEntityField();
    cleared.setId(fieldByName(instrument, MEASUREMENT_TECHNIQUE).getId());
    ApiInstrument update = new ApiInstrument();
    update.setId(instrument.getId());
    update.setFields(List.of(cleared));
    instrumentApiMgr.updateApiInstrument(update, user);

    inventoryIdentifierApiMgr.publishIdentifier(instrumentOid, user);

    List<DataCiteDoiAttributes.RelatedIdentifier> sent = requireSentRelatedIdentifiers("publish");
    assertEquals(1, sent.size(), "the cleared Measurement technique field must not be registered");
    assertNamesTheLinkedRecord(sent.get(0), "Calibration", calibrationId);
  }

  /**
   * Clearing BOTH link fields must reach DataCite as an explicit empty list: [] is how the
   * registered property is cleared, while an absent or null property would leave the previously
   * registered entries attached forever.
   */
  @Test
  public void publishSendsAnExplicitEmptyListWhenBothLinkFieldsAreCleared() {
    ApiInstrument instrument = instrumentWithBothLinks();
    GlobalIdentifier instrumentOid = instrument.getOid();
    inventoryIdentifierApiMgr.registerNewIdentifier(instrumentOid, user);

    ApiInventoryEntityField clearedTechnique = new ApiInventoryEntityField();
    clearedTechnique.setId(fieldByName(instrument, MEASUREMENT_TECHNIQUE).getId());
    ApiInventoryEntityField clearedCalibration = new ApiInventoryEntityField();
    clearedCalibration.setId(fieldByName(instrument, CALIBRATION).getId());
    ApiInstrument update = new ApiInstrument();
    update.setId(instrument.getId());
    update.setFields(List.of(clearedTechnique, clearedCalibration));
    instrumentApiMgr.updateApiInstrument(update, user);

    dataCiteConnectorDummy.doiSentToDatacite = null;
    inventoryIdentifierApiMgr.publishIdentifier(instrumentOid, user);

    List<DataCiteDoiAttributes.RelatedIdentifier> sent = lastSentRelatedIdentifiers();
    assertNotNull(sent, "both fields cleared must clear the property with [], not leave it absent");
    assertEquals(0, sent.size());
  }

  /**
   * The B2INST branch of the same mapping. It is a separate code path in the adapter and a separate
   * provider branch in the manager, and unlike DataCite it gets exactly one shot: B2INST has no
   * metadata-update call, so whatever the draft carries at register time is what the record keeps.
   */
  @Test
  public void b2instRegistrationCarriesTheInstrumentsRelatedIdentifiers() {
    B2instConnectorDummy b2instDummy = new B2instConnectorDummy();
    ReflectionTestUtils.setField(inventoryIdentifierApiMgr, "b2instConnector", b2instDummy);

    ApiInstrument instrument = instrumentWithBothLinks();
    String techniqueId =
        fieldByName(instrument, MEASUREMENT_TECHNIQUE).getLink().getTargetGlobalId();
    String calibrationId = fieldByName(instrument, CALIBRATION).getLink().getTargetGlobalId();

    inventoryIdentifierApiMgr.registerNewIdentifier(instrument.getOid(), user);

    List<B2instRelatedIdentifier> sent =
        b2instDummy.getDoiSentToB2inst().getMetadata().getRelatedIdentifier();
    assertNotNull(sent, "no RelatedIdentifier reached B2INST at draft-register time");
    assertEquals(2, sent.size());
    assertEquals("Measurement Technique", sent.get(0).getRelatedIdentifierName());
    assertEquals("IsDescribedBy", sent.get(0).getRelationType());
    assertEquals("URL", sent.get(0).getRelatedIdentifierType());
    assertEquals(
        propertyHolder.getServerUrl() + "/globalId/" + techniqueId,
        sent.get(0).getRelatedIdentifierValue());
    assertEquals("Calibration", sent.get(1).getRelatedIdentifierName());
    assertEquals("IsDescribedBy", sent.get(1).getRelationType());
    assertEquals("URL", sent.get(1).getRelatedIdentifierType());
    assertEquals(
        propertyHolder.getServerUrl() + "/globalId/" + calibrationId,
        sent.get(1).getRelatedIdentifierValue());
  }
}
