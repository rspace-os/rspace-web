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
import com.researchspace.b2inst.model.request.B2instDoi;
import com.researchspace.datacite.model.DataCiteDoi;
import com.researchspace.model.User;
import com.researchspace.model.field.FieldType;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.field.InventoryEntityField;
import com.researchspace.service.inventory.RspaceToExternalProviderAdapter;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
  // shared with the service layer's landing-page rules, which must resolve the same field
  private static final String FIELD_LANDING_PAGE = PidinstFields.LANDING_PAGE;
  private static final String FIELD_ALTERNATE_IDENTIFIER = "Alternate Identifier";

  // PIDINST controlled values ("DeCommissioned" deliberately differs from the field name).
  private static final String DATE_TYPE_COMMISSIONED = "Commissioned";
  private static final String DATE_TYPE_DECOMMISSIONED = "DeCommissioned";
  private static final String ALTERNATE_ID_TYPE_OTHER = "Other";

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
     * identifier's public landing page. The materialised globalId default is recognised and never
     * registered: it needs an RSpace sign-in, and a LandingPage is baked into a citable PID once a
     * curator accepts, with no way to update the published record afterwards. With neither a typed
     * value nor a public URL the property is omitted: a missing property is recoverable, a wrong
     * published URL is not. See ADR 0006 and CONTEXT.md ("Registered landing page").
     */
    Optional<String> typed =
        mappedFieldData(source, FIELD_LANDING_PAGE, FieldType.URI)
            .filter(fieldValue -> !isMaterialisedGlobalIdDefault(fieldValue, source));
    if (typed.isPresent() && !isResolvableAddress(typed.get())) {
      // Substituting our own address for one the user typed is worth saying out loud: the field
      // goes
      // on displaying their value, so nothing else tells them it was not the one registered. The
      // value itself is not logged, only the record it belongs to.
      log.warn(
          "Not registering the Landing page of {} as its LandingPage: the value is not an absolute"
              + " http(s) address. Using the identifier's public landing page instead.",
          source.getGlobalIdentifier());
    }
    Optional<String> landingPage =
        typed
            // before the .or so an unusable field value falls back to the public page...
            .filter(RspaceToExternalProviderAdapterImpl::isResolvableAddress)
            .or(() -> Optional.ofNullable(publicLandingPageUrl))
            // ...and again after it, because the fallback needs the same guard: the public landing
            // page is built from the deployment's server URL, which nothing validates for a scheme,
            // so a deployment configured without one would register the very form we refuse from
            // users. Failing this second check omits the property rather than falling back further.
            .filter(RspaceToExternalProviderAdapterImpl::isResolvableAddress);
    // LandingPage is mandatory in the PIDINST 1.0 schema asserted above, so omitting it is a
    // deliberate, visible trade rather than a silent one: an operator who has left the server URL
    // unconfigured should be able to see why a mandatory property left RSpace empty, instead of
    // hearing it from a curator. Same reasoning as the WARN in
    // InstrumentEntityApiManagerImpl.fillBlankLandingPage.
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
   * Whether a typed Landing page is an address a resolver could actually follow. The field's own
   * validation is only {@code new URI(...)} parsing (core-model's InventoryUriField), which accepts
   * a bare host, a relative path, and non-web schemes such as {@code javascript:} and {@code
   * data:}. None of those identify the instrument to someone resolving the PID, and a LandingPage
   * is baked into a citable PID once a curator accepts, so anything that is not an absolute http(s)
   * address falls back to the identifier's public page rather than being registered.
   *
   * <p>A scheme prefix is deliberately enough: the field's validation does reject a scheme with no
   * authority ({@code new URI("http://")} throws "Expected authority"), so there is no reachable
   * input of that shape left to guard against here.
   */
  private static boolean isResolvableAddress(String fieldValue) {
    return StringUtils.startsWithIgnoreCase(fieldValue, "http://")
        || StringUtils.startsWithIgnoreCase(fieldValue, "https://");
  }

  /**
   * Whether the Landing page field is holding RSpace's own materialised default rather than a value
   * the user typed. Matched on the {@code /globalId/<globalId>} tail alone, not on equality with
   * the currently configured address: the tail is what the default-fill produces and names this one
   * record, while the host part is whatever the server URL said at fill time. Comparing whole
   * addresses would stop recognising the fill as soon as the deployment was renamed or lost its
   * server URL setting, and would then register the login-walled default — irreversibly, once a
   * curator accepts. See {@link GlobalIdUrls} and ADR 0006.
   *
   * <p>The tail is compared against the address's path with any query and fragment dropped, any
   * trailing slash removed, and case folded, because none of those change which page the address
   * names. Without that normalisation a default a user had since edited to carry a trailing slash
   * or a {@code ?from=...} would read as user-typed and be registered.
   *
   * <p>Two accepted consequences, both erring towards omission because a missing property is
   * recoverable and a wrong published one is not. A user who deliberately types some other RSpace's
   * {@code /globalId/<same id>} address has it discarded in favour of this identifier's public
   * page. And a differently-cased global id is treated as the default even though it may resolve to
   * nothing; an address that resolves to nothing is no more fit to register.
   */
  private static boolean isMaterialisedGlobalIdDefault(String fieldValue, InstrumentEntity source) {
    String globalIdTail = GlobalIdUrls.GLOBAL_ID_PATH + source.getGlobalIdentifier();
    return StringUtils.endsWithIgnoreCase(
        StringUtils.stripEnd(comparablePath(fieldValue), "/"), globalIdTail);
  }

  /**
   * The address's path, normalised so that forms which name the same page compare equal: query and
   * fragment gone, dot segments resolved, percent-escapes decoded. {@link URI#getPath()} does the
   * last two ({@code getPath} decodes, unlike {@code getRawPath}).
   *
   * <p>An address the URI parser rejects falls back to the raw text with query and fragment
   * stripped, so a malformed value is still checked rather than waved through. The field's own
   * validation makes that rare but not impossible, since it runs at save time and says nothing
   * about rows written before it existed.
   */
  private static String comparablePath(String address) {
    try {
      String path = URI.create(address).normalize().getPath();
      return path == null ? address : path;
    } catch (IllegalArgumentException unparseable) {
      return StringUtils.substringBefore(StringUtils.substringBefore(address, "#"), "?");
    }
  }

  /**
   * Exactly one Owner entry: ownerName from the "Owner" field when non-blank, else the record
   * owner's full name; ownerContact is always the record owner's email. Owner is the only
   * PIDINST-mandatory property given an unconditional fallback, hence the fallback here.
   * Manufacturer is mandatory too and is built from a field with no fallback and no warning, so the
   * three mandatory properties are handled three different ways; worth aligning separately.
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
   * to carry, so it is sent as-is. "Measurement technique", "Calibration" and "Last calibrated" are
   * deliberately not mapped: they have no PIDINST home and are kept on the template purely as
   * instrument documentation (see DevDocs/adr/0005-measured-variable-narratives.md, superseded).
   */
  private List<String> measuredVariables(InstrumentEntity instrument) {
    List<String> measuredVariables = new ArrayList<>();
    mappedFieldData(instrument, FIELD_MEASURED_QUANTITY, FieldType.STRING)
        .ifPresent(measuredVariables::add);
    return measuredVariables;
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
  public DataCiteDoi buildDataCiteDoi(ApiInventoryDOI doi) {
    return doi.convertToDataCiteDoi();
  }
}
