package com.researchspace.inventory.model;

import com.researchspace.model.collection.AccessFunction;
import com.researchspace.model.collection.AccessPolicy;
import com.researchspace.model.collection.CollectionDescription;
import com.researchspace.model.collection.CollectionFieldTypes;
import com.researchspace.model.collection.Field;
import com.researchspace.model.collection.Sort;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import java.util.List;

/** Read-only container fields safe for both full and contents-only Inventory visibility. */
public final class ApiV2ContainerResource {
  public static final String RESOURCE_NAME = "containers";

  // A deployment must supply ContainerReadAccess before serving this resource.
  public static final CollectionDescription<Container> DESCRIPTION =
      description(AccessFunction.never());

  public static CollectionDescription<Container> description(AccessFunction readAccess) {
    return new CollectionDescription<>(
        RESOURCE_NAME,
        Container.class,
        List.of(
            Field.readOnly("id", "id", CollectionFieldTypes.longNumber(), Container::getId),
            Field.readOnly(
                    "globalId",
                    "globalIdentifier",
                    CollectionFieldTypes.text(),
                    Container::getGlobalIdentifier)
                .withQueryCapabilities(false, false),
            Field.readOnly(
                "name", "editInfo.name", CollectionFieldTypes.text(255), Container::getName),
            Field.readOnly(
                "containerType",
                "containerType",
                CollectionFieldTypes.enumeration(ContainerType.class),
                Container::getContainerType),
            Field.readOnly("deleted", "deleted", CollectionFieldTypes.bool(), Container::isDeleted)
                .withQueryCapabilities(true, false)),
        "id",
        List.of(new Sort("name", true), new Sort("id", true)),
        AccessPolicy.readOnly(readAccess),
        InventoryReadFilters.ALL);
  }

  private ApiV2ContainerResource() {}
}
