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
import com.researchspace.properties.IPropertyHolder;
import com.researchspace.service.inventory.RspaceToExternalProviderAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** See {@link RspaceToExternalProviderAdapter}. */
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
  public B2instDoi buildB2instDoi(InventoryRecord instrument) {
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
     * Omitted rather than sent site-relative when no server URL is configured: a LandingPage is baked
     * into a citable PID once a curator accepts the record, and RSpace has no way to update a
     * published B2INST record afterwards. A missing property is recoverable; a wrong published URL is
     * not. See GlobalIdUrls.globalIdUrl.
     */
    mappedFieldData(source, FIELD_LANDING_PAGE, FieldType.URI)
        .or(() -> GlobalIdUrls.globalIdUrl(properties, source.getGlobalIdentifier()))
        .ifPresent(metadata::setLandingPage);
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
   * Exactly one Owner entry: ownerName from the "Owner" field when non-blank, else the record
   * owner's full name; ownerContact is always the record owner's email. Owner is the only
   * PIDINST-mandatory property built from fields, hence the unconditional fallback.
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
