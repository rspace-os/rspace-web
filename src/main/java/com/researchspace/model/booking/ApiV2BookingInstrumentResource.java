package com.researchspace.model.booking;

import com.researchspace.inventory.model.InventoryReadFilters;
import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.OpenApiSchemaDocumentation;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.inventory.Instrument;
import java.util.List;

/** Safe, relationship-only Instrument projection used by Booking. */
public final class ApiV2BookingInstrumentResource {

  public static final String RESOURCE_NAME = "booking-instruments";

  public static final CollectionDescription<Instrument> DESCRIPTION =
      description(AccessFunction.authenticated());

  /** Builds the safe relationship projection with the caller's current Inventory read policy. */
  public static CollectionDescription<Instrument> description(AccessFunction readAccess) {
    return new CollectionDescription<>(
        RESOURCE_NAME,
        Instrument.class,
        List.<Field<Instrument, ?>>of(
            Field.<Instrument, Long>readOnly(
                    "id", "id", CollectionFieldTypes.longNumber(), Instrument::getId)
                .documented(
                    OpenApiSchemaDocumentation.of(
                        "Instrument ID", "Stable instrument identifier.", "123")),
            Field.readOnly(
                    "globalId",
                    "globalIdentifier",
                    CollectionFieldTypes.text(),
                    Instrument::getGlobalIdentifier)
                .withQueryCapabilities(false, false)
                .documented(
                    OpenApiSchemaDocumentation.of(
                        "Global ID", "RSpace global identifier.", "IN123")),
            Field.<Instrument, String>readOnly(
                    "name", "editInfo.name", CollectionFieldTypes.text(255), Instrument::getName)
                .documented(
                    OpenApiSchemaDocumentation.of(
                        "Name", "Display name of the instrument.", "Microscope")),
            Field.readOnly("deleted", "deleted", CollectionFieldTypes.bool(), Instrument::isDeleted)
                .withQueryCapabilities(true, false)
                .documented(
                    OpenApiSchemaDocumentation.of(
                        "Deleted", "True when the instrument is in the trash.", "false"))),
        "id",
        List.of(new Sort("name", true), new Sort("id", true)),
        AccessPolicy.readOnly(readAccess),
        InventoryReadFilters.ALL);
  }

  private ApiV2BookingInstrumentResource() {}
}
