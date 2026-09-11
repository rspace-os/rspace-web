package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.api.v1.model.ApiPidinstRecord;
import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.b2inst.model.response.B2instSearchResult;
import com.researchspace.dao.DigitalObjectIdentifierDao;
import com.researchspace.dao.InstrumentTemplateDao;
import com.researchspace.dao.customliquibaseupdates.CreateDefaultInstrumentTemplate_RSDEV1219;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiSearchResult;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InstrumentTemplate;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.service.inventory.InstrumentEntityApiManager;
import com.researchspace.service.inventory.InventoryIdentifierApiManager;
import com.researchspace.service.inventory.PidinstAlreadyLinkedException;
import com.researchspace.service.inventory.PidinstLookupManager;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import jakarta.ws.rs.NotFoundException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** See {@link PidinstLookupManager}. */
@Slf4j
@Service("pidinstLookupManager")
public class PidinstLookupManagerImpl implements PidinstLookupManager {

  /** The only DataCite state a lookup offers: a publicly resolvable DOI. */
  static final String STATE_FINDABLE = "findable";

  /** A DOI, bare or behind a doi.org resolver; group 1 is the bare DOI. */
  static final Pattern DOI_QUERY =
      Pattern.compile(
          "^(?:https?://(?:dx\\.)?doi\\.org/)?(10\\.\\d{4,9}/\\S+)$", Pattern.CASE_INSENSITIVE);

  /** A Handle under an ePIC prefix (B2INST mints 21.xxx), bare or behind hdl.handle.net. */
  static final Pattern HANDLE_QUERY =
      Pattern.compile(
          "^(?:https?://hdl\\.handle\\.net/)?(21\\.[A-Za-z0-9.]+/\\S+)$", Pattern.CASE_INSENSITIVE);

  @Autowired private B2instConnector b2instConnector;
  @Autowired private DataCiteConnector dataCiteConnector;
  @Autowired private DigitalObjectIdentifierDao doiDao;
  @Autowired private InstrumentTemplateDao instrumentTemplateDao;
  @Autowired private InstrumentEntityApiManager instrumentApiMgr;
  @Autowired private InventoryIdentifierApiManager identifierMgr;
  @Autowired private MessageSourceUtils messages;

  @Override
  public ApiPidinstSearchResult search(String query, User user) {
    IdentifierType provider = enabledProvider();
    String q = query.trim();
    ApiPidinstSearchResult result = new ApiPidinstSearchResult();
    result.setProvider(provider.name());
    if (isPidOfTheOtherRegistry(q, provider)) {
      // decision 5: a DOI on a B2INST deployment (or a Handle on DataCite) cannot be resolved here
      return result;
    }
    Optional<String> pid = pidOf(q, provider);
    if (pid.isPresent()) {
      fetchByPid(pid.get(), provider).ifPresent(result.getHits()::add);
      result.setTotal(result.getHits().size());
    } else if (provider == IdentifierType.PIDINST_B2INST) {
      searchB2inst(q, result);
    } else {
      DataCiteDoiSearchResult hits =
          dataCiteConnector.searchInstrumentDois(q, MAX_HITS, InventorySettingType.PIDINST);
      // re-checked here as well as asked for in the request, so the rule holds whatever the index
      // returns (ADR 0009), and so this path cannot offer what fetchByPid would refuse
      hits.getData().stream()
          .filter(PidinstLookupManagerImpl::isInstrumentDoi)
          .filter(PidinstLookupManagerImpl::isFindable)
          .map(PidinstRecordMapper::fromDataCite)
          .filter(record -> record.getPid() != null)
          .forEach(result.getHits()::add);
      result.setTotal(hits.getMeta().getTotal());
    }
    result
        .getHits()
        .sort(
            Comparator.comparing(
                hit -> StringUtils.defaultString(hit.getName()).toLowerCase(Locale.ROOT)));
    annotateLinkedInstruments(result.getHits(), provider);
    return result;
  }

  @Override
  public ApiInstrument importInstrument(
      String pid, ApiTargetLocation newTargetLocation, User user) {
    IdentifierType provider = enabledProvider();
    ApiPidinstRecord record =
        pidOf(pid.trim(), provider)
            .flatMap(bare -> fetchByPid(bare, provider))
            .orElseThrow(
                () ->
                    new NotFoundException(
                        messages.getMessage(
                            "errors.inventory.identifier.pidinstNotFound", new Object[] {pid})));
    linkedInstrumentOf(record.getPid(), provider)
        .ifPresent(
            globalId -> {
              throw new PidinstAlreadyLinkedException(
                  messages.getMessage(
                      "errors.inventory.identifier.pidinstAlreadyLinked", new Object[] {globalId}),
                  globalId);
            });
    InstrumentTemplate template =
        instrumentTemplateDao
            .findLockedTemplateByName(CreateDefaultInstrumentTemplate_RSDEV1219.TEMPLATE_NAME)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "The locked default instrument template '"
                            + CreateDefaultInstrumentTemplate_RSDEV1219.TEMPLATE_NAME
                            + "' is missing: the RSDEV-1219 Liquibase seeder has not run on this"
                            + " database"));
    ApiInstrument toCreate = PidinstRecordMapper.toApiInstrument(record, template);
    applyTargetLocation(toCreate, newTargetLocation);
    ApiInstrument created = instrumentApiMgr.createNewApiInstrument(toCreate, user);
    ApiInventoryDOI link = PidinstRecordMapper.toLinkedIdentifier(record, user);
    ApiInventoryRecordInfo linked =
        identifierMgr.linkExternalIdentifier(
            new GlobalIdentifier(created.getGlobalId()), link, user);
    log.info("Imported {} from {} as {}", record.getPid(), provider, created.getGlobalId());
    return (ApiInstrument) linked;
  }

  /**
   * The B2INST half of a free-text search: one query against the PUBLISHED index.
   *
   * <p>The account's own records under {@code /api/user/records} are deliberately not searched.
   * Only a public PID may be linked (RSDEV-1326), so a record still in draft, submitted or declined
   * is not a candidate, and the published index is exactly the set that is. Hits are filtered on
   * {@code is_published} as well, so the rule holds in RSpace whatever the index returns, and
   * {@code total} stays the provider's own.
   */
  private void searchB2inst(String query, ApiPidinstSearchResult result) {
    B2instSearchResult page = b2instConnector.searchRecords(query, MAX_HITS);
    page.getHits().getHits().stream()
        .filter(record -> Boolean.TRUE.equals(record.getIsPublished()))
        .map(PidinstRecordMapper::fromB2inst)
        .filter(record -> record.getPid() != null)
        .forEach(result.getHits()::add);
    Integer total = page.getHits().getTotal();
    result.setTotal(total == null ? result.getHits().size() : total);
  }

  private IdentifierType enabledProvider() {
    if (b2instConnector.isConfiguredAndEnabled()) {
      return IdentifierType.PIDINST_B2INST;
    }
    if (dataCiteConnector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST)) {
      return IdentifierType.PIDINST_DATACITE;
    }
    // the controllers' availability gate answers this first; kept so the manager is safe to call
    // directly, and phrased with the same key the gate uses
    throw new UnsupportedOperationException(
        messages.getMessage(
            "errors.inventory.identifier.integrationNotEnabled", new Object[] {"PIDINST"}));
  }

  /** The bare PID when the query has the enabled provider's PID shape; empty means free text. */
  private static Optional<String> pidOf(String query, IdentifierType provider) {
    Matcher matcher = pidPattern(provider).matcher(query);
    return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
  }

  private static boolean isPidOfTheOtherRegistry(String query, IdentifierType provider) {
    IdentifierType other =
        provider == IdentifierType.PIDINST_B2INST
            ? IdentifierType.PIDINST_DATACITE
            : IdentifierType.PIDINST_B2INST;
    return pidPattern(other).matcher(query).matches();
  }

  private static Pattern pidPattern(IdentifierType provider) {
    return provider == IdentifierType.PIDINST_B2INST ? HANDLE_QUERY : DOI_QUERY;
  }

  private Optional<ApiPidinstRecord> fetchByPid(String pid, IdentifierType provider) {
    if (provider == IdentifierType.PIDINST_B2INST) {
      return b2instConnector
          .getRecordByHandle(pid)
          .filter(record -> Boolean.TRUE.equals(record.getIsPublished()))
          .map(PidinstRecordMapper::fromB2inst)
          .filter(record -> record.getPid() != null)
          // getRecordByHandle resolves the suffix alone, so any well-formed prefix reaches the same
          // record: 21.FAKE/abc would otherwise answer with the real 21.T11998/abc. Handles are
          // case-insensitive by spec, so the comparison is too.
          .filter(record -> pid.equalsIgnoreCase(record.getPid()));
    }
    return dataCiteConnector
        .findDoi(pid, InventorySettingType.PIDINST)
        .filter(PidinstLookupManagerImpl::isInstrumentDoi)
        .filter(PidinstLookupManagerImpl::isFindable)
        .map(PidinstRecordMapper::fromDataCite)
        .filter(record -> record.getPid() != null);
  }

  /**
   * Whether the DOI resolves publicly. Only a findable DOI may be linked (RSDEV-1326): a {@code
   * draft} or {@code registered} DOI has no public landing page, so linking one would put an
   * address in the Identifiers card that answers nothing. A DOI of another repository that is not
   * findable never reaches here, because DataCite answers 404 for it.
   */
  private static boolean isFindable(DataCiteDoi doi) {
    return doi.getAttributes() != null
        && STATE_FINDABLE.equalsIgnoreCase(doi.getAttributes().getState());
  }

  private static boolean isInstrumentDoi(DataCiteDoi doi) {
    return doi.getAttributes() != null
        && doi.getAttributes().getTypes() != null
        && PidinstRecordMapper.RESOURCE_TYPE_INSTRUMENT.equalsIgnoreCase(
            doi.getAttributes().getTypes().getResourceTypeGeneral());
  }

  /**
   * Stamps every hit with the instrument already linking its PID, in one query rather than one per
   * hit: a full page is {@link PidinstLookupManager#MAX_HITS} rows, and none of it is cached with
   * the provider page because link status is local and changes independently of it.
   */
  private void annotateLinkedInstruments(List<ApiPidinstRecord> hits, IdentifierType provider) {
    List<String> pids =
        hits.stream().map(ApiPidinstRecord::getPid).filter(Objects::nonNull).toList();
    Map<String, String> linkedByPid = new HashMap<>();
    for (DigitalObjectIdentifier identifier :
        doiDao.findActiveByIdentifiersAndType(pids, provider)) {
      // rows come oldest first, and putIfAbsent keeps the oldest, which is the row the
      // single-identifier query would have returned should two ever exist
      linkedByPid.putIfAbsent(
          identifier.getIdentifier(), identifier.getConnectedRecordGlobalIdentifier());
    }
    hits.forEach(hit -> hit.setLinkedInstrumentGlobalId(linkedByPid.get(hit.getPid())));
  }

  private Optional<String> linkedInstrumentOf(String pid, IdentifierType provider) {
    return doiDao
        .findActiveByIdentifierAndType(pid, provider)
        .map(DigitalObjectIdentifier::getConnectedRecordGlobalIdentifier);
  }

  /** Same translation {@code InstrumentsApiController.createNewInstrument} applies to a POST. */
  private static void applyTargetLocation(ApiInstrument toCreate, ApiTargetLocation target) {
    if (target == null) {
      return;
    }
    ApiContainerInfo parentContainer = new ApiContainerInfo();
    parentContainer.setId(target.getContainerId());
    toCreate.setParentContainer(parentContainer);
    toCreate.setParentLocation(target.getContainerLocation());
  }
}
