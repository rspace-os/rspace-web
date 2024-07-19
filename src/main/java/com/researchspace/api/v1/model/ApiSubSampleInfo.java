/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.inventory.SubSample;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.web.util.UriComponentsBuilder;

/** API representation of an Inventory SubSample */
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
  "tags",
  "attachments",
  "barcodes",
  "identifiers",
  "owner",
  "permittedActions",
  "sharingMode",
  "quantity",
  "type",
  "parentContainers",
  "parentLocation",
  "lastNonWorkbenchParent",
  "lastMoveDate",
  "revisionId",
  "deletedOnSampleDeletion",
  "storedInContainer",
  "_links"
})
public class ApiSubSampleInfo extends ApiInventoryRecordInfo {

  @JsonProperty("parentContainers")
  private List<ApiContainerInfo> parentContainers = new ArrayList<>();

  @JsonProperty("parentLocation")
  private ApiContainerLocation parentLocation;

  @JsonProperty("lastNonWorkbenchParent")
  private ApiContainerInfo lastNonWorkbenchParent;

  @EqualsAndHashCode.Exclude
  @JsonProperty("lastMoveDate")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long lastMoveDateMillis;

  @JsonProperty("revisionId")
  private Long revisionId;

  @JsonProperty("deletedOnSampleDeletion")
  private boolean deletedOnSampleDeletion;

  @JsonProperty("storedInContainer")
  private boolean storedInContainer;

  /** default constructor used by jackson deserializer */
  public ApiSubSampleInfo() {
    setType(ApiInventoryRecordType.SUBSAMPLE);
  }

  public ApiSubSampleInfo(SubSample subSample) {
    super(subSample);

    if (subSample.getParentLocation() != null) {
      parentLocation = new ApiContainerLocation(subSample.getParentLocation());
      populateParentContainers(subSample.getParentContainer());
    }
    if (subSample.getLastNonWorkbenchParent() != null) {
      setLastNonWorkbenchParent(new ApiContainerInfo(subSample.getLastNonWorkbenchParent()));
    }
    if (subSample.getLastMoveDate() != null) {
      setLastMoveDateMillis(Date.from(subSample.getLastMoveDate()).getTime());
    }
    setDeletedOnSampleDeletion(subSample.isDeletedOnSampleDeletion());
    setStoredInContainer(subSample.isStoredInContainer());
  }

  protected void addImageLinksFromParentSample(
      UriComponentsBuilder baseUrlBuilder, ApiSampleInfo parentSampleInfo) {
    // add parent's image/thumbnail links if present
    Optional<ApiLinkItem> imageOptional = parentSampleInfo.getLinkOfType(ApiLinkItem.IMAGE_REL);
    if (imageOptional.isPresent()) {
      addLink(imageOptional.get());
    }
    Optional<ApiLinkItem> thumbnailOptional =
        parentSampleInfo.getLinkOfType(ApiLinkItem.THUMBNAIL_REL);
    if (thumbnailOptional.isPresent()) {
      addLink(thumbnailOptional.get());
    }
  }

  protected String getSelfLinkEndpoint() {
    return BaseApiInventoryController.SUBSAMPLES_ENDPOINT;
  }

  @Override
  public LinkableApiObject buildAndAddSelfLink(
      final String endpoint, String pathStartingWithId, UriComponentsBuilder baseUrl) {
    if (revisionId != null) {
      pathStartingWithId += "/revisions/" + revisionId;
    }
    return super.buildAndAddSelfLink(endpoint, pathStartingWithId, baseUrl);
  }

  @Override
  protected void populateLimitedViewCopy(ApiInventoryRecordInfo apiInvRecCopy) {
    super.populateLimitedViewCopy(apiInvRecCopy);
    ApiSubSampleInfo publicViewCopy = (ApiSubSampleInfo) apiInvRecCopy;
    publicViewCopy.setParentContainers(getParentContainers());
    publicViewCopy.setParentLocation(getParentLocation());
  }

  @Override
  protected void nullifyListsForPublicView(ApiInventoryRecordInfo apiInvRec) {
    ApiSubSampleInfo apiSubSample = (ApiSubSampleInfo) apiInvRec;
    super.nullifyListsForPublicView(apiSubSample);
    apiSubSample.setParentContainers(null);
  }
}
