/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.FileProperty;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Container.ContainerType;
import com.researchspace.model.inventory.Container.GridLayoutAxisLabelEnum;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.web.util.UriComponentsBuilder;

/** API representation of an Inventory Container */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
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
  "_links"
})
public class ApiContainerInfo extends ApiInventoryRecordInfo {

  @JsonProperty("locationsCount")
  private Integer locationsCount;

  @JsonProperty("contentSummary")
  private ApiContainerContentSummary contentSummary;

  @Pattern(
      regexp = "(LIST)|(GRID)|(IMAGE)",
      flags = {Pattern.Flag.CASE_INSENSITIVE})
  @JsonProperty(value = "cType")
  private String cType;

  @JsonProperty("gridLayout")
  private ApiContainerGridLayoutConfig gridLayout;

  @JsonProperty("canStoreSamples")
  private Boolean canStoreSamples;

  @JsonProperty("canStoreContainers")
  private Boolean canStoreContainers;

  @JsonProperty("parentContainers")
  private List<ApiContainerInfo> parentContainers = new ArrayList<>();

  @JsonProperty("parentLocation")
  private ApiContainerLocation parentLocation;

  @JsonProperty(value = "removeFromParentContainerRequest", access = Access.WRITE_ONLY)
  private boolean removeFromParentContainerRequest;

  @JsonProperty("lastNonWorkbenchParent")
  private ApiContainerInfo lastNonWorkbenchParent;

  @EqualsAndHashCode.Exclude
  @JsonProperty("lastMoveDate")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long lastMoveDateMillis;

  @JsonIgnore
  public boolean isListContainer() {
    return ContainerType.LIST.name().equals(cType);
  }

  @JsonIgnore
  public boolean isGridContainer() {
    return ContainerType.GRID.name().equals(cType);
  }

  @JsonIgnore
  public boolean isImageContainer() {
    return ContainerType.IMAGE.name().equals(cType);
  }

  @JsonIgnore
  public boolean isWorkbench() {
    return ContainerType.WORKBENCH.name().equals(cType);
  }

  @JsonIgnore private FileProperty locationsImageFileProperty;

  /** default constructor used by jackson deserializer */
  public ApiContainerInfo() {
    setType(ApiInventoryRecordType.CONTAINER);
  }

  public ApiContainerInfo(Container container) {
    super(container);

    if (container.isGridLayoutContainer()) {
      setGridLayout(
          new ApiContainerGridLayoutConfig(
              container.getGridLayoutColumnsNumber(),
              container.getGridLayoutRowsNumber(),
              container.getGridLayoutColumnsLabelType(),
              container.getGridLayoutRowsLabelType()));
    }
    // list containers can store any number of locations, for others set locationsCount
    if (!container.isListLayoutContainer() && !container.isWorkbench()) {
      setLocationsCount(container.getLocationsCount());
    }
    setContentSummary(
        new ApiContainerContentSummary(
            container.getContentCount(),
            container.getContentCountSubSamples(),
            container.getContentCountContainers()));
    setCanStoreContainers(container.isCanStoreContainers());
    setCanStoreSamples(container.isCanStoreSamples());
    setCustomImage(container.getImageFileProperty() != null);
    setCType(container.getContainerType().name());
    setLocationsImageFileProperty(container.getLocationsImageFileProperty());

    if (container.getParentLocation() != null) {
      parentLocation = new ApiContainerLocation(container.getParentLocation());
      populateParentContainers(container.getParentContainer());
    }
    if (container.getLastNonWorkbenchParent() != null) {
      setLastNonWorkbenchParent(new ApiContainerInfo(container.getLastNonWorkbenchParent()));
    }
    if (container.getLastMoveDate() != null) {
      setLastMoveDateMillis(Date.from(container.getLastMoveDate()).getTime());
    }
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ApiContainerGridLayoutConfig {
    private Integer columnsNumber;
    private Integer rowsNumber;
    private GridLayoutAxisLabelEnum columnsLabelType;
    private GridLayoutAxisLabelEnum rowsLabelType;

    public ApiContainerGridLayoutConfig(Integer columnsNumber, Integer rowsNumber) {
      this.columnsNumber = columnsNumber;
      this.rowsNumber = rowsNumber;
    }
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ApiContainerContentSummary {
    private Integer totalCount;
    private Integer subSampleCount;
    private Integer containerCount;
  }

  public boolean applyChangesToDatabaseContainer(Container dbContainer) {
    boolean contentChanged = super.applyChangesToDatabaseInventoryRecord(dbContainer);
    contentChanged |= applyChangesToDatabaseGridLayout(dbContainer);
    contentChanged |= applyContainerContentFlagsToDatabaseContainer(dbContainer);

    return contentChanged;
  }

  public boolean applyChangesToDatabaseGridLayout(Container dbContainer) {
    boolean contentChanged = false;
    if (gridLayout != null) {
      if (gridLayout.getColumnsNumber() != dbContainer.getGridLayoutColumnsNumber()
          || gridLayout.getRowsNumber() != dbContainer.getGridLayoutRowsNumber()) {
        dbContainer.configureAsGridLayoutContainer(
            gridLayout.getColumnsNumber(), gridLayout.getRowsNumber());
        contentChanged = true;
      }
      if (gridLayout.getColumnsLabelType() != null
          && !gridLayout
              .getColumnsLabelType()
              .equals(dbContainer.getGridLayoutColumnsLabelType())) {
        dbContainer.setGridLayoutColumnsLabelType(gridLayout.getColumnsLabelType());
        contentChanged = true;
      }
      if (gridLayout.getRowsLabelType() != null
          && !gridLayout.getRowsLabelType().equals(dbContainer.getGridLayoutRowsLabelType())) {
        dbContainer.setGridLayoutRowsLabelType(gridLayout.getRowsLabelType());
        contentChanged = true;
      }
    }
    return contentChanged;
  }

  public boolean applyContainerContentFlagsToDatabaseContainer(Container dbContainer) {
    boolean contentChanged = false;
    if (getCanStoreSamples() != null
        && !getCanStoreSamples().equals(dbContainer.isCanStoreSamples())) {
      if (!getCanStoreSamples() && !dbContainer.getStoredSubSamples().isEmpty()) {
        throw new IllegalArgumentException(
            "Cannot set canStoreSamples to false, as this container is already storing subsamples");
      }
      dbContainer.setCanStoreSamples(getCanStoreSamples());
      contentChanged = true;
    }
    if (getCanStoreContainers() != null
        && !getCanStoreContainers().equals(dbContainer.isCanStoreContainers())) {
      if (!getCanStoreContainers() && !dbContainer.getStoredContainers().isEmpty()) {
        throw new IllegalArgumentException(
            "Cannot set canStoreContainers to false, as this container is already storing"
                + " subcontainers");
      }
      dbContainer.setCanStoreContainers(getCanStoreContainers());
      contentChanged = true;
    }
    return contentChanged;
  }

  protected String getSelfLinkEndpoint() {
    return BaseApiInventoryController.CONTAINERS_ENDPOINT;
  }

  @Override
  public void buildAndAddInventoryRecordLinks(UriComponentsBuilder inventoryApiBaseUrl) {
    super.buildAndAddInventoryRecordLinks(inventoryApiBaseUrl);

    if (getLocationsImageFileProperty() != null) {
      addLocationImageLink(inventoryApiBaseUrl);
    }
  }

  void addLocationImageLink(UriComponentsBuilder baseUrlBuilder) {
    String imageType = ApiLinkItem.LOCATIONS_IMAGE_REL;
    addImageLink(baseUrlBuilder, getLocationsImageFileProperty().getContentsHash(), imageType);
  }

  @Override
  protected void populateLimitedViewCopy(ApiInventoryRecordInfo apiInvRecCopy) {
    super.populateLimitedViewCopy(apiInvRecCopy);
    ApiContainerInfo publicViewCopy = (ApiContainerInfo) apiInvRecCopy;
    publicViewCopy.setContentSummary(getContentSummary());
    publicViewCopy.setParentContainers(getParentContainers());
    publicViewCopy.setParentLocation(getParentLocation());
    publicViewCopy.setCType(getCType());
    publicViewCopy.setCanStoreContainers(getCanStoreContainers());
    publicViewCopy.setCanStoreSamples(getCanStoreSamples());
  }

  @Override
  protected void nullifyListsForPublicView(ApiInventoryRecordInfo apiInvRec) {
    ApiContainerInfo apiContainer = (ApiContainerInfo) apiInvRec;
    super.nullifyListsForPublicView(apiContainer);
    apiContainer.setParentContainers(null);
  }
}
