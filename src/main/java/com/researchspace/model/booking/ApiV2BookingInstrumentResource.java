package com.researchspace.model.booking;

import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.OpenApiSchemaDocumentation;
import com.researchspace.model.inventory.Instrument;
import java.util.List;

/** Safe, relationship-only Instrument projection used by Booking. */
public final class ApiV2BookingInstrumentResource {

  public static final String RESOURCE_NAME = "booking-instruments";

  public static final CollectionDescription<Instrument> DESCRIPTION =
      new CollectionDescription<>(
          RESOURCE_NAME,
          Instrument.class,
          List.<Field<Instrument, ?>>of(
              Field.<Instrument, Long>readOnly(
                      "id", "id", CollectionFieldTypes.longNumber(), Instrument::getId)
                  .documented(
                      documentation("Instrument ID", "Stable instrument identifier.", "123")),
              Field.readOnly(
                      "globalId",
                      "globalIdentifier",
                      CollectionFieldTypes.text(),
                      Instrument::getGlobalIdentifier)
                  .withQueryCapabilities(false, false)
                  .documented(documentation("Global ID", "RSpace global identifier.", "IN123")),
              Field.<Instrument, String>readOnly(
                      "name", "editInfo.name", CollectionFieldTypes.text(255), Instrument::getName)
                  .documented(
                      documentation("Name", "Display name of the instrument.", "Microscope")),
              Field.readOnly(
                      "deleted", "deleted", CollectionFieldTypes.bool(), Instrument::isDeleted)
                  .withQueryCapabilities(false, false)
                  .documented(
                      documentation(
                          "Deleted", "True when the instrument is in the trash.", "false"))),
          List.of(),
          "id",
          List.of(new Sort("name", true), new Sort("id", true)),
          AccessPolicy.readOnly(AccessFunction.authenticated()));

  private static OpenApiSchemaDocumentation documentation(
      String title, String description, String example) {
    return new OpenApiSchemaDocumentation(
        title, description, example, null, null, null, List.of(), false);
  }

  private ApiV2BookingInstrumentResource() {}
}
