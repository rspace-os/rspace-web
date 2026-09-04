package com.researchspace.service.inventory;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.researchspace.api.v1.model.ApiInstrument;
import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.api.v1.model.ApiInventoryDOI.ApiExternalMetadataUpdate;
import com.researchspace.api.v1.model.ApiInventoryDOI.ApiExternalMetadataUpdate.Outcome;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pushes an instrument's freshly remapped PIDINST metadata to the provider records registered for
 * it, as part of the ordinary instrument save (RSDEV-1251, ADR 0008; CONTEXT.md, "External metadata
 * update").
 *
 * <p>Deliberately <strong>not</strong> a {@code *Manager}, and do not rename it to one: the {@code
 * *..service.inventory.*Manager} pointcut would wrap every method in a transaction, and the point
 * of this class is that the provider HTTP call happens outside one. The payload rebuild does need a
 * transaction ({@link RspaceToExternalProviderAdapter} is {@code Propagation.MANDATORY}), so the
 * boundary is opened narrowly with a {@link TransactionTemplate} rather than
 * {@code @Transactional}, which a sibling method of the same bean would bypass.
 *
 * <p>Nothing here writes to the database, and a push failure is reported and audited, never
 * rethrown: the instrument edit has already committed and must stay committed.
 */
@Slf4j
@Service
public class InventoryIdentifierExternalUpdateService {

  /**
   * The one B2INST state with no writable draft left: acceptance publishes the record and removes
   * it (ADR 0008).
   *
   * <p>An exclusion rather than an inclusion list of {@code draft} and {@code submitted}, because
   * {@code refreshIdentifier} stores the review status verbatim, so an identifier can also sit in
   * {@code created}, {@code cancelled}, {@code declined} or {@code expired} - each with a live
   * draft that the narrower rule would have left to drift for good. All confirmed writable against
   * b2inst-test.gwdg.de, August 2026.
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
    // REQUIRES_NEW, not the default REQUIRED, which would silently join a caller's transaction:
    // setReadOnly would be ignored, the boundary would stay open across the provider HTTP call,
    // and a build failure would mark the caller rollback-only and lose the instrument save.
    this.readOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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

  /** How the record's identifiers divide up, worked out in one read-only transaction. */
  private record Classified(List<ApiInventoryDOI> pushable, List<ApiInventoryDOI> frozenByState) {}

  /**
   * Rebuilds and pushes the metadata of every identifier of the instrument whose provider record
   * can still be rewritten, then records the outcome on the caller's copy of that identifier so the
   * response carries it.
   *
   * <p>Must be called after the update transaction has committed, from a non-transactional caller.
   * That is now enforced rather than merely asked for: see the guard below.
   *
   * <p>A writable record is pushed and reported. One frozen by its own state is reported without
   * being pushed, since "nothing was sent, and here is why" is what the acceptance criteria ask
   * for. One that could not be pushed in any case - no record id, no state, an IGSN, a switched-off
   * integration - is passed over in silence, because a sentence on every save would be noise.
   *
   * @param saved the instrument as saved and about to be returned. Its identifier DTOs are the ones
   *     decorated, but they are not what decides who gets pushed - see {@link
   *     #attachedIdentifiers(ApiInstrument, InstrumentEntity)}.
   */
  public void pushMetadataUpdates(ApiInstrument saved, User user) {
    if (saved == null || saved.getId() == null || nothingCouldBeAttached(saved)) {
      return;
    }
    /*
     * Pushing from inside a caller's transaction is the one failure always-push cannot heal: the
     * batch rolls back locally and the provider is left holding what RSpace no longer has, with no
     * later save to retry from. A bulk batch really does reach here, so InventoryBulkOperationsApi-
     * Controller pushes after its transaction commits instead; this stays as the backstop.
     */
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      log.warn(
          "Skipping the external metadata update of instrument {}: a transaction is already open,"
              + " so a rollback would leave the provider ahead of RSpace.",
          saved.getId());
      return;
    }
    Classified classified = readOnlyTx.execute(status -> classify(saved));
    if (classified == null) {
      return;
    }
    for (ApiInventoryDOI frozen : classified.frozenByState()) {
      report(saved, frozen, notWritableOutcome(frozen));
    }
    for (ApiInventoryDOI doi : classified.pushable()) {
      PendingUpdate pending = buildInItsOwnTransaction(saved.getId(), doi);
      if (pending == null) {
        report(saved, doi, failedOutcome(doi));
        continue;
      }
      ApiExternalMetadataUpdate outcome = push(pending);
      report(saved, pending.identifier(), outcome);
      audit(pending.instrument(), user, outcome);
    }
  }

  /**
   * Whether an empty identifier list can be believed, letting the record read be skipped. Only an
   * unfiltered view lists everything the record has; a filtered one lists none regardless, so it
   * proves nothing and the record has to be asked.
   */
  private static boolean nothingCouldBeAttached(ApiInstrument saved) {
    boolean listsSome = saved.getIdentifiers() != null && !saved.getIdentifiers().isEmpty();
    boolean viewMayHideThem = saved.isLimitedReadItem() || saved.isPublicReadItem();
    return !listsSome && !viewMayHideThem;
  }

  /**
   * Divides the record's identifiers into the ones to push and the ones frozen by their own state,
   * inside a read-only transaction because reading them off the record needs a session.
   *
   * <p>Deliberately does no mapping: the adapter is {@code Propagation.MANDATORY}, so an exception
   * from it would mark this transaction rollback-only and take everything decided here with it. See
   * {@link #buildInItsOwnTransaction(Long, ApiInventoryDOI)}.
   */
  private Classified classify(ApiInstrument saved) {
    InstrumentEntity instrument = instrumentApiMgr.getIfExists(saved.getId());
    List<ApiInventoryDOI> pushable = new ArrayList<>();
    List<ApiInventoryDOI> frozen = new ArrayList<>();
    for (ApiInventoryDOI doi : attachedIdentifiers(saved, instrument)) {
      if (!isPushable(doi)) {
        continue;
      }
      if (isUpdatable(doi)) {
        pushable.add(doi);
      } else {
        frozen.add(doi);
      }
    }
    return new Classified(pushable, frozen);
  }

  /**
   * Remaps one identifier's payload in a transaction of its own, returning null if it could not be
   * built.
   *
   * <p>One boundary per identifier, with the catch outside it, is what isolates a mapping failure.
   * The adapter is {@code Propagation.MANDATORY}, so an exception escaping it marks the surrounding
   * transaction rollback-only and the commit throws {@code UnexpectedRollbackException} however
   * carefully the call was wrapped. Catching inside a shared boundary therefore lost every other
   * identifier's push for that instrument. An instrument realistically carries one, so the extra
   * boundaries cost nothing.
   */
  private PendingUpdate buildInItsOwnTransaction(Long instrumentId, ApiInventoryDOI doi) {
    try {
      return readOnlyTx.execute(
          status -> buildPayload(doi, instrumentApiMgr.getIfExists(instrumentId)));
    } catch (RuntimeException e) {
      log.warn(
          "Could not rebuild the {} payload for identifier {}: {}",
          providerName(typeOf(doi)),
          doi.getDoi(),
          e.getMessage());
      return null;
    }
  }

  /**
   * The identifiers the record actually has, which is what decides who gets pushed.
   *
   * <p>The response's own list is used when it has one, which costs nothing on an ordinary save. An
   * EMPTY one says nothing, though: {@code clearPropertiesForLimitedView} blanks the list entirely
   * for a permission-filtered response, which is exactly the owner-transfer case, and trusting it
   * there skipped the push while the provider kept the previous owner's address. So an empty list
   * falls back to the record. A non-empty one can only have come from an unfiltered view, since
   * filtering blanks rather than trims.
   */
  private List<ApiInventoryDOI> attachedIdentifiers(
      ApiInstrument saved, InstrumentEntity instrument) {
    if (saved.getIdentifiers() != null && !saved.getIdentifiers().isEmpty()) {
      return saved.getIdentifiers().stream().filter(Objects::nonNull).toList();
    }
    return instrument.getActiveIdentifiers().stream()
        .filter(Objects::nonNull)
        .map(ApiInventoryDOI::new)
        .toList();
  }

  /**
   * Whether this deployment could push this identifier at all: it carries a provider record id and
   * a state, and belongs to a PIDINST provider whose integration is switched on.
   *
   * <p>The enablement check matters more here than elsewhere because the push is not something the
   * user asked for: it rides on an ordinary save. Without it, switching PIDINST provider would put
   * a failure sentence on every later save of every instrument still holding a draft.
   */
  private boolean isPushable(ApiInventoryDOI doi) {
    return isNotBlank(doi.getDoi())
        && isNotBlank(doi.getState())
        && isEnabledPidinstProvider(typeOf(doi));
  }

  /**
   * Records an outcome on the caller's own copy of the identifier, matched by id, when they have
   * one.
   *
   * <p>A caller left with a limited view has none, and is told nothing. That is correct rather than
   * a shortcoming: they can no longer see the identifier, so there is nowhere to put the outcome
   * and nothing they could do with it. The push still happened, and is still audited.
   */
  private void report(
      ApiInstrument saved, ApiInventoryDOI source, ApiExternalMetadataUpdate outcome) {
    if (saved.getIdentifiers() == null || source.getId() == null) {
      return;
    }
    saved.getIdentifiers().stream()
        .filter(Objects::nonNull)
        .filter(candidate -> source.getId().equals(candidate.getId()))
        .findFirst()
        .ifPresent(candidate -> candidate.setExternalMetadataUpdate(outcome));
  }

  /**
   * Whether this identifier belongs to a PIDINST provider that is enabled right now. An IGSN is
   * refused here rather than by the state rules: an instrument should never carry one, and mapping
   * it through the instrument adapter would build PIDINST metadata for a sample identifier.
   */
  private boolean isEnabledPidinstProvider(IdentifierType type) {
    if (type == null) {
      return false;
    }
    return switch (type) {
      case PIDINST_B2INST -> b2instConnector.isConfiguredAndEnabled();
      case PIDINST_DATACITE ->
          dataCiteConnector.isDataCiteConfiguredAndEnabled(InventorySettingType.PIDINST);
      case IGSN_DATACITE -> false;
    };
  }

  private static IdentifierType typeOf(ApiInventoryDOI doi) {
    return EnumUtils.getEnum(IdentifierType.class, doi.getDoiType());
  }

  /**
   * Whether the provider record behind this identifier can still be rewritten in place. Everything
   * reaching here is already a known PIDINST type with a non-blank state.
   */
  private static boolean isUpdatable(ApiInventoryDOI doi) {
    IdentifierType type = typeOf(doi);
    if (type == null) {
      return false;
    }
    String state = doi.getState().trim().toLowerCase(Locale.ROOT);
    return switch (type) {
      case PIDINST_B2INST -> !B2INST_PUBLISHED_STATES.contains(state);
      case PIDINST_DATACITE -> DATACITE_UPDATABLE_STATES.contains(state);
      case IGSN_DATACITE -> false;
    };
  }

  /**
   * The "nothing was sent, and here is why" outcome, for a record its own state has frozen.
   *
   * <p>One message per provider, because the two are frozen for different reasons: B2INST has
   * nothing writable left, whereas a DataCite DOI past draft is our own decision (see {@link
   * #DATACITE_UPDATABLE_STATES}) and has Republish as its next step.
   *
   * <p>The state itself is deliberately not interpolated: it is free-form provider text, so it
   * would ship untranslated inside a translated sentence and would not match the label the UI
   * shows. Not audited either, since nothing was sent.
   */
  private ApiExternalMetadataUpdate notWritableOutcome(ApiInventoryDOI doi) {
    IdentifierType type = typeOf(doi);
    String key =
        IdentifierType.PIDINST_B2INST.equals(type)
            ? "errors.inventory.identifier.externalUpdateNotPossibleB2inst"
            : "errors.inventory.identifier.externalUpdateNotPossibleDataCite";
    return new ApiExternalMetadataUpdate(
        false, Outcome.NOT_UPDATABLE, message(key, providerName(type), null));
  }

  /** A mapping failure, reported like a push failure. Not audited: nothing was sent. */
  private ApiExternalMetadataUpdate failedOutcome(ApiInventoryDOI doi) {
    return new ApiExternalMetadataUpdate(
        false,
        Outcome.FAILED,
        message(
            "errors.inventory.identifier.externalUpdateFailed", providerName(typeOf(doi)), null));
  }

  private PendingUpdate buildPayload(ApiInventoryDOI doi, InstrumentEntity instrument) {
    IdentifierType type = typeOf(doi);
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
          true, Outcome.UPDATED, message("inventory.identifier.externalUpdated", provider, null));
    } catch (RuntimeException e) {
      // e.getMessage() only, never the throwable: B2instConnectorImpl redacts the bearer token at
      // a single exit, and in Spring 6 RestClientResponseException.getMessage() embeds the raw
      // response body, so printing the cause chain would put the unredacted body in the log.
      log.warn(
          "External metadata update of {} record {} failed: {}",
          provider,
          pending.providerRecordId(),
          e.getMessage());
      return new ApiExternalMetadataUpdate(
          false,
          Outcome.FAILED,
          message("errors.inventory.identifier.externalUpdateFailed", provider, userSafeDetail(e)));
    }
  }

  /**
   * The provider's own explanation, when there is one fit to show a user.
   *
   * <p>B2INST separates that from its developer-facing message (see {@code
   * B2instConnectionException}), so its reason is passed through. DataCite has no equivalent - its
   * exception carries only a developer sentence asking about prefixes and credentials - so nothing
   * is interpolated there and the localized text stands on its own.
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
   * <p>A direct {@code notify} rather than an application event, because the push runs after the
   * write transaction has committed and {@code InventoryAuditTrail}'s
   * {@code @TransactionalEventListener}s would never fire. The audited object is the instrument
   * entity, which carries {@code @AuditTrailData}; it is detached by now, which is safe because its
   * audited properties read the id and embedded edit info rather than a lazy association.
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
