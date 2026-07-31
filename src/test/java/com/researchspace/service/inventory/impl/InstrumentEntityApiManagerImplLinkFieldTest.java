package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiField.ApiFieldType;
import com.researchspace.api.v1.model.ApiFieldToModelFieldFactory;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInstrumentTemplate;
import com.researchspace.api.v1.model.ApiInstrumentTemplatePost;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.model.inventory.field.InventoryUriField;
import com.researchspace.model.record.RecordFactory;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.ApiExtraFieldsHelper;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.service.inventory.InventoryMoveHelper;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.testutils.TestFactory;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for the link-field persistence logic in {@link InstrumentEntityApiManagerImpl}.
 *
 * <p>The {@code applyLinkFieldValue} method is the creation/update hot-path for structured
 * link-type template fields on instruments. It mirrors the equivalent in {@link
 * SampleApiManagerImpl}; these tests guard against independent drift or regression.
 */
@ExtendWith(MockitoExtension.class)
class InstrumentEntityApiManagerImplLinkFieldTest {

  @Mock private InventoryLinkManager inventoryLinkManager;
  @Mock private InstrumentDao instrumentDao;
  @Mock private InstrumentTemplateDao instrumentTemplateDao;
  @Mock private InventoryPermissionUtils invPermissions;
  @Mock private ContainerDao containerDao;
  @Mock private IPropertyHolder properties;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private UserManager userManager;
  @Mock private ApiExtraFieldsHelper extraFieldHelper;
  @Mock private InventoryMoveHelper inventoryMoveHelper;
  @Mock private MessageSourceUtils messages;
  private InstrumentEntityApiManagerImpl manager;

  private User user;
  private InventoryLinkField dbField;
  private InventoryLink dbLink;

  @BeforeEach
  void setUp() {
    manager = new InstrumentEntityApiManagerImpl();
    ReflectionTestUtils.setField(manager, "inventoryLinkManager", inventoryLinkManager);
    ReflectionTestUtils.setField(manager, "instrumentDao", instrumentDao);
    ReflectionTestUtils.setField(manager, "instrumentTemplateDao", instrumentTemplateDao);
    // the real factory, not a mock: building an instrument from a template is the behaviour under
    // test here, not a trust boundary to stub out
    ReflectionTestUtils.setField(manager, "recordFactory", new RecordFactory());
    ReflectionTestUtils.setField(manager, "invPermissions", invPermissions);
    ReflectionTestUtils.setField(manager, "containerDao", containerDao);
    ReflectionTestUtils.setField(manager, "properties", properties);
    ReflectionTestUtils.setField(manager, "publisher", publisher);
    ReflectionTestUtils.setField(manager, "userManager", userManager);
    ReflectionTestUtils.setField(manager, "extraFieldHelper", extraFieldHelper);
    ReflectionTestUtils.setField(manager, "inventoryMoveHelper", inventoryMoveHelper);
    ReflectionTestUtils.setField(manager, "messages", messages);
    // the real factory is stateless, so the template-create path is exercised end to end
    ReflectionTestUtils.setField(
        manager, "apiFieldToModelFieldFactory", new ApiFieldToModelFieldFactory());
    user = TestFactory.createAnyUser("any");
    dbLink = new InventoryLink();
    dbLink.setRelationType("References");
    dbLink.setTargetGlobalId("SA2");
    dbLink.setTargetPrefix(GlobalIdPrefix.SA);
    dbLink.setTargetDbId(2L);
    dbField = new InventoryLinkField();
    dbField.setName("related sample");
    dbField.setLink(dbLink);
  }

  private ApiInventoryEntityField apiLinkField(
      String targetGlobalId, String relationType, Long versionPin) {
    ApiInventoryEntityField apiField = new ApiInventoryEntityField();
    ApiInventoryLink apiLink = new ApiInventoryLink();
    apiLink.setTargetGlobalId(targetGlobalId);
    apiLink.setRelationType(relationType);
    apiLink.setVersionPin(versionPin);
    apiField.setLink(apiLink);
    return apiField;
  }

  @Test
  void unchangedLinkLeavesTheExistingRowAlone() {
    ApiInventoryEntityField apiField = apiLinkField("SA2", "References", null);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertFalse(changed);
    assertSame(dbLink, dbField.getLink());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void pinnedSuffixedTargetMatchingStoredBaseIdIsUnchanged() {
    dbLink.setVersionPin(4L);
    ApiInventoryEntityField apiField = apiLinkField("SA2v4", "References", null);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertFalse(changed);
    assertSame(dbLink, dbField.getLink());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void retargetUpdatesTheExistingRowInPlace() {
    ApiInventoryEntityField apiField = apiLinkField("SA3", "References", null);
    when(inventoryLinkManager.updateLink(dbLink, apiField.getLink(), user)).thenReturn(dbLink);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    verify(inventoryLinkManager).updateLink(dbLink, apiField.getLink(), user);
    verify(inventoryLinkManager, never()).createLink(any(), any());
  }

  @Test
  void versionPinChangeUpdatesTheExistingRowInPlace() {
    ApiInventoryEntityField apiField = apiLinkField("SA2", "References", 4L);
    when(inventoryLinkManager.updateLink(dbLink, apiField.getLink(), user)).thenReturn(dbLink);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    verify(inventoryLinkManager).updateLink(dbLink, apiField.getLink(), user);
  }

  @Test
  void relationTypeChangeUpdatesTheExistingRowInPlace() {
    ApiInventoryEntityField apiField = apiLinkField("SA2", "IsCitedBy", null);
    when(inventoryLinkManager.updateLink(dbLink, apiField.getLink(), user)).thenReturn(dbLink);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    verify(inventoryLinkManager).updateLink(dbLink, apiField.getLink(), user);
  }

  @Test
  void clearingTheValueDereferencesTheRowForOrphanRemoval() {
    ApiInventoryEntityField apiField = new ApiInventoryEntityField();

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    assertNull(dbField.getLink());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void invalidRelationTypeIsRejectedWithCleanError() {
    ApiInventoryEntityField apiField = apiLinkField("SA3", "NotARelation", null);

    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class, () -> manager.applyLinkFieldValue(dbField, apiField, user));
    assertEquals("errors.inventory.field.linkRelationTypeInvalid", ex.getErrorCode());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void relationOutsideTemplateWhitelistIsRejected() {
    dbField.setAllowedRelationTypes("References|IsPartOf");
    ApiInventoryEntityField apiField = apiLinkField("SA3", "Cites", null);

    ApiRuntimeException ex =
        assertThrows(
            ApiRuntimeException.class, () -> manager.applyLinkFieldValue(dbField, apiField, user));
    assertEquals("errors.inventory.field.linkRelationTypeNotPermitted", ex.getErrorCode());
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void relationInsideTemplateWhitelistIsAccepted() {
    dbField.setAllowedRelationTypes("References|IsPartOf");
    ApiInventoryEntityField apiField = apiLinkField("SA3", "IsPartOf", null);
    when(inventoryLinkManager.updateLink(dbLink, apiField.getLink(), user)).thenReturn(dbLink);

    assertTrue(manager.applyLinkFieldValue(dbField, apiField, user));
  }

  @Test
  void clearingAnAlreadyEmptyFieldIsANoop() {
    dbField.setLink(null);
    ApiInventoryEntityField apiField = new ApiInventoryEntityField();

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertFalse(changed);
    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void anEmptyFieldGainsItsFirstLinkViaCreate() {
    dbField.setLink(null);
    InventoryLink created = new InventoryLink();
    ApiInventoryEntityField apiField = apiLinkField("SA3", "References", null);
    when(inventoryLinkManager.createLink(apiField.getLink(), user)).thenReturn(created);

    boolean changed = manager.applyLinkFieldValue(dbField, apiField, user);

    assertTrue(changed);
    assertSame(created, dbField.getLink());
    verify(inventoryLinkManager, never()).updateLink(any(), any(), any());
  }

  // --- duplicateInstrument / landing-page tests ---

  private Instrument instrumentWithLandingPage(long id, String landingPageData) {
    return instrumentWithLandingPage(id, landingPageData, false);
  }

  private Instrument instrumentWithMandatoryLandingPage(long id, String landingPageData) {
    return instrumentWithLandingPage(id, landingPageData, true);
  }

  private Instrument instrumentWithLandingPage(long id, String landingPageData, boolean mandatory) {
    Instrument instrument = new Instrument();
    instrument.setId(id);
    instrument.setName("Test Instrument");
    instrument.setOwner(user);
    InventoryUriField lp = new InventoryUriField("Landing page");
    lp.setFieldData(landingPageData);
    lp.setMandatory(mandatory);
    addField(instrument, lp);
    return instrument;
  }

  private void addField(Instrument instrument, InventoryEntityField field) {
    field.setInventoryRecord(instrument);
    field.setColumnIndex(instrument.getFields().size() + 1);
    instrument.getFields().add(field);
    instrument.refreshActiveFieldsAndColumnIndex();
  }

  private void stubDuplicateInfrastructure(Instrument source) {
    when(instrumentDao.exists(source.getId())).thenReturn(true);
    when(instrumentDao.get(source.getId())).thenReturn(source);
    when(instrumentDao.save(any()))
        .thenAnswer(
            inv -> {
              Instrument arg = inv.getArgument(0);
              if (arg.getId() == null) {
                arg.setId(source.getId() + 1);
              }
              return arg;
            });
    when(containerDao.getWorkbenchForUser(user)).thenReturn(mock(Container.class));
  }

  @Test
  void duplicatingSystemGeneratedLandingPageGivesTheCopyItsOwnAddress() {
    String serverUrl = "https://rspace.example.com";
    // Source IN1 has the system-generated landing page for IN1
    Instrument source = instrumentWithLandingPage(1L, serverUrl + "/globalId/IN1");

    when(properties.getServerUrl()).thenReturn(serverUrl);
    stubDuplicateInfrastructure(source);

    manager.duplicateInstrument(1L, user);

    // First save creates the copy (id=2); second save persists the filled landing page
    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(2)).save(captor.capture());

    String copyLandingPage = landingPageFieldData(captor.getAllValues().get(1));
    assertEquals(serverUrl + "/globalId/IN2", copyLandingPage);
  }

  @Test
  void duplicatingInstrumentWithNoLandingPageFieldChangesNothing() {
    // Source has no URI fields at all
    Instrument source = new Instrument();
    source.setId(1L);
    source.setName("Plain Instrument");
    source.setOwner(user);

    stubDuplicateInfrastructure(source);

    manager.duplicateInstrument(1L, user);

    // fillBlankLandingPage found nothing to fill — only one save
    verify(instrumentDao, times(1)).save(any());
  }

  /*
   * Deliberately traverses the same code as the system-generated case above: RSDEV-1261 cleared
   * only a system-generated value, RSDEV-1307 made the clear unconditional, so the two scenarios
   * now converge. Kept as a pair because they document the distinction that used to matter, and
   * would diverge again the moment the clear started inspecting the source value.
   */
  @Test
  void duplicatingUserTypedLandingPageGivesTheCopyItsOwnAddress() {
    String serverUrl = "https://rspace.example.com";
    String userTypedUrl = "https://external.lab.example.com/my-instrument";
    Instrument source = instrumentWithLandingPage(1L, userTypedUrl);

    when(properties.getServerUrl()).thenReturn(serverUrl);
    stubDuplicateInfrastructure(source);

    manager.duplicateInstrument(1L, user);

    // First save creates the copy (id=2); second save persists the filled landing page
    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(2)).save(captor.capture());

    assertEquals(serverUrl + "/globalId/IN2", landingPageFieldData(captor.getAllValues().get(1)));
  }

  @Test
  void duplicatingUserTypedLandingPageWithoutServerUrlLeavesItBlank() {
    String userTypedUrl = "https://external.lab.example.com/my-instrument";
    Instrument source = instrumentWithLandingPage(1L, userTypedUrl);

    // No server URL configured: the fill cannot build the copy's own address
    when(properties.getServerUrl()).thenReturn(null);
    stubDuplicateInfrastructure(source);

    manager.duplicateInstrument(1L, user);

    // Clear happened, fill had nothing to write — blank is the recoverable state, one save only
    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(1)).save(captor.capture());

    assertNull(landingPageFieldData(captor.getValue()));
  }

  @Test
  void duplicatingAMandatoryLandingPageDoesNotBlowUp() {
    String serverUrl = "https://rspace.example.com";
    // A mandatory field rejects blank content through setFieldData, so the clear must bypass
    // validation — the refill immediately writes a valid URL anyway
    Instrument source = instrumentWithMandatoryLandingPage(1L, serverUrl + "/globalId/IN1");

    when(properties.getServerUrl()).thenReturn(serverUrl);
    stubDuplicateInfrastructure(source);

    manager.duplicateInstrument(1L, user);

    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(2)).save(captor.capture());
    assertEquals(serverUrl + "/globalId/IN2", landingPageFieldData(captor.getAllValues().get(1)));
  }

  @Test
  void duplicatingDoesNotClearOtherUriFields() {
    String serverUrl = "https://rspace.example.com";
    Instrument source = new Instrument();
    source.setId(1L);
    source.setName("Test Instrument");
    source.setOwner(user);
    // ordered first, so a name-blind findFirst would clear this field instead
    InventoryUriField docs = new InventoryUriField("Documentation URL");
    docs.setFieldData("https://docs.example.org/manual");
    addField(source, docs);
    InventoryUriField lp = new InventoryUriField("Landing page");
    lp.setFieldData(serverUrl + "/globalId/IN1");
    addField(source, lp);

    when(properties.getServerUrl()).thenReturn(serverUrl);
    stubDuplicateInfrastructure(source);

    manager.duplicateInstrument(1L, user);

    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(2)).save(captor.capture());
    Instrument copy = captor.getAllValues().get(1);
    // only the Landing page is identity-bound; an unrelated URI field is copied as-is
    assertEquals(
        "https://docs.example.org/manual",
        copy.getActiveFields().stream()
            .filter(f -> "Documentation URL".equals(f.getName()))
            .findFirst()
            .map(InventoryEntityField::getFieldData)
            .orElse(null));
    // and the right field was still found and refilled, despite not being first
    assertEquals(serverUrl + "/globalId/IN2", landingPageFieldData(copy));
  }

  @Test
  void landingPageIsMatchedIgnoringCaseAndSurroundingWhitespace() {
    String serverUrl = "https://rspace.example.com";
    Instrument source = new Instrument();
    source.setId(1L);
    source.setName("Odd Field Name");
    source.setOwner(user);
    InventoryUriField lp = new InventoryUriField(" landing PAGE ");
    lp.setFieldData("https://external.lab.example.com/my-instrument");
    addField(source, lp);

    when(properties.getServerUrl()).thenReturn(serverUrl);
    stubDuplicateInfrastructure(source);

    manager.duplicateInstrument(1L, user);

    // the name predicate trims and ignores case, so this field is still identity-bound
    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(2)).save(captor.capture());
    assertEquals(
        serverUrl + "/globalId/IN2",
        captor.getAllValues().get(1).getActiveFields().stream()
            .filter(f -> f.getType() == FieldType.URI)
            .findFirst()
            .map(InventoryEntityField::getFieldData)
            .orElse(null));
  }

  private String landingPageFieldData(InstrumentEntity instrument) {
    return instrument.getActiveFields().stream()
        .filter(f -> f.getType() == FieldType.URI)
        .filter(f -> "Landing page".equalsIgnoreCase(f.getName()))
        .findFirst()
        .map(InventoryEntityField::getFieldData)
        .orElse(null);
  }

  // --- createNewApiInstrument / landing-page tests ---

  private void addTemplateField(InstrumentTemplate template, InventoryEntityField field) {
    field.setInventoryRecord(template);
    field.setColumnIndex(template.getFields().size() + 1);
    template.getFields().add(field);
    template.refreshActiveFieldsAndColumnIndex();
  }

  private InstrumentTemplate templateWithLandingPage(long id, String landingPageData) {
    InstrumentTemplate template = new InstrumentTemplate();
    template.setId(id);
    template.setName("Test Template");
    template.setOwner(user);
    InventoryUriField lp = new InventoryUriField("Landing page");
    lp.setFieldData(landingPageData);
    addTemplateField(template, lp);
    return template;
  }

  private ApiInventoryEntityField apiFieldWithContent(String content) {
    ApiInventoryEntityField field = new ApiInventoryEntityField();
    field.setContent(content);
    return field;
  }

  private void stubCreateFromTemplateInfrastructure(InstrumentTemplate template) {
    when(instrumentTemplateDao.exists(template.getId())).thenReturn(true);
    when(instrumentTemplateDao.get(template.getId())).thenReturn(template);
    when(instrumentDao.save(any()))
        .thenAnswer(
            inv -> {
              Instrument arg = inv.getArgument(0);
              if (arg.getId() == null) {
                arg.setId(2L);
              }
              return arg;
            });
    when(containerDao.getWorkbenchForUser(user)).thenReturn(mock(Container.class));
  }

  private ApiInstrument creationRequestEchoing(long templateId, String landingPageContent) {
    ApiInstrument request = new ApiInstrument();
    request.setTemplateId(templateId);
    request.setName("New Instrument");
    request.getFields().add(apiFieldWithContent(landingPageContent));
    return request;
  }

  @Test
  void createFromTemplateWithALandingPageEchoedBackFromTheTemplateGivesItsOwnAddress() {
    String serverUrl = "https://rspace.example.com";
    String templateLandingPage = "https://external.lab.example.com/original";
    InstrumentTemplate template = templateWithLandingPage(1L, templateLandingPage);

    when(properties.getServerUrl()).thenReturn(serverUrl);
    stubCreateFromTemplateInfrastructure(template);

    // a client that reads the template and posts its fields straight back must not be able to
    // re-establish the template's landing page on the new instrument (RSDEV-1307)
    manager.createNewApiInstrument(creationRequestEchoing(1L, templateLandingPage), user);

    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(2)).save(captor.capture());
    assertEquals(serverUrl + "/globalId/IN2", landingPageFieldData(captor.getAllValues().get(1)));
  }

  @Test
  void createFromTemplateKeepsALandingPageTheUserTypedOnTheNewInstrument() {
    String userTyped = "https://external.lab.example.com/my-own-page";
    InstrumentTemplate template =
        templateWithLandingPage(1L, "https://external.lab.example.com/original");

    stubCreateFromTemplateInfrastructure(template);

    // the boundary of the previous test: only the template's own value is discarded, a value the
    // user typed for this record is theirs and is kept
    manager.createNewApiInstrument(creationRequestEchoing(1L, userTyped), user);

    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(1)).save(captor.capture());
    assertEquals(userTyped, landingPageFieldData(captor.getValue()));
    // one save only: nothing was blank, so the fill had nothing to write
    verify(properties, never()).getServerUrl();
  }

  @Test
  void duplicatingATemplateLeavesTheCopysLandingPageBlank() {
    InstrumentTemplate source =
        templateWithLandingPage(1L, "https://external.lab.example.com/original");

    when(instrumentTemplateDao.exists(1L)).thenReturn(true);
    when(instrumentTemplateDao.get(1L)).thenReturn(source);
    when(instrumentTemplateDao.save(any()))
        .thenAnswer(
            inv -> {
              InstrumentTemplate arg = inv.getArgument(0);
              if (arg.getId() == null) {
                arg.setId(2L);
              }
              return arg;
            });

    manager.duplicateInstrumentTemplate(1L, user);

    ArgumentCaptor<InstrumentTemplate> captor = ArgumentCaptor.forClass(InstrumentTemplate.class);
    verify(instrumentTemplateDao).save(captor.capture());
    // a template is never filled with an address: stamping one instrument's page onto a reusable
    // definition would hand it to every instrument later created from it (RSDEV-1307)
    assertNull(landingPageFieldData(captor.getValue()));
    // and the source template keeps its own value
    assertEquals("https://external.lab.example.com/original", landingPageFieldData(source));
    verify(properties, never()).getServerUrl();
  }

  @Test
  void createFromTemplateExemptsTheLandingPageByNameNotByPosition() {
    String serverUrl = "https://rspace.example.com";
    String templateLandingPage = "https://external.lab.example.com/original";
    InstrumentTemplate template = new InstrumentTemplate();
    template.setId(1L);
    template.setName("Two Field Template");
    template.setOwner(user);
    // Manufacturer sits where a positional exemption would land, Landing page does not
    InventoryStringField manufacturer = new InventoryStringField("Manufacturer");
    manufacturer.setFieldData("Template Co");
    addTemplateField(template, manufacturer);
    InventoryUriField lp = new InventoryUriField("Landing page");
    lp.setFieldData(templateLandingPage);
    addTemplateField(template, lp);

    when(properties.getServerUrl()).thenReturn(serverUrl);
    stubCreateFromTemplateInfrastructure(template);

    // the landing page is echoed back rather than blanked: a blank would be stored and refilled
    // identically whichever field the exemption picked, so it could not tell the two apart
    ApiInstrument request = new ApiInstrument();
    request.setTemplateId(1L);
    request.setName("New Instrument");
    request.getFields().add(apiFieldWithContent("Zeiss"));
    request.getFields().add(apiFieldWithContent(templateLandingPage));

    manager.createNewApiInstrument(request, user);

    // exempting by index instead would store the echoed value, leaving nothing blank to fill and
    // so producing a single save holding the template's address
    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(2)).save(captor.capture());
    Instrument created = captor.getAllValues().get(1);
    assertEquals(serverUrl + "/globalId/IN2", landingPageFieldData(created));
    assertEquals(
        "Zeiss",
        created.getActiveFields().stream()
            .filter(f -> "Manufacturer".equals(f.getName()))
            .findFirst()
            .map(InventoryEntityField::getFieldData)
            .orElse(null));
  }

  @Test
  void createFromTemplateWithoutAServerUrlLeavesTheLandingPageBlank() {
    String templateLandingPage = "https://external.lab.example.com/original";
    InstrumentTemplate template = templateWithLandingPage(1L, templateLandingPage);

    // no server URL configured: the fill cannot build the new instrument's own address
    when(properties.getServerUrl()).thenReturn(null);
    stubCreateFromTemplateInfrastructure(template);

    manager.createNewApiInstrument(creationRequestEchoing(1L, templateLandingPage), user);

    // the mirror of the duplicate path: cleared, nothing to fill, and blank is the recoverable
    // state rather than a half-written address
    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(1)).save(captor.capture());
    assertNull(landingPageFieldData(captor.getValue()));
  }

  @Test
  void createWithoutATemplateProducesNoStructuredFields() {
    when(instrumentDao.save(any()))
        .thenAnswer(
            inv -> {
              Instrument arg = inv.getArgument(0);
              if (arg.getId() == null) {
                arg.setId(2L);
              }
              return arg;
            });
    when(containerDao.getWorkbenchForUser(user)).thenReturn(mock(Container.class));

    ApiInstrument request = new ApiInstrument();
    request.setName("Plain Instrument");

    manager.createNewApiInstrument(request, user);

    // pins a cross-repo invariant the landing-page validation exemption relies on: with no
    // template there are no structured fields to validate, so the exemption cannot be reached
    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(1)).save(captor.capture());
    assertTrue(captor.getValue().getActiveFields().isEmpty());
  }

  @Test
  void createFromTemplateRejectsAFieldListThatDoesNotMatchTheTemplate() {
    InstrumentTemplate template =
        templateWithLandingPage(1L, "https://external.lab.example.com/original");

    when(instrumentTemplateDao.exists(1L)).thenReturn(true);
    when(instrumentTemplateDao.get(1L)).thenReturn(template);
    when(messages.getMessage(eq("errors.inventory.instrument.fieldCountMismatch"), any()))
        .thenReturn("field count mismatch");

    ApiInstrument request = new ApiInstrument();
    request.setTemplateId(1L);
    request.setName("New Instrument");
    request.getFields().add(apiFieldWithContent(""));
    request.getFields().add(apiFieldWithContent(""));

    // the message is resolved through the message source, not built inline
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class, () -> manager.createNewApiInstrument(request, user));
    assertEquals("field count mismatch", thrown.getMessage());
    verify(instrumentDao, never()).save(any());
  }

  @Test
  void createFromTemplateStillRejectsAMalformedLandingPage() {
    InstrumentTemplate template =
        templateWithLandingPage(1L, "https://external.lab.example.com/original");

    when(instrumentTemplateDao.exists(1L)).thenReturn(true);
    when(instrumentTemplateDao.get(1L)).thenReturn(template);

    // the clear-instead-of-store path is reserved for blank and inherited values; anything else is
    // user input for this record and goes through the ordinary URI validation
    assertThrows(
        IllegalArgumentException.class,
        () ->
            manager.createNewApiInstrument(creationRequestEchoing(1L, "http://[not a uri"), user));

    verify(instrumentDao, never()).save(any());
  }

  @Test
  void deletingAnInstrumentLinkFieldSoftDeletesItsLink() {
    // RSDEV-1270: the instrument manager had no equivalent of the sample manager's reconciliation,
    // so a deleted link field left its InventoryLink row with deleted=false
    dbField.setDeleted(true);

    manager.softDeleteLinkOfDeletedLinkField(dbField, user);

    verify(inventoryLinkManager).deleteLink(dbLink, user);
  }

  @Test
  void aDeletedInstrumentLinkFieldWhoseLinkIsAlreadyDeletedIsLeftAlone() {
    dbLink.setDeleted(true);
    dbField.setDeleted(true);

    manager.softDeleteLinkOfDeletedLinkField(dbField, user);

    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void aLiveInstrumentLinkFieldKeepsItsLink() {
    manager.softDeleteLinkOfDeletedLinkField(dbField, user);

    verifyNoInteractions(inventoryLinkManager);
  }

  @Test
  void aNewInstrumentTemplateLinkFieldIsCreatedWithItsDefaultLink() {
    // RSDEV-1246: mirrors the sample template create path
    ApiInstrumentTemplatePost post = new ApiInstrumentTemplatePost();
    ApiInventoryEntityField apiField = apiLinkField("SA2", "References", null);
    apiField.setType(ApiFieldType.LINK);
    apiField.setName("default related sample");
    post.setFields(List.of(apiField));
    InventoryLink created = new InventoryLink();
    when(inventoryLinkManager.createLink(apiField.getLink(), user)).thenReturn(created);

    InstrumentTemplate dbTemplate = new InstrumentTemplate();
    manager.addFieldsToNewInstrumentTemplate(post, dbTemplate, user);

    InventoryLinkField added = (InventoryLinkField) dbTemplate.getActiveFields().get(0);
    assertSame(created, added.getLink());
  }

  @Test
  void aLinkFieldAddedToAnExistingInstrumentTemplateIsCreatedWithItsDefaultLink() {
    ApiInstrumentTemplate apiTemplate = new ApiInstrumentTemplate();
    ApiInventoryEntityField apiField = apiLinkField("SA2", "References", null);
    apiField.setType(ApiFieldType.LINK);
    apiField.setName("default related sample");
    apiField.setNewFieldRequest(true);
    apiTemplate.setFields(List.of(apiField));
    InventoryLink created = new InventoryLink();
    when(inventoryLinkManager.createLink(apiField.getLink(), user)).thenReturn(created);

    InstrumentTemplate dbTemplate = new InstrumentTemplate();
    assertTrue(
        manager.createDeleteRequestedFieldsInDbInstrumentTemplate(apiTemplate, dbTemplate, user));

    InventoryLinkField added = (InventoryLinkField) dbTemplate.getActiveFields().get(0);
    assertSame(created, added.getLink());
  }

  @Test
  void editingAnInstrumentTemplatesDefaultLinkUpdatesItsRowInPlace() {
    ApiInstrumentTemplate apiTemplate = new ApiInstrumentTemplate();
    ApiInventoryEntityField apiField = apiLinkField("SA3", "References", null);
    apiField.setId(7L);
    apiTemplate.setFields(List.of(apiField));
    when(inventoryLinkManager.updateLink(dbLink, apiField.getLink(), user)).thenReturn(dbLink);

    InstrumentTemplate dbTemplate = new InstrumentTemplate();
    dbTemplate.setId(1L);
    dbField.setId(7L);
    dbField.setInventoryRecord(dbTemplate);
    dbTemplate.getFields().add(dbField);
    dbTemplate.refreshActiveFieldsAndColumnIndex();

    assertTrue(manager.applyLinkFieldValuesOnUpdate(apiTemplate, dbTemplate, user));
    verify(inventoryLinkManager).updateLink(dbLink, apiField.getLink(), user);
  }
}
