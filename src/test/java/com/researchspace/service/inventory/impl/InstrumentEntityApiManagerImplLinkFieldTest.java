package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.auth.ApiRuntimeException;
import com.researchspace.api.v1.model.ApiInventoryEntityField;
import com.researchspace.api.v1.model.ApiInventoryLink;
import com.researchspace.dao.ContainerDao;
import com.researchspace.dao.InstrumentDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.model.inventory.field.InventoryLinkField;
import com.researchspace.model.inventory.field.InventoryUriField;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.UserManager;
import com.researchspace.service.inventory.InventoryLinkManager;
import com.researchspace.service.inventory.InventoryPermissionUtils;
import com.researchspace.testutils.TestFactory;
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
  @Mock private InventoryPermissionUtils invPermissions;
  @Mock private ContainerDao containerDao;
  @Mock private IPropertyHolder properties;
  @Mock private ApplicationEventPublisher publisher;
  @Mock private UserManager userManager;
  private InstrumentEntityApiManagerImpl manager;

  private User user;
  private InventoryLinkField dbField;
  private InventoryLink dbLink;

  @BeforeEach
  void setUp() {
    manager = new InstrumentEntityApiManagerImpl();
    ReflectionTestUtils.setField(manager, "inventoryLinkManager", inventoryLinkManager);
    ReflectionTestUtils.setField(manager, "instrumentDao", instrumentDao);
    ReflectionTestUtils.setField(manager, "invPermissions", invPermissions);
    ReflectionTestUtils.setField(manager, "containerDao", containerDao);
    ReflectionTestUtils.setField(manager, "properties", properties);
    ReflectionTestUtils.setField(manager, "publisher", publisher);
    ReflectionTestUtils.setField(manager, "userManager", userManager);
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
    Instrument instrument = new Instrument();
    instrument.setId(id);
    instrument.setName("Test Instrument");
    InventoryUriField lp = new InventoryUriField("Landing page");
    lp.setFieldData(landingPageData);
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
    String serverUrl = "https://rspace.example.com";
    // Source has no URI fields at all
    Instrument source = new Instrument();
    source.setId(1L);
    source.setName("Plain Instrument");

    when(properties.getServerUrl()).thenReturn(serverUrl);
    stubDuplicateInfrastructure(source);

    manager.duplicateInstrument(1L, user);

    // fillBlankLandingPage found nothing to fill — only one save
    verify(instrumentDao, times(1)).save(any());
  }

  @Test
  void duplicatingUserTypedLandingPagePreservesTheValue() {
    String serverUrl = "https://rspace.example.com";
    String userTypedUrl = "https://external.lab.example.com/my-instrument";
    // Source has a user-typed URL that does not match the system-generated form for IN1
    Instrument source = instrumentWithLandingPage(1L, userTypedUrl);

    when(properties.getServerUrl()).thenReturn(serverUrl);
    stubDuplicateInfrastructure(source);

    manager.duplicateInstrument(1L, user);

    // Conservative check did not match → field still non-blank → only one save
    ArgumentCaptor<Instrument> captor = ArgumentCaptor.forClass(Instrument.class);
    verify(instrumentDao, times(1)).save(captor.capture());

    assertEquals(userTypedUrl, landingPageFieldData(captor.getValue()));
  }

  private String landingPageFieldData(Instrument instrument) {
    return instrument.getActiveFields().stream()
        .filter(f -> f.getType() == FieldType.URI)
        .filter(f -> "Landing page".equalsIgnoreCase(f.getName()))
        .findFirst()
        .map(InventoryEntityField::getFieldData)
        .orElse(null);
  }
}
