/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.web.util.UriComponentsBuilder;

/** API representation of an Inventory Instrument */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
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
  "type",
  "attachments",
  "barcodes",
  "identifiers",
  "owner",
  "permittedActions",
  "sharingMode",
  "templateId",
  "templateVersion",
  "template",
  "revisionId",
  "version",
  "historicalVersion",
  "parentContainers",
  "parentLocation",
  "lastNonWorkbenchParent",
  "lastMoveDate",
  "storedInContainer",
  "_links"
})
public class ApiInstrumentEntityInfo extends ApiInventoryRecordInfo {

  @JsonProperty("templateId")
  @JsonInclude(value = NON_NULL)
  private Long templateId;

  @JsonProperty("templateVersion")
  @JsonInclude(value = NON_NULL)
  private Long templateVersion;

  @JsonProperty(value = "template", access = Access.READ_ONLY)
  private boolean template;

  /* to use when generating image/thumbnail links on controller level, but not sent to front-end */
  @JsonIgnore private boolean templateImageAvailable;

  @JsonProperty("revisionId")
  private Long revisionId;

  @JsonProperty("version")
  private Long version;

  @JsonProperty("historicalVersion")
  private boolean historicalVersion;

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

  @JsonProperty("storedInContainer")
  private boolean storedInContainer;

  public ApiInstrumentEntityInfo(InstrumentEntity instrumentEntity) {
    super(instrumentEntity);
    if (instrumentEntity.isInstrument()) {
      Instrument in = (Instrument) instrumentEntity;
      this.setTemplateId(in.getParentTemplateId());
      this.setTemplate(false);
      this.setTemplateVersion(in.getTemplateLinkedVersion());
      if (in.getParentLocation() != null) {
        parentLocation = new ApiContainerLocation(in.getParentLocation());
        Container parent = in.getParentContainer();
        if (parent != null) {
          parentContainers.add(new ApiContainerInfo(parent));
          Container curr = parent.getParentContainer();
          while (curr != null) {
            parentContainers.add(new ApiContainerInfo(curr));
            curr = curr.getParentContainer();
          }
        }
      }
      if (in.getLastNonWorkbenchParent() != null) {
        lastNonWorkbenchParent = new ApiContainerInfo(in.getLastNonWorkbenchParent());
      }
      if (in.getLastMoveDate() != null) {
        lastMoveDateMillis = Date.from(in.getLastMoveDate()).getTime();
      }
      storedInContainer = in.isStoredInContainer();
    } else { // then it is an InstrumentTemplate
      this.setTemplate(true);
    }
    this.setVersion(instrumentEntity.getVersion());
  }

  protected boolean applyChangesToDatabaseInstrument(InstrumentEntity instrument) {
    return super.applyChangesToDatabaseInventoryRecord(instrument);
  }

  @Override
  public void buildAndAddInventoryRecordLinks(UriComponentsBuilder baseUrlBuilder) {
    super.buildAndAddInventoryRecordLinks(baseUrlBuilder);

    if (getIconId() != null) {
      String iconPath =
          BaseApiInventoryController.INSTRUMENT_TEMPLATES_ENDPOINT
              + "/"
              + getId()
              + "/icon/"
              + getIconId();
      String iconLink = buildLinkForForPath(baseUrlBuilder, iconPath);
      addLink(ApiLinkItem.builder().link(iconLink).rel(ApiLinkItem.ICON_REL).build());

      if (!isCustomImage()) {
        if (isTemplateImageAvailable()
            && getImageFileProperty() != null
            && getThumbnailFileProperty() != null) {
          addMainImageLink(baseUrlBuilder);
          addThumbnailLink(baseUrlBuilder);
        } else {
          addLink(iconLink, ApiLinkItem.THUMBNAIL_REL);
          addLink(iconLink, ApiLinkItem.IMAGE_REL);
        }
      }
    }
  }

  protected String getSelfLinkEndpoint() {
    if (isTemplate()) {
      return BaseApiInventoryController.INSTRUMENT_TEMPLATES_ENDPOINT;
    }
    return BaseApiInventoryController.INSTRUMENTS_ENDPOINT;
  }

  @Override
  public LinkableApiObject buildAndAddSelfLink(
      final String endpoint, String pathStartingWithId, UriComponentsBuilder baseUrl) {
    if (revisionId != null) {
      pathStartingWithId += "/revisions/" + revisionId;
    }
    return super.buildAndAddSelfLink(endpoint, pathStartingWithId, baseUrl);
  }

  /**
   * Boolean test for whether this instrument has a non-default templateIcon id (non-null, > 0)
   *
   * @return
   */
  @JsonIgnore
  public boolean hasIconImage() {
    return getIconId() != null && getIconId() > 0;
  }

  @Override
  protected void populateLimitedViewCopy(ApiInventoryRecordInfo apiInvRecCopy) {
    super.populateLimitedViewCopy(apiInvRecCopy);
    ApiInstrumentEntityInfo limitedViewCopy = (ApiInstrumentEntityInfo) apiInvRecCopy;
    limitedViewCopy.setTemplate(this.template); // can be null
    limitedViewCopy.setTemplateId(getTemplateId());
    limitedViewCopy.setTemplateVersion(getTemplateVersion());
    limitedViewCopy.setTemplateImageAvailable(isTemplateImageAvailable());
  }
}
