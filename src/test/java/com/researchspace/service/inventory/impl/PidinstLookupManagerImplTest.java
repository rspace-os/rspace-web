package com.researchspace.service.inventory.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.api.v1.model.ApiContainerLocation;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.b2inst.model.metadata.B2instIdentifier;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.b2inst.model.metadata.B2instManufacturer;
import com.researchspace.b2inst.model.metadata.B2instOwner;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
import com.researchspace.b2inst.model.response.B2instRecordLinks;
import com.researchspace.b2inst.model.response.B2instSearchResult;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryStringField;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.PidinstAlreadyLinkedException;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PidinstLookupManagerImplTest {

  private static final String HANDLE = "21.T11975/abcde-12345";

  @Mock private B2instConnector b2instConnector;
  @Mock private DataCiteConnector dataCiteConnector;
  @Mock private DigitalObjectIdentifierDao doiDao;
  @Mock private InstrumentTemplateDao instrumentTemplateDao;
  @Mock private InstrumentEntityApiManager instrumentApiMgr;
  @Mock private InventoryIdentifierApiManager identifierMgr;
  @Mock private MessageSourceUtils messages;
  @InjectMocks private PidinstLookupManagerImpl manager;

  private final User user = new User("jane");

  @BeforeEach
  void setUp() {
    // B2INST is the enabled provider in every test here; DataCite is asked only if B2INST is off
    lenient().when(b2instConnector.isConfiguredAndEnabled()).thenReturn(true);
    lenient()
        .when(messages.getMessage(anyString(), any(Object[].class)))
        .thenAnswer(
            invocation ->
                invocation.getArgument(0) + " " + invocation.getArgument(1, Object[].class)[0]);
  }

  private static B2instDraftRecord publishedRecord() {
    B2instInstrumentMetadata md = new B2instInstrumentMetadata();
    md.setName("Test microscope");
    md.setIdentifier(new B2instIdentifier("Handle", HANDLE));
    md.setOwner(List.of(new B2instOwner("Lab A", null)));
    md.setManufacturer(List.of(new B2instManufacturer("Zeiss", null)));
    B2instDraftRecord record = new B2instDraftRecord();
    record.setId("abcde-12345");
    record.setIsPublished(true);
    record.setMetadata(md);
    B2instRecordLinks links = new B2instRecordLinks();
    links.setSelfHtml("https://b2inst-test.gwdg.de/records/abcde-12345");
    record.setLinks(links);
    return record;
  }

  private static B2instSearchResult searchResultOf(B2instDraftRecord record, int total) {
    B2instSearchResult result = new B2instSearchResult();
    result.getHits().getHits().add(record);
    result.getHits().setTotal(total);
    return result;
  }

  private static InstrumentTemplate lockedTemplate() {
    InstrumentTemplate template = new InstrumentTemplate();
    template.setId(7L);
    for (String name : List.of("Owner", "Manufacturer")) {
      InventoryEntityField field = new InventoryStringField(name);
      field.setMandatory(true);
      field.setInventoryRecord(template);
      field.setColumnIndex(template.getFields().size() + 1);
      template.getFields().add(field);
    }
    template.refreshActiveFieldsAndColumnIndex();
    return template;
  }

  @Test
  void freeTextSearchGoesToTheEnabledProviderAndFlagsAlreadyLinkedPids() {
    when(b2instConnector.searchRecords("microscope", 50))
        .thenReturn(searchResultOf(publishedRecord(), 3));
    // the B2INST published index does not carry the account's own drafts, so both are searched
    when(b2instConnector.searchUserRecords("microscope", 50)).thenReturn(new B2instSearchResult());
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier(HANDLE, "Test microscope", "suffix1234567890");
    Instrument owner = new Instrument();
    owner.setId(99L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifierAndType(HANDLE, IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.of(existing));

    ApiPidinstSearchResult result = manager.search("  microscope ", user);

    assertEquals("PIDINST_B2INST", result.getProvider());
    assertEquals(3, result.getTotal());
    assertEquals(1, result.getHits().size());
    assertEquals(HANDLE, result.getHits().get(0).getPid());
    assertEquals("IN99", result.getHits().get(0).getLinkedInstrumentGlobalId());
    verify(dataCiteConnector, never()).searchInstrumentDois(anyString(), eq(50), any());
  }

  @Test
  void aHandleIsADirectLookupAndADoiYieldsNoHitOnAB2instDeployment() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(publishedRecord()));
    when(doiDao.findActiveByIdentifierAndType(HANDLE, IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.empty());

    ApiPidinstSearchResult direct = manager.search("https://hdl.handle.net/" + HANDLE, user);
    assertEquals(1, direct.getHits().size());
    assertNull(direct.getHits().get(0).getLinkedInstrumentGlobalId());
    verify(b2instConnector, never()).searchRecords(anyString(), eq(50));
    verify(b2instConnector, never()).searchUserRecords(anyString(), eq(50));

    ApiPidinstSearchResult foreign = manager.search("10.15151/esrf-instr-gco8", user);
    assertTrue(foreign.getHits().isEmpty());
    assertEquals(0, foreign.getTotal());
    verify(b2instConnector, never()).searchRecords(eq("10.15151/esrf-instr-gco8"), eq(50));
  }

  @Test
  void importCreatesFromTheLockedTemplateThenLinksTheIdentifier() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(publishedRecord()));
    when(doiDao.findActiveByIdentifierAndType(HANDLE, IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.empty());
    when(instrumentTemplateDao.findLockedTemplateByName("Instrument (PIDINST 1.0)"))
        .thenReturn(Optional.of(lockedTemplate()));
    ApiInstrument created = new ApiInstrument();
    created.setId(5L);
    created.setGlobalId("IN5");
    when(instrumentApiMgr.createNewApiInstrument(any(ApiInstrument.class), eq(user)))
        .thenReturn(created);
    ApiInstrument linked = new ApiInstrument();
    linked.setId(5L);
    when(identifierMgr.linkExternalIdentifier(
            eq(new GlobalIdentifier("IN5")), any(ApiInventoryDOI.class), eq(user)))
        .thenReturn(linked);

    ApiInstrument result = manager.importInstrument(HANDLE, null, user);

    assertEquals(5L, result.getId());
    ArgumentCaptor<ApiInstrument> toCreate = ArgumentCaptor.forClass(ApiInstrument.class);
    verify(instrumentApiMgr).createNewApiInstrument(toCreate.capture(), eq(user));
    assertEquals(7L, toCreate.getValue().getTemplateId());
    assertEquals("Lab A", toCreate.getValue().getFields().get(0).getContent());
    assertEquals("Zeiss", toCreate.getValue().getFields().get(1).getContent());
    ArgumentCaptor<ApiInventoryDOI> link = ArgumentCaptor.forClass(ApiInventoryDOI.class);
    verify(identifierMgr)
        .linkExternalIdentifier(eq(new GlobalIdentifier("IN5")), link.capture(), eq(user));
    assertTrue(link.getValue().isLinked());
    assertEquals(HANDLE, link.getValue().getDoi());
    assertEquals("accepted", link.getValue().getState());
  }

  @Test
  void importRefusesAPidThatIsAlreadyLinkedNamingTheInstrument() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(publishedRecord()));
    DigitalObjectIdentifier existing =
        new DigitalObjectIdentifier(HANDLE, "Test microscope", "suffix1234567890");
    Instrument owner = new Instrument();
    owner.setId(99L);
    owner.addIdentifier(existing);
    when(doiDao.findActiveByIdentifierAndType(HANDLE, IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.of(existing));

    PidinstAlreadyLinkedException ex =
        assertThrows(
            PidinstAlreadyLinkedException.class,
            () -> manager.importInstrument(HANDLE, null, user));

    assertEquals("IN99", ex.getLinkedInstrumentGlobalId());
    assertTrue(
        ex.getMessage().contains("pidinstAlreadyLinked") && ex.getMessage().contains("IN99"));
    verify(instrumentApiMgr, never()).createNewApiInstrument(any(), any());
  }

  @Test
  void importIs404WhenTheProviderHasNoPublishedRecordOrTheValueIsNotAPidOfThisProvider() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> manager.importInstrument(HANDLE, null, user));
    assertThrows(
        NotFoundException.class,
        () -> manager.importInstrument("10.15151/esrf-instr-gco8", null, user));
    verify(instrumentApiMgr, never()).createNewApiInstrument(any(), any());
  }

  @Test
  void withNoPidinstProviderEnabledThereIsNoLookup() {
    when(b2instConnector.isConfiguredAndEnabled()).thenReturn(false);
    when(dataCiteConnector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST))
        .thenReturn(false);

    assertThrows(UnsupportedOperationException.class, () -> manager.search("microscope", user));
  }

  @Test
  void importAcceptsARecordInAnyProviderStatusAndLinksTheStatusItReports() {
    B2instDraftRecord unpublished = publishedRecord();
    unpublished.setIsPublished(false);
    unpublished.setStatus("submitted");
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(unpublished));
    when(doiDao.findActiveByIdentifierAndType(HANDLE, IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.empty());
    when(instrumentTemplateDao.findLockedTemplateByName("Instrument (PIDINST 1.0)"))
        .thenReturn(Optional.of(lockedTemplate()));
    ApiInstrument created = new ApiInstrument();
    created.setId(5L);
    created.setGlobalId("IN5");
    when(instrumentApiMgr.createNewApiInstrument(any(ApiInstrument.class), eq(user)))
        .thenReturn(created);
    when(identifierMgr.linkExternalIdentifier(
            eq(new GlobalIdentifier("IN5")), any(ApiInventoryDOI.class), eq(user)))
        .thenReturn(created);

    manager.importInstrument(HANDLE, null, user);

    ArgumentCaptor<ApiInventoryDOI> link = ArgumentCaptor.forClass(ApiInventoryDOI.class);
    verify(identifierMgr).linkExternalIdentifier(any(), link.capture(), eq(user));
    assertTrue(link.getValue().isLinked());
    assertEquals("submitted", link.getValue().getState(), "the provider's own status is linked");
  }

  private static B2instDraftRecord draftRecord() {
    B2instInstrumentMetadata md = new B2instInstrumentMetadata();
    md.setName("Draft microscope");
    md.setIdentifier(new B2instIdentifier("Handle", "21.T11975/anaf6-fk223"));
    B2instDraftRecord record = new B2instDraftRecord();
    record.setId("anaf6-fk223");
    record.setIsPublished(false);
    record.setStatus("draft");
    record.setMetadata(md);
    return record;
  }

  @Test
  void b2instSearchMergesTheAccountsOwnRecordsAndDropsTheOnesSeenTwice() {
    // the account's own PUBLISHED records are in both indexes, so the union must be deduplicated
    B2instSearchResult published = searchResultOf(publishedRecord(), 3);
    B2instSearchResult own = searchResultOf(publishedRecord(), 2);
    own.getHits().getHits().add(draftRecord());
    when(b2instConnector.searchRecords("microscope", 50)).thenReturn(published);
    when(b2instConnector.searchUserRecords("microscope", 50)).thenReturn(own);
    when(doiDao.findActiveByIdentifierAndType(anyString(), eq(IdentifierType.PIDINST_B2INST)))
        .thenReturn(Optional.empty());

    ApiPidinstSearchResult result = manager.search("microscope", user);

    assertEquals(2, result.getHits().size(), "the record in both indexes appears once");
    assertEquals("Draft microscope", result.getHits().get(0).getName(), "sorted by name");
    assertEquals("draft", result.getHits().get(0).getState(), "a draft is offered for import");
    assertEquals(HANDLE, result.getHits().get(1).getPid());
    assertEquals(4, result.getTotal(), "both provider totals, less the record counted twice");
  }

  @Test
  void importPlacesTheNewInstrumentInTheRequestedTargetLocation() {
    when(b2instConnector.getRecordByHandle(HANDLE)).thenReturn(Optional.of(publishedRecord()));
    when(doiDao.findActiveByIdentifierAndType(HANDLE, IdentifierType.PIDINST_B2INST))
        .thenReturn(Optional.empty());
    when(instrumentTemplateDao.findLockedTemplateByName("Instrument (PIDINST 1.0)"))
        .thenReturn(Optional.of(lockedTemplate()));
    ApiInstrument created = new ApiInstrument();
    created.setId(5L);
    created.setGlobalId("IN5");
    when(instrumentApiMgr.createNewApiInstrument(any(ApiInstrument.class), eq(user)))
        .thenReturn(created);
    when(identifierMgr.linkExternalIdentifier(
            eq(new GlobalIdentifier("IN5")), any(ApiInventoryDOI.class), eq(user)))
        .thenReturn(created);
    ApiContainerLocation location = new ApiContainerLocation();
    location.setCoordX(2);
    location.setCoordY(3);

    manager.importInstrument(HANDLE, new ApiTargetLocation(12L, location), user);

    ArgumentCaptor<ApiInstrument> toCreate = ArgumentCaptor.forClass(ApiInstrument.class);
    verify(instrumentApiMgr).createNewApiInstrument(toCreate.capture(), eq(user));
    assertEquals(
        12L,
        toCreate.getValue().getParentContainer().getId(),
        "the container is translated the way a plain instrument POST translates it");
    assertEquals(location, toCreate.getValue().getParentLocation());
  }
}
