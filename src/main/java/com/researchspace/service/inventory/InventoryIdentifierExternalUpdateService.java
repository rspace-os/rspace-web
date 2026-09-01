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
    // REQUIRES_NEW, not the default REQUIRED: this class's contract is that the push happens
    // outside any transaction, and REQUIRED would silently join a caller's instead of enforcing
    // that. Joined, setReadOnly above is ignored, the boundary never closes so the provider HTTP
    // exchange holds a pooled JDBC connection, and a build failure would mark the caller's
    // transaction rollback-only and lose the instrument save the push is meant not to affect.
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
   * <p>Every identifier the record carries ends up in one of three places. One whose provider
   * record is writable is pushed and the result reported. One whose record is frozen by its own
   * state is not pushed but is still reported, because "nothing was sent, and here is why" is the
   * answer the acceptance criteria ask for and silence is not. One this deployment could not push
   * in any case - no provider record id, no state, an IGSN, or a provider integration that is
   * switched off - is passed over in silence, because there is nothing the user could act on and a
   * sentence on every save would be noise.
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
     * The contract of this class is a caller with no transaction open, and it really is reachable
     * with one: bulk CHANGE_OWNER with rollbackOnError runs the whole batch inside a single
     * transaction (InventoryBulkOperationApiManager) and calls back into
     * InstrumentsApiController.changeInstrumentOwner, which pushes.
     *
     * Pushing from there would be wrong in the one direction always-push cannot heal. A later record
     * in the batch fails, the owner change rolls back locally, and the provider is left holding an
     * owner RSpace no longer has - the mirror image of the failure this design accepts, and not
     * self-correcting, because there is no later save to retry from. It would also defeat the
     * boundary: REQUIRES_NEW would suspend the caller's transaction and hold its connection for the
     * length of the provider exchange.
     *
     * So decline. The metadata drifts until the next ordinary save of that instrument, which is the
     * recoverable direction and exactly what a failed push already leaves behind.
     */
    if (TransactionSynchronizationManager.isActualTransactionActive()) {
      log.warn(
          "Skipping the external metadata update of instrument {}: a transaction is already open,"
              + " so the provider call would run inside it and a rollback would leave the provider"
              + " ahead of RSpace. The next ordinary save of this instrument will push.",
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
   * Whether the response can be believed when it lists no identifiers, letting the record read
   * below be skipped. An unfiltered view lists every identifier the record has, so an empty list
   * there really does mean there is nothing to push - the ordinary case, and the reason an
   * instrument without identifiers costs no extra read. A filtered view lists none whatever the
   * record holds, so it says nothing and has to be checked against the record.
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
   * <p>Deliberately does no mapping. The mapping adapter is {@code Propagation.MANDATORY}, so an
   * exception from it marks this transaction rollback-only, and anything else decided in the same
   * boundary would be lost with it - see {@link #buildInItsOwnTransaction(Long, ApiInventoryDOI)}.
   *
   * <p>If the instrument itself cannot be read back, {@code getIfExists} throws {@code
   * NotFoundException} and it propagates to the controller's guard, which logs it and leaves the
   * response unannotated. That is a save racing a deletion, not something a user can act on.
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
   * <p>One boundary per identifier, and the catch outside it, is what actually isolates a mapping
   * failure. The adapter is {@code Propagation.MANDATORY}, so an exception escaping it marks the
   * surrounding transaction rollback-only ({@code globalRollbackOnParticipationFailure} defaults to
   * true) and the commit throws {@code UnexpectedRollbackException} however carefully the call
   * itself was wrapped - the hazard the adapter's own javadoc predicts. Catching inside a shared
   * boundary therefore discarded every other identifier's push, report and audit for that
   * instrument, which is the opposite of what ADR 0008 promises. An instrument realistically
   * carries one identifier, so the extra boundaries cost nothing.
   *
   * <p>The transaction closes before this returns, so the provider call the caller makes next is
   * outside it and cannot pin a pooled JDBC connection for the length of an HTTP exchange.
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
   * <p>The response's own list is used when it has one. That is the ordinary save, where the list
   * is already built from the record, so nothing extra is read. It cannot be trusted when empty,
   * though: a permission-filtered response carries no identifiers whatever the record holds,
   * because {@code ApiInventoryRecordInfo.clearPropertiesForLimitedView} blanks its lists. That is
   * exactly the owner-transfer case - the caller is the owner giving the instrument away, left with
   * {@code LIMITED_READ} - and reading candidates from there skipped the push silently while the
   * registered record kept the previous owner's contact address. An empty list is therefore
   * ambiguous and the record is asked instead. A non-empty one can only have come from an
   * unfiltered view, since filtering blanks the list entirely rather than trimming it, so it is
   * safe to take.
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
   * <p>The enablement check is what every other identifier operation does first (see {@code
   * ApiAvailabilityHandler}), and it matters more here because this push is not something the user
   * asked for: it rides on an ordinary save. Without it, disabling PIDINST - or switching provider,
   * which disables the sibling automatically - would put a failure sentence on every later save of
   * every instrument still holding a draft, and audit a write each time, with nothing the user
   * could do to stop it.
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
   * <p>One message per provider, because the two are frozen for different reasons and a single
   * sentence could only be right about one of them. B2INST really has nothing writable left once a
   * community review is accepted. A DataCite DOI past draft is a decision of record, not a provider
   * refusal (see {@link #DATACITE_UPDATABLE_STATES}), and it has a next step the user can take, so
   * saying "no record open for changes" there would be both wrong and a dead end. The split follows
   * the {@code b2instRegisterNoDraft} / {@code dataCiteRegisterNoDraft} pair already in this
   * catalogue.
   *
   * <p>The state itself is deliberately not interpolated. It is free-form text written by the
   * provider, so it would ship untranslated inside a translated sentence, and it would not even
   * match what the user is shown: the UI maps every state to its own catalogue label.
   *
   * <p>Not audited: nothing was sent and nothing changed, so there is no synchronisation to record.
   */
  private ApiExternalMetadataUpdate notWritableOutcome(ApiInventoryDOI doi) {
    IdentifierType type = typeOf(doi);
    String key =
        IdentifierType.PIDINST_B2INST.equals(type)
            ? "errors.inventory.identifier.externalUpdateNotPossibleB2inst"
            : "errors.inventory.identifier.externalUpdateNotPossibleDataCite";
    return new ApiExternalMetadataUpdate(false, message(key, providerName(type), null));
  }

  /** A mapping failure, reported like a push failure. Not audited: nothing was sent. */
  private ApiExternalMetadataUpdate failedOutcome(ApiInventoryDOI doi) {
    return new ApiExternalMetadataUpdate(
        false,
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
          true, message("inventory.identifier.externalUpdated", provider, null));
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
