package com.researchspace.service.inventory;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryDOI.ApiExternalMetadataUpdate;
import com.researchspace.api.v1.model.ApiInventorySystemSettings.InventorySettingType;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.audittrail.AuditAction;
import com.researchspace.model.audittrail.AuditTrailService;
import com.researchspace.model.audittrail.GenericEvent;
import com.researchspace.model.inventory.DigitalObjectIdentifier.IdentifierType;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.MessageSourceUtils;
import com.researchspace.webapp.integrations.b2inst.B2instConnectionException;
import com.researchspace.webapp.integrations.b2inst.B2instConnector;
import com.researchspace.webapp.integrations.datacite.DataCiteConnector;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pushes an instrument's freshly remapped PIDINST metadata to the provider records registered for
 * it, as part of the ordinary instrument save (RSDEV-1251, ADR 0008; CONTEXT.md, "External metadata
 * update").
 *
 * <p>Deliberately <strong>not</strong> a {@code *Manager}: the {@code
 * *..service.inventory.*Manager} pointcut in {@code applicationContext-service.xml} would wrap
 * every method in a transaction, and the whole point of this class is that the provider HTTP call
 * happens outside one. The payload rebuild does need a transaction, because {@link
 * RspaceToExternalProviderAdapter} is {@code Propagation.MANDATORY} and reads the instrument's lazy
 * fields, so the boundary is opened explicitly and narrowly with a read-only {@link
 * TransactionTemplate} and closed again before anything is sent. A {@code @Transactional} method
 * called from a sibling method of the same bean would bypass the proxy and silently have no
 * transaction at all, which is the other reason for the template.
 *
 * <p>Nothing here writes to the database. A push failure is reported and audited, never rethrown to
 * the caller: the instrument edit has already committed and must stay committed.
 */
@Slf4j
@Service
public class InventoryIdentifierExternalUpdateService {

  /**
   * The B2INST states in which there is no writable draft left. Acceptance publishes the record and
   * removes its draft, so this is the one B2INST state an external metadata update cannot reach;
   * updating an accepted record would mean a whole new draft-and-review round (ADR 0008).
   *
   * <p>An exclusion rather than the inclusion list of {@code draft} and {@code submitted} the plan
   * assumed, because {@code refreshIdentifier} stores the community review's status verbatim for
   * anything that is not accepted, so an identifier can also sit in {@code created} (a review PUT
   * but never submitted), {@code cancelled}, {@code declined} or {@code expired} - each with a
   * live, writable draft that the narrower rule would have left to drift for good. Verified against
   * b2inst-test.gwdg.de (August 2026): a full-replace {@code PUT .../draft} answers 200 and bumps
   * {@code revision_id} while the review status is {@code created}, {@code submitted} and {@code
   * cancelled} alike, and cancelling drops the record's own status back to {@code draft}. Erring
   * towards pushing is also the safer direction: a push that should not have happened is reported
   * and changes nothing locally, while one wrongly skipped is silent drift.
   *
   * <p>Lower-cased and compared case-insensitively. {@code DigitalObjectIdentifier.state} is
   * free-form text, written either by RSpace or copied verbatim from a provider response, so a
   * differently-cased value is worth updating rather than silently skipping.
   */
  private static final Set<String> B2INST_PUBLISHED_STATES = Set.of("accepted");

  /**
   * DataCite is the other way round: only {@code draft} is updated here. A {@code findable} DOI is
   * refreshed by the existing Republish, which resends full current metadata, and a retracted
   * ({@code registered}) DOI is left alone because that is the decision of record (ADR 0008), not
   * because DataCite would refuse it.
   */
  private static final Set<String> DATACITE_UPDATABLE_STATES = Set.of("draft");

  @Autowired private InstrumentEntityApiManager instrumentApiMgr;
  @Autowired private RspaceToExternalProviderAdapter rspaceToExternalProviderAdapter;
  @Autowired private B2instConnector b2instConnector;
  @Autowired private DataCiteConnector dataCiteConnector;
  @Autowired private IPropertyHolder properties;
  @Autowired private MessageSourceUtils messages;
  @Autowired private AuditTrailService auditer;

  private TransactionTemplate readOnlyTx;

  @Autowired
  public void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.readOnlyTx = new TransactionTemplate(transactionManager);
    this.readOnlyTx.setReadOnly(true);
  }

  /**
   * One eligible identifier's rebuilt payload, carried from the transactional build out to the
   * push. Exactly one of the two payloads is set, chosen by {@link #type}.
   */
  private record PendingUpdate(
      ApiInventoryDOI identifier,
      InstrumentEntity instrument,
      IdentifierType type,
      String providerRecordId,
      B2instDoi b2instPayload,
      DataCiteDoi dataCitePayload) {}

  /**
   * Rebuilds and pushes the metadata of every identifier of {@code saved} that is in an updatable
   * state, then records the outcome on that identifier's DTO so the response carries it.
   *
   * <p>Must be called after the update transaction has committed, from a non-transactional caller.
   *
   * @param saved the instrument as saved and about to be returned; its identifier DTOs are the ones
   *     decorated, and a push is attempted for every eligible one
   * @param incoming the request body, read only for the assign/delete/register markers that say an
   *     identifier was itself mutated by this same save and so must not be pushed
   */
  public void pushMetadataUpdates(ApiInstrument saved, ApiInstrument incoming, User user) {
    List<ApiInventoryDOI> eligible = eligibleIdentifiers(saved, incoming);
    if (eligible.isEmpty()) {
      return;
    }
    for (PendingUpdate pending : buildPayloads(saved.getId(), eligible)) {
      ApiExternalMetadataUpdate outcome = push(pending);
      pending.identifier().setExternalMetadataUpdate(outcome);
      audit(pending.instrument(), user, outcome);
    }
  }

  /**
   * The identifiers this save should push: attached, carrying a provider record id, in an updatable
   * state for their provider, and not created, assigned or delete-requested by this same save.
   *
   * <p>An identifier the request mutated is excluded because its metadata was just registered or
   * just detached; a delete-requested one is gone from {@code saved} anyway, so the marker check
   * matters for the assign and register cases.
   */
  private List<ApiInventoryDOI> eligibleIdentifiers(ApiInstrument saved, ApiInstrument incoming) {
    if (saved == null || saved.getId() == null || saved.getIdentifiers() == null) {
      return List.of();
    }
    Set<Long> mutatedByThisSave = idsMutatedByThisSave(incoming);
    return saved.getIdentifiers().stream()
        .filter(Objects::nonNull)
        .filter(doi -> doi.getId() == null || !mutatedByThisSave.contains(doi.getId()))
        .filter(doi -> isNotBlank(doi.getDoi()))
        .filter(InventoryIdentifierExternalUpdateService::isUpdatable)
        .toList();
  }

  private Set<Long> idsMutatedByThisSave(ApiInstrument incoming) {
    if (incoming == null || incoming.getIdentifiers() == null) {
      return Set.of();
    }
    return incoming.getIdentifiers().stream()
        .filter(Objects::nonNull)
        .filter(
            doi ->
                doi.isDeleteIdentifierRequest()
                    || doi.isAssignIdentifierRequest()
                    || doi.isRegisterIdentifierRequest())
        .map(ApiInventoryDOI::getId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
  }

  private static boolean isUpdatable(ApiInventoryDOI doi) {
    IdentifierType type = EnumUtils.getEnum(IdentifierType.class, doi.getDoiType());
    if (type == null || StringUtils.isBlank(doi.getState())) {
      return false;
    }
    String state = doi.getState().trim().toLowerCase(Locale.ROOT);
    return switch (type) {
      case PIDINST_B2INST -> !B2INST_PUBLISHED_STATES.contains(state);
      case PIDINST_DATACITE -> DATACITE_UPDATABLE_STATES.contains(state);
      // an instrument should never carry an IGSN; skipped rather than mapped through the
      // instrument adapter, which would build PIDINST metadata for a sample identifier
      case IGSN_DATACITE -> false;
    };
  }

  /**
   * Remaps the instrument's current fields into one payload per eligible identifier, inside a
   * read-only transaction because the mapping adapter demands one and walks lazy associations.
   *
   * <p>The transaction ends when this method returns, so every provider call is made outside it and
   * cannot pin a pooled JDBC connection for the length of an HTTP exchange.
   */
  private List<PendingUpdate> buildPayloads(Long instrumentId, List<ApiInventoryDOI> eligible) {
    List<PendingUpdate> built =
        readOnlyTx.execute(
            status -> {
              InstrumentEntity instrument = instrumentApiMgr.getIfExists(instrumentId);
              if (instrument == null) {
                log.warn(
                    "Skipping the external metadata update of instrument {}: it could not be read"
                        + " back after the save.",
                    instrumentId);
                return List.<PendingUpdate>of();
              }
              return eligible.stream().map(doi -> buildPayload(doi, instrument)).toList();
            });
    return built == null ? List.of() : built;
  }

  private PendingUpdate buildPayload(ApiInventoryDOI doi, InstrumentEntity instrument) {
    IdentifierType type = EnumUtils.getEnum(IdentifierType.class, doi.getDoiType());
    if (IdentifierType.PIDINST_B2INST.equals(type)) {
      return new PendingUpdate(
          doi,
          instrument,
          type,
          doi.getDoi(),
          rspaceToExternalProviderAdapter.buildB2instDoi(instrument, publicLandingPageUrl(doi)),
          null);
    }
    return new PendingUpdate(
        doi,
        instrument,
        type,
        doi.getDoi(),
        null,
        rspaceToExternalProviderAdapter.buildDataCiteDoi(doi, instrument));
  }

  /**
   * The identifier's own public landing page, offered to the B2INST mapping as the LandingPage to
   * register when the instrument's Landing page field holds nothing a user typed (ADR 0006). Read
   * from {@code rsPublicId}, which is where a DTO built from a persisted identifier carries the
   * public link suffix; {@code publicLinkSuffix} is deliberately only ever set for a brand-new one.
   */
  private String publicLandingPageUrl(ApiInventoryDOI doi) {
    return InventoryUrls.publicLandingPageUrl(properties.getServerUrl(), doi.getRsPublicId())
        .orElse(null);
  }

  /**
   * Sends one rebuilt payload. Every failure is caught and turned into a reason: the save has
   * already committed, so a provider outage must not become an HTTP error, and nothing local
   * changes either way.
   */
  private ApiExternalMetadataUpdate push(PendingUpdate pending) {
    String provider = providerName(pending.type());
    try {
      if (IdentifierType.PIDINST_B2INST.equals(pending.type())) {
        b2instConnector.updateDraftDoi(pending.providerRecordId(), pending.b2instPayload());
      } else {
        dataCiteConnector.updateDoi(pending.dataCitePayload(), InventorySettingType.PIDINST);
      }
      return new ApiExternalMetadataUpdate(
          true, message("inventory.identifier.externalUpdated", provider, null));
    } catch (RuntimeException e) {
      log.warn(
          "External metadata update of {} record {} failed: {}",
          provider,
          pending.providerRecordId(),
          e.getMessage(),
          e);
      return new ApiExternalMetadataUpdate(
          false,
          message("errors.inventory.identifier.externalUpdateFailed", provider, userSafeDetail(e)));
    }
  }

  /**
   * The provider's own explanation, when there is one fit to show a user.
   *
   * <p>The B2INST connector already separates that from its developer-facing message (see {@code
   * B2instConnectionException}), so its reason is passed through. Nothing equivalent exists on the
   * DataCite side: {@code DataCiteConnectionException} carries only a developer sentence, and the
   * client's three canned messages ask the reader about repository prefixes and credentials, so the
   * detail is left out rather than shown. The localized text stands on its own without it.
   */
  private String userSafeDetail(RuntimeException e) {
    return e instanceof B2instConnectionException b2instError ? b2instError.getReason() : null;
  }

  /**
   * Resolves the localized reason, collapsing the whitespace left where an absent provider detail
   * would have gone and any line breaks a provider's own message brought with it.
   */
  private String message(String key, String provider, String detail) {
    return StringUtils.normalizeSpace(
        messages.getMessage(key, new Object[] {provider, StringUtils.defaultString(detail)}));
  }

  private static String providerName(IdentifierType type) {
    return IdentifierType.PIDINST_B2INST.equals(type) ? "B2INST" : "DataCite";
  }

  /**
   * Audits the attempt, successful or not, so the drift window between a failed push and the next
   * save is visible after the fact.
   *
   * <p>A direct {@code notify} rather than an application event: the push runs after the write
   * transaction has committed, and {@code InventoryAuditTrail}'s listeners are
   * {@code @TransactionalEventListener}s, which would never fire. The audited object is the
   * instrument entity, because {@code @AuditTrailData} lives there and not on the API DTO; it is
   * detached by now, which is safe since its two audited properties ({@code getName} and {@code
   * getGlobalIdentifier}) read the id and the embedded edit info rather than a lazy association.
   */
  private void audit(InstrumentEntity instrument, User user, ApiExternalMetadataUpdate outcome) {
    try {
      auditer.notify(new GenericEvent(user, instrument, AuditAction.WRITE, outcome.getReason()));
    } catch (RuntimeException e) {
      log.warn(
          "Could not audit the external metadata update of instrument {}",
          instrument.getGlobalIdentifier(),
          e);
    }
  }
}
