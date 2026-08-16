package com.researchspace.inventory.model;

import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.AccessResult;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Field;
import com.researchspace.model.collection.CollectionDescription.Operator;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.FilterExpression;
import com.researchspace.model.collection.OpenApiSchemaDocumentation;
import com.researchspace.model.inventory.Instrument;
import java.util.List;
import java.util.Set;

/**
 * Minimal read-only REST v2 shape for Instruments: the fields needed to render one instrument as a
 * relationship value or as a picker option.
 *
 * <p>Described programmatically rather than from an annotated record because the queryable name
 * lives at {@code editInfo.name}: {@code Instrument.getName()} is {@code @Transient}, so the
 * annotated path would describe a filter and sort target that has no column.
 */
public final class ApiV2InstrumentResource {

  public static final CollectionDescription<Instrument> DESCRIPTION =
      description(activeInstruments());

  /** Builds this collection shape with its deployment's complete instrument read policy. */
  public static CollectionDescription<Instrument> description(AccessFunction readAccess) {
    return new CollectionDescription<>(
        "instruments",
        Instrument.class,
        List.<Field<Instrument, ?>>of(
            Field.<Instrument, Long>readOnly(
                    "id", "id", CollectionFieldTypes.longNumber(), Instrument::getId)
                .documented(documentation("Instrument ID", "Stable instrument identifier.", "123")),
            Field.<Instrument, String>readOnly(
                    "name", "editInfo.name", CollectionFieldTypes.text(255), Instrument::getName)
                .documented(
                    documentation(
                        "Name", "Display name of the instrument.", "Confocal microscope")),
            Field.<Instrument, String>readOnly(
                    "globalId",
                    "globalIdentifier",
                    CollectionFieldTypes.text(),
                    Instrument::getGlobalIdentifier)
                // Derived from the ID, so there is no column to filter or sort on.
                .withQueryCapabilities(false, false)
                .documented(documentation("Global ID", "RSpace global identifier.", "IN123")),
            Field.<Instrument, Boolean>readOnly(
                    "deleted", "deleted", CollectionFieldTypes.bool(), Instrument::isDeleted)
                .withQueryCapabilities(true, false)
                .documented(
                    documentation(
                        "Deleted", "True when the instrument is in the trash.", "false"))),
        List.of(),
        "id",
        List.of(new Sort("name", true), new Sort("id", true)),
        AccessPolicy.readOnly(readAccess),
        InventoryReadFilters.ALL);
  }

  private ApiV2InstrumentResource() {}

  /**
   * Reads are limited to instruments that are not in the trash.
   *
   * <p>A row constraint rather than a filter added by the operations class: the registration folds
   * it into the list, the count, the single read, and relationship resolution, so one declaration
   * covers every route that can return an instrument.
   */
  private static AccessFunction activeInstruments() {
    FilterExpression notDeleted =
        new FilterExpression.Comparison("deleted", Operator.EQUAL, List.of(false), false);
    return AccessFunction.documented(
        "A logged-in session is required. Deleted instruments are not returned.",
        Set.of(AccessPolicy.AUTHENTICATION_REQUIRED),
        context ->
            context.isAuthenticated()
                ? AccessResult.allowedWhere(notDeleted)
                : AccessResult.denied(AccessPolicy.AUTHENTICATION_REQUIRED));
  }

  private static OpenApiSchemaDocumentation documentation(
      String title, String description, String example) {
    return new OpenApiSchemaDocumentation(
        title, description, example, null, null, null, List.of(), false);
  }
}
