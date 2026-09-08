package com.researchspace.service.inventory.impl;

import com.researchspace.api.v1.model.ApiContainerInfo;
import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryRecordInfo;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.api.v1.model.ApiPidinstRecord;
import com.researchspace.api.v1.model.ApiPidinstSearchResult;
import com.researchspace.api.v1.model.ApiTargetLocation;
import com.researchspace.b2inst.model.response.B2instDraftRecord;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
      hits.getData().stream()
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
    // one page, whatever it was assembled from: the B2INST branch unions two searches of MAX_HITS
    if (result.getHits().size() > MAX_HITS) {
      result.getHits().subList(MAX_HITS, result.getHits().size()).clear();
    }
    for (ApiPidinstRecord hit : result.getHits()) {
      hit.setLinkedInstrumentGlobalId(linkedInstrumentOf(hit.getPid(), provider).orElse(null));
    }
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
   * The B2INST half of a free-text search: the published index and the account's own records,
   * merged and deduplicated by PID.
   *
   * <p>Two calls because InvenioRDM keeps unpublished records out of the published index, while an
   * import accepts a PID in any review status, so a draft or submitted record would otherwise be
   * impossible to find by name. The account's own PUBLISHED records come back from both, hence the
   * deduplication; {@code total} is the two provider totals less what was seen twice, which is
   * exact while both pages fit and an over-estimate once they do not - the direction that tells a
   * user to narrow the query rather than hiding hits from them.
   */
  private void searchB2inst(String query, ApiPidinstSearchResult result) {
    B2instSearchResult published = b2instConnector.searchRecords(query, MAX_HITS);
    B2instSearchResult own = b2instConnector.searchUserRecords(query, MAX_HITS);
    Map<String, ApiPidinstRecord> byPid = new LinkedHashMap<>();
    int duplicates = 0;
    for (B2instSearchResult page : List.of(published, own)) {
      for (B2instDraftRecord record : page.getHits().getHits()) {
        ApiPidinstRecord mapped = PidinstRecordMapper.fromB2inst(record);
        if (mapped.getPid() == null) {
          continue;
        }
        if (byPid.put(mapped.getPid(), mapped) != null) {
          duplicates++;
        }
      }
    }
    result.getHits().addAll(byPid.values());
    result.setTotal(Math.max(byPid.size(), total(published) + total(own) - duplicates));
  }

  /** A provider total, falling back to the page size when the provider reports none. */
  private static int total(B2instSearchResult page) {
    Integer total = page.getHits().getTotal();
    return total == null ? page.getHits().getHits().size() : total;
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
      // No filter on is_published: a PID may be imported whatever its review status, and the
      // mapper carries the provider's own status onto the linked identifier (RSDEV-1326).
      return b2instConnector
          .getRecordByHandle(pid)
          .map(PidinstRecordMapper::fromB2inst)
          .filter(record -> record.getPid() != null);
    }
    return dataCiteConnector
        .findDoi(pid, InventorySettingType.PIDINST)
        .filter(PidinstLookupManagerImpl::isInstrumentDoi)
        .map(PidinstRecordMapper::fromDataCite)
        .filter(record -> record.getPid() != null);
  }

  private static boolean isInstrumentDoi(DataCiteDoi doi) {
    return doi.getAttributes() != null
        && doi.getAttributes().getTypes() != null
        && PidinstRecordMapper.RESOURCE_TYPE_INSTRUMENT.equalsIgnoreCase(
            doi.getAttributes().getTypes().getResourceTypeGeneral());
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
