package com.researchspace.service.inventory.impl;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import com.researchspace.api.v1.model.ApiInventoryDOI;
import com.researchspace.b2inst.model.common.B2instAccess;
import com.researchspace.b2inst.model.common.B2instFilesOptions;
import com.researchspace.b2inst.model.metadata.B2instAlternateIdentifier;
import com.researchspace.b2inst.model.metadata.B2instDate;
import com.researchspace.b2inst.model.metadata.B2instInstrumentMetadata;
import com.researchspace.b2inst.model.metadata.B2instInstrumentType;
import com.researchspace.b2inst.model.metadata.B2instManufacturer;
import com.researchspace.b2inst.model.metadata.B2instModel;
import com.researchspace.b2inst.model.metadata.B2instOwner;
import com.researchspace.b2inst.model.metadata.B2instRelatedIdentifier;
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.datacite.model.DataCiteDoiAttributes;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.model.inventory.field.InventoryLink;
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.inventory.InventoryUrls;
import com.researchspace.service.inventory.RspaceToExternalProviderAdapter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** See {@link RspaceToExternalProviderAdapter}. */
@Slf4j
public class RspaceToExternalProviderAdapterImpl implements RspaceToExternalProviderAdapter {

  private static final String PIDINST_SCHEMA_VERSION = "1.0";
  private static final String PUBLIC_ACCESS = "public";

  /*
   * Canonical PIDINST-mapped field names: the default "Instrument (PIDINST 1.0)" template's
   * spelling, matched case-insensitively on the trimmed field name AND on the template's field
   * type (see CONTEXT.md, "PIDINST-mapped field").
   */
  private static final String FIELD_OWNER = "Owner";
  private static final String FIELD_MANUFACTURER = "Manufacturer";
  private static final String FIELD_MODEL = "Model";
  private static final String FIELD_INSTRUMENT_TYPE = "Instrument type";
  private static final String FIELD_COMMISSIONED = "Commissioned";
  private static final String FIELD_DECOMMISSIONED = "Decommissioned";
  private static final String FIELD_MEASURED_QUANTITY = "Measured quantity";
  private static final String FIELD_ALTERNATE_IDENTIFIER = "Alternate Identifier";
  private static final String FIELD_MEASUREMENT_TECHNIQUE = "Measurement technique";
  private static final String FIELD_CALIBRATION = "Calibration";

  // PIDINST controlled values ("DeCommissioned" deliberately differs from the field name).
  private static final String DATE_TYPE_COMMISSIONED = "Commissioned";
  private static final String DATE_TYPE_DECOMMISSIONED = "DeCommissioned";
  private static final String ALTERNATE_ID_TYPE_OTHER = "Other";

  // Wire values fixed by RSDEV-1253 (see ADR 0007). The labels are the ticket's spelling, capital
  // T, not the template field name's. IsDescribedBy is sent whatever relation the link stores,
  // because PIDINST's RelatedIdentifier vocabulary has no IsDocumentedBy/IsCalibratedBy.
  private static final String RELATED_ID_NAME_MEASUREMENT_TECHNIQUE = "Measurement Technique";
  private static final String RELATED_ID_NAME_CALIBRATION = "Calibration";
  private static final String RELATION_TYPE_IS_DESCRIBED_BY = "IsDescribedBy";
  private static final String RELATED_ID_TYPE_URL = "URL";

  /**
   * Link target types whose version-suffixed globalId resolves to that version, mirroring {@code
   * GlobalLookupController.VERSIONED_INVENTORY_PREFIXES}, which is the authority. A pin on any
   * other allowed target is deliberately not registered: NB has no versioned route at all, and SD's
   * and GL's lead to an audit view and a file stream rather than the record's page, so for those
   * the unpinned address stays the safer thing to make permanent.
   */
  private static final Set<GlobalIdPrefix> VERSION_PINNABLE_TARGETS =
      EnumSet.of(
          GlobalIdPrefix.SA,
          GlobalIdPrefix.SS,
          GlobalIdPrefix.IC,
          GlobalIdPrefix.IT,
          GlobalIdPrefix.IN,
          GlobalIdPrefix.NT);

  /** Deployment configuration; the server URL feeds the related-identifier addresses. */
  private final IPropertyHolder properties;

  public RspaceToExternalProviderAdapterImpl(IPropertyHolder properties) {
    this.properties = properties;
  }

  /*
   * MANDATORY, not REQUIRED: this walks the instrument's lazy associations (getActiveFields, and the
   * link targets behind them), so it needs a session that is already open and must not quietly start
   * one of its own. This class is a Spring bean whose name does not end in Manager, so it matches
   * none of the AOP advisors in applicationContext-service.xml; without this annotation the contract
   * is invisible and a future non-transactional caller (a controller, an @Async handler, a Quartz
   * job) would get a LazyInitializationException far from the cause. MANDATORY turns that into an
   * immediate, self-explanatory failure instead. <tx:annotation-driven> is enabled and
   * TransactionAdviceStartupCheck verifies at startup that the advice was actually applied.
   *
   * No readOnly flag: it configures only a transaction the advice itself starts, and MANDATORY always
   * joins the caller's, so it would never be applied and would read as a write guard that guards
   * nothing. One real side effect of the advice being here: an exception escaping this method now
   * marks the shared transaction rollback-only, so a future caller that catches the
   * IllegalArgumentException below to skip one record would get UnexpectedRollbackException at commit.
   */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public B2instDoi buildB2instDoi(InventoryRecord instrument, String publicLandingPageUrl) {
    if (instrument == null || !instrument.isInstrument()) {
      throw new IllegalArgumentException(
          "B2INST instrument PIDs can only be built for Instrument records (IN*)");
    }
    InstrumentEntity source = (InstrumentEntity) instrument;

    B2instInstrumentMetadata metadata = new B2instInstrumentMetadata();
    metadata.setName(source.getName());
    metadata.setSchemaVersion(PIDINST_SCHEMA_VERSION);
    metadata.setOwner(List.of(ownerOf(source)));
    if (isNotBlank(source.getDescription())) {
      metadata.setDescription(source.getDescription());
    }
    mappedFieldData(source, FIELD_MANUFACTURER, FieldType.STRING)
        .ifPresent(m -> metadata.setManufacturer(List.of(new B2instManufacturer(m, null))));
    mappedFieldData(source, FIELD_MODEL, FieldType.STRING)
        .ifPresent(m -> metadata.setModel(new B2instModel(m, null)));
    mappedFieldData(source, FIELD_INSTRUMENT_TYPE, FieldType.STRING)
        .ifPresent(t -> metadata.setInstrumentType(List.of(new B2instInstrumentType(t))));
    metadata.setDate(nullIfEmpty(dates(source)));
    metadata.setMeasuredVariable(nullIfEmpty(measuredVariables(source)));
    /*
     * The registered landing page: the user's own value when they typed one, otherwise the
     * identifier's public landing page. A legacy auto-filled landing page is recognised and never
     * registered: it needs an RSpace sign-in, and a LandingPage is baked into a citable PID once a
     * curator accepts, with no way to update the published record afterwards. With neither a typed
     * value nor a public URL the property is omitted: a missing property is recoverable, a wrong
     * published URL is not. See ADR 0006 and CONTEXT.md ("Registered landing page").
     */
    Optional<String> typed = PidinstFields.userTypedLandingPage(source);
    Optional<String> registrableTyped = typed.filter(PidinstFields::isResolvableAddress);
    if (typed.isPresent() && registrableTyped.isEmpty()) {
      // Discarding a value the user typed is worth saying out loud: the field goes on displaying
      // it,
      // so nothing else tells them it was not the one registered. The value itself is not logged,
      // only the record it belongs to. Deliberately silent on what replaced it - that is decided
      // below and reported there, so the two lines cannot contradict each other.
      log.warn(
          "Not registering the Landing page of {} as its LandingPage: the value is not an absolute"
              + " http(s) address.",
          source.getGlobalIdentifier());
    }
    Optional<String> landingPage =
        registrableTyped
            // an unusable field value has already been filtered out, so it falls back here...
            .or(() -> Optional.ofNullable(publicLandingPageUrl))
            // ...and again after it, because the fallback needs the same guard: the public landing
            // page is built from the deployment's server URL, which nothing validates for a scheme,
            // so a deployment configured without one would register the very form we refuse from
            // users. Failing this second check omits the property rather than falling back further.
            .filter(PidinstFields::isResolvableAddress);
    // LandingPage is mandatory in the PIDINST 1.0 schema asserted above, so omitting it is a
    // deliberate, visible trade rather than a silent one: an operator who has left the server URL
    // unconfigured should be able to see why a mandatory property left RSpace empty, instead of
    // hearing it from a curator. Same reasoning as the WARN in
    // InventoryIdentifierApiManagerImpl.seedLandingPageForNewPidinst.
    landingPage.ifPresentOrElse(
        metadata::setLandingPage,
        () ->
            log.warn(
                "Registering {} without a LandingPage: no user-typed address and no public landing"
                    + " page were available. The property is mandatory in PIDINST 1.0, but a wrong"
                    + " address cannot be corrected once a curator accepts the record.",
                source.getGlobalIdentifier()));
    mappedFieldData(source, FIELD_ALTERNATE_IDENTIFIER, FieldType.STRING)
        .ifPresent(
            a ->
                metadata.setAlternateIdentifier(
                    List.of(new B2instAlternateIdentifier(ALTERNATE_ID_TYPE_OTHER, a))));
    metadata.setRelatedIdentifier(
        nullIfEmpty(
            relatedIdentifiers(
                source,
                (url, label) ->
                    new B2instRelatedIdentifier(
                        RELATED_ID_TYPE_URL, url, RELATION_TYPE_IS_DESCRIBED_BY, label))));

    B2instAccess access = new B2instAccess();
    access.setRecord(PUBLIC_ACCESS);
    access.setFiles(PUBLIC_ACCESS);

    B2instDoi doi = new B2instDoi();
    doi.setMetadata(metadata);
    doi.setAccess(access);
    doi.setFiles(new B2instFilesOptions(false));
    return doi;
  }

  /**
   * Exactly one Owner entry: ownerName from the "Owner" field when non-blank, else the record
   * owner's full name; ownerContact is always the record owner's email. Owner is the only
   * PIDINST-mandatory property given an unconditional fallback, hence the fallback here.
   *
   * <p>PIDINST 1.0 marks six properties mandatory: Identifier, SchemaVersion, LandingPage, Name,
   * Owner and Manufacturer (see the RDA schema table). RSpace sets the first two itself, and the
   * remaining four come from fields but are handled three different ways: Owner falls back, Name
   * and Manufacturer are sent as found with no fallback and no warning, and LandingPage warns when
   * omitted. Worth aligning deliberately rather than by accident, but that is its own change.
   */
  private B2instOwner ownerOf(InstrumentEntity instrument) {
    B2instOwner b2instOwner = new B2instOwner();
    User owner = instrument.getOwner();
    if (owner != null) {
      b2instOwner.setOwnerName(owner.getFullName());
      b2instOwner.setOwnerContact(owner.getEmail());
    }
    mappedFieldData(instrument, FIELD_OWNER, FieldType.STRING).ifPresent(b2instOwner::setOwnerName);
    return b2instOwner;
  }

  private List<B2instDate> dates(InstrumentEntity instrument) {
    List<B2instDate> dates = new ArrayList<>();
    mappedFieldData(instrument, FIELD_COMMISSIONED, FieldType.DATE)
        .ifPresent(d -> dates.add(new B2instDate(d, DATE_TYPE_COMMISSIONED)));
    mappedFieldData(instrument, FIELD_DECOMMISSIONED, FieldType.DATE)
        .ifPresent(d -> dates.add(new B2instDate(d, DATE_TYPE_DECOMMISSIONED)));
    return dates;
  }

  /**
   * The measured quantity is the one instrument fact PIDINST's {@code MeasuredVariable} was meant
   * to carry, so it is sent as-is. Nothing else reaches MeasuredVariable: the narrative mapping of
   * the other fields was rejected (see DevDocs/adr/0005-measured-variable-narratives.md).
   * "Measurement technique" and "Calibration" instead become RelatedIdentifier entries (ADR 0007);
   * only "Last calibrated" is still unmapped, having no PIDINST home at all.
   */
  private List<String> measuredVariables(InstrumentEntity instrument) {
    List<String> measuredVariables = new ArrayList<>();
    mappedFieldData(instrument, FIELD_MEASURED_QUANTITY, FieldType.STRING)
        .ifPresent(measuredVariables::add);
    return measuredVariables;
  }

  /**
   * RelatedIdentifier entries for the Measurement technique and Calibration link fields
   * (RSDEV-1253, ADR 0007): the link target's globalId page as a URL, always related as
   * IsDescribedBy. Measurement Technique first, Calibration second, matching the ticket's example
   * payloads.
   *
   * <p>Which fields are sent, and in what order, is decided here alone so the two providers cannot
   * drift apart: each passes a builder taking the address and the label, and a third link field is
   * added in one place. The two provider types take the same four values in different positional
   * orders, so keeping one constructor call per provider also keeps that easy mistake to one site.
   */
  private <T> List<T> relatedIdentifiers(
      InstrumentEntity instrument, BiFunction<String, String, T> entry) {
    List<T> related = new ArrayList<>();
    pidinstLinkUrl(instrument, FIELD_MEASUREMENT_TECHNIQUE)
        .ifPresent(url -> related.add(entry.apply(url, RELATED_ID_NAME_MEASUREMENT_TECHNIQUE)));
    pidinstLinkUrl(instrument, FIELD_CALIBRATION)
        .ifPresent(url -> related.add(entry.apply(url, RELATED_ID_NAME_CALIBRATION)));
    return related;
  }

  /**
   * The registrable address of the record this link field points at, or empty when the field holds
   * no live link. Guarded like the LandingPage: an address without an http(s) scheme (server URL
   * unset or scheme-less) is omitted with a WARN rather than registered, because a wrong published
   * value cannot be corrected and a missing one can.
   */
  private Optional<String> pidinstLinkUrl(InstrumentEntity instrument, String canonicalName) {
    Optional<InventoryLink> link = PidinstFields.mappedLink(instrument, canonicalName);
    if (link.isEmpty()) {
      return Optional.empty();
    }
    Optional<String> url =
        InventoryUrls.globalIdPageUrl(
                properties.getServerUrl(), registrableTargetGlobalId(link.get()))
            .filter(PidinstFields::isResolvableAddress);
    if (url.isEmpty()) {
      log.warn(
          "Not registering the {} link of {} as a RelatedIdentifier: no usable http(s) address"
              + " could be built, which means no server URL is configured or it carries no http(s)"
              + " scheme.",
          canonicalName,
          instrument.getGlobalIdentifier());
    }
    return url;
  }

  /**
   * The target globalId to register, carrying the link's version pin when the target type resolves
   * a version-suffixed id. A pinned link names one version deliberately, and a registered address
   * is permanent, so it must not quietly follow the record's latest state instead. The stored id is
   * unsuffixed by contract ({@code InventoryLinkManagerImpl.applyApiToEntity} keeps the version in
   * versionPin), and one already carrying a suffix is left alone rather than doubled.
   */
  private String registrableTargetGlobalId(InventoryLink link) {
    String target = StringUtils.trimToEmpty(link.getTargetGlobalId());
    Long versionPin = link.getVersionPin();
    if (versionPin == null) {
      return target;
    }
    GlobalIdentifier oid = new GlobalIdentifier(target);
    if (oid.hasVersionId() || !VERSION_PINNABLE_TARGETS.contains(oid.getPrefix())) {
      return target;
    }
    return target + "v" + versionPin;
  }

  private Optional<String> mappedFieldData(
      InstrumentEntity instrument, String canonicalName, FieldType expectedType) {
    return PidinstFields.mappedField(instrument, canonicalName, expectedType)
        .map(InventoryEntityField::getFieldData)
        .filter(StringUtils::isNotBlank)
        .map(String::trim);
  }

  private <T> List<T> nullIfEmpty(List<T> list) {
    return list.isEmpty() ? null : list;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public DataCiteDoi buildDataCiteDoi(ApiInventoryDOI doi, InventoryRecord associatedRecord) {
    DataCiteDoi dataCiteDoi = doi.convertToDataCiteDoi();
    if (associatedRecord == null || !associatedRecord.isInstrument()) {
      return dataCiteDoi;
    }
    InstrumentEntity instrument = (InstrumentEntity) associatedRecord;
    List<DataCiteDoiAttributes.RelatedIdentifier> related =
        relatedIdentifiers(
            instrument,
            (url, label) ->
                new DataCiteDoiAttributes.RelatedIdentifier(
                    RELATION_TYPE_IS_DESCRIBED_BY, url, RELATED_ID_TYPE_URL, label));
    // Set unconditionally, the empty list included. DataCite replaces the whole property with
    // whatever the payload carries and clears it only when sent an explicit empty array; a property
    // that is absent or null leaves the registered value alone. An instrument whose link fields
    // have
    // all been cleared therefore has to send [], or the entries registered before the user cleared
    // them stay attached to a findable DOI with no way to withdraw them.
    dataCiteDoi.getAttributes().setRelatedIdentifiers(related);
    return dataCiteDoi;
  }
}
