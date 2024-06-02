/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.ContainerLocation;
import com.researchspace.model.inventory.field.ExtraField;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * API representation of an full Inventory Container - with extra fields, and also locations (and
 * their content)
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder({
  "id",
  "globalId",
  "name",
  "description",
  "created",
  "createdBy",
  "lastModified",
  "modifiedBy",
  "modifiedByFullName",
  "deleted",
  "deletedDate",
  "iconId",
  "quantity",
  "tags",
  "type",
  "attachments",
  "barcodes",
  "identifiers",
  "owner",
  "permittedActions",
  "sharingMode",
  "parentContainers",
  "parentLocation",
  "lastNonWorkbenchParent",
  "lastMoveDate",
  "locationsCount",
  "contentSummary",
  "cType",
  "gridLayout",
  "canStoreSamples",
  "canStoreContainers",
  "sharedWith",
  "extraFields",
  "locations",
  "_links"
})
public class ApiContainer extends ApiContainerInfo {

  @JsonProperty(value = "sharedWith")
  private List<ApiGroupInfoWithSharedFlag> sharedWith;

  @JsonProperty("extraFields")
  private List<ApiExtraField> extraFields = new ArrayList<>();

  @JsonProperty("locations")
  private List<ApiContainerLocationWithContent> locations = new ArrayList<>();

  @Size(max = 10_000_000, message = "Container image cannot be larger than 10MB")
  @JsonProperty(value = "newBase64Image", access = Access.WRITE_ONLY)
  private String newBase64Image;

  @Size(max = 10_000_000, message = "Container locations image cannot be larger than 10MB")
  @JsonProperty(value = "newBase64LocationsImage", access = Access.WRITE_ONLY)
  private String newBase64LocationsImage;

  public ApiContainer(Container container) {
    this(container, true);
  }

  public ApiContainer(Container container, boolean includeContent) {
    super(container);

    sharedWith = new ArrayList<>();
    for (ExtraField extraField : container.getActiveExtraFields()) {
      extraFields.add(new ApiExtraField(extraField));
    }
    if (includeContent) {
      for (ContainerLocation location : container.getLocations()) {
        locations.add(new ApiContainerLocationWithContent(location));
      }
    } else {
      setLocations(null);
    }
  }

  /** to simplify creation of a valid container (must have name and type). */
  public ApiContainer(String name, ContainerType type) {
    setName(name);
    setCType(type.toString());
  }

  @JsonIgnore
  public List<ApiInventoryRecordInfo> getStoredContent() {
    if (locations == null) {
      return null;
    }
    return locations.stream()
        .map(ApiContainerLocationWithContent::getContent)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public boolean applyChangesToDatabaseContainer(Container dbContainer, User user) {
    boolean contentChanged = super.applyChangesToDatabaseContainer(dbContainer);
    contentChanged |=
        applyChangesToDatabaseExtraFields(extraFields, dbContainer.getActiveExtraFields(), user);
    contentChanged |=
        applyChangesToDatabaseBarcodes(getBarcodes(), dbContainer.getActiveBarcodes());
    contentChanged |=
        applyChangesToDatabaseIdentifiers(
            getIdentifiers(), dbContainer.getActiveIdentifiers(), user);
    contentChanged |= applyChangesToSharingMode(dbContainer, user);
    contentChanged |=
        applyChangesToDatabaseLocations(locations, dbContainer.getLocations(), dbContainer, user);

    return contentChanged;
  }

  /**
   * Applies content changes to pre-existing locations. Skips fields marked with
   * newLocation/deleteLocationRequest flags.
   *
   * @param locations incoming location changes
   * @param dbLocations pre-existing locations of the container
   * @param dbContainer
   * @param user
   * @return true if any change was applied
   */
  protected boolean applyChangesToDatabaseLocations(
      List<ApiContainerLocationWithContent> locations,
      List<ContainerLocation> dbLocations,
      Container dbContainer,
      User user) {

    boolean anyFieldChanged = false;
    if (locations != null) {
      for (ApiContainerLocation incomingLocation : locations) {
        if (incomingLocation.isNewLocationRequest()) {
          dbContainer.createNewImageContainerLocation(
              incomingLocation.getCoordX(), incomingLocation.getCoordY());
          anyFieldChanged = true;
          continue;
        }
        if (incomingLocation.getId() == null) {
          throw new IllegalArgumentException(
              "'id' property not provided for a container location"
                  + " (and 'newFieldRequest' is: "
                  + incomingLocation.isNewLocationRequest()
                  + ")");
        }
        Optional<ContainerLocation> dbLocationOpt =
            dbLocations.stream()
                .filter(sf -> sf.getId().equals(incomingLocation.getId()))
                .findFirst();
        if (!dbLocationOpt.isPresent()) {
          throw new IllegalArgumentException(
              "Container location id: "
                  + incomingLocation.getId()
                  + " doesn't match id of any pre-existing location");
        }
        ContainerLocation dbLocation = dbLocationOpt.get();
        if (incomingLocation.isDeleteLocationRequest()) {
          if (dbLocation.getStoredRecord() != null) {
            throw new IllegalArgumentException(
                "Container location id: "
                    + incomingLocation.getId()
                    + " stores a record: "
                    + dbLocation.getStoredRecord().getGlobalIdentifier()
                    + ". Move the record before trying to delete the location.");
          }
          dbContainer.removeLocation(dbLocation);
          anyFieldChanged = true;
        } else {
          anyFieldChanged |= incomingLocation.applyChangesToDatabaseLocation(dbLocation);
        }
      }
    }
    return anyFieldChanged;
  }

  @Override
  public void buildAndAddInventoryRecordLinks(UriComponentsBuilder inventoryApiBaseUrl) {
    super.buildAndAddInventoryRecordLinks(inventoryApiBaseUrl);

    List<ApiInventoryRecordInfo> storedContent = getStoredContent();
    if (storedContent != null) {
      for (ApiInventoryRecordInfo item : storedContent) {
        item.buildAndAddInventoryRecordLinks(inventoryApiBaseUrl);
      }
    }
  }

  public ApiContainerInfo toContainerInfoWithIdOnly() {
    ApiContainerInfo apiContainerInfo = new ApiContainerInfo();
    apiContainerInfo.setId(getId());
    return apiContainerInfo;
  }

  @Override
  protected void nullifyListsForLimitedView(ApiInventoryRecordInfo apiInvRec) {
    ApiContainer apiContainer = (ApiContainer) apiInvRec;
    super.nullifyListsForLimitedView(apiContainer);
    apiContainer.setSharedWith(null);
    apiContainer.setExtraFields(null);
    apiContainer.setLocations(null);
  }
}
