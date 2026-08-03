package com.researchspace.inventory.api.v2;

import static com.researchspace.model.collection.ApiV2ResourceField.Access.READ_ONLY;

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
    @ApiV2ResourceField(
            access = READ_ONLY,
            description = "Stable instrument identifier.",
            example = "123")
        Long id,
    @ApiV2ResourceField(
            access = READ_ONLY,
            description = "Display name of the instrument.",
            example = "Confocal microscope")
        String name) {

  public static final CollectionDescription<Instrument> DESCRIPTION =
      CollectionDescription.fromApiV2Resource(
          ApiV2InstrumentResource.class,
          List.of(),
          List.of(new Sort("id", true)),
          AccessPolicy.readOnly(AccessFunction.authenticated()));
}
