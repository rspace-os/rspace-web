package com.researchspace.inventory.api.v2;

import static com.researchspace.model.collection.ApiV2ResourceField.AccessPreset.NEVER;

import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.ApiV2ResourceDefinition;
import com.researchspace.model.collection.ApiV2ResourceField;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionDescription.Sort;
import com.researchspace.model.inventory.Instrument;
import java.util.List;

/** Minimal REST v2 shape used when an Instrument relationship is populated. */
@ApiV2ResourceDefinition(name = "instruments", entity = Instrument.class, id = "id")
public record ApiV2InstrumentResource(
    @ApiV2ResourceField(description = "Stable instrument identifier.", example = "123") Long id,
    @ApiV2ResourceField(
            createAccess = NEVER,
            updateAccess = NEVER,
            description = "Display name of the instrument.",
            example = "Confocal microscope")
        String name) {

  public static final CollectionDescription<Instrument> DESCRIPTION =
      CollectionDescription.fromApiV2Resource(
          ApiV2InstrumentResource.class,
          Instrument.class,
          List.of(),
          List.of(new Sort("id", true)),
          AccessPolicy.readOnly(AccessFunction.authenticated()));
}
