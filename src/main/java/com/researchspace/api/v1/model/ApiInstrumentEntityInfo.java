/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import com.researchspace.model.inventory.Instrument;
import com.researchspace.model.inventory.InstrumentEntity;
import com.researchspace.model.inventory.InventoryItemSource;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.web.util.UriComponentsBuilder;

/** API representation of an Inventory Sample */
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
  "instrumentSource",
  "_links"
})
public class ApiInstrumentEntityInfo extends ApiInventoryRecordInfo {

  @JsonProperty("templateId")
  @JsonInclude(value = NON_NULL)
  private Long templateId;

  @JsonProperty("templateVersion")
  @JsonInclude(value = NON_NULL)
  private Long templateVersion;

  @JsonProperty("template")
  @JsonInclude(value = NON_NULL)
  private boolean template;

  /* to use when generating image/thumbnail links on controller level, but not sent to front-end */
  @JsonIgnore private boolean templateImageAvailable;

  @JsonProperty("instrumentSource")
  private InventoryItemSource instrumentSource;

  @JsonProperty("revisionId")
  private Long revisionId;

  @JsonProperty("version")
  private Long version;

  @JsonProperty("historicalVersion")
  private boolean historicalVersion;

  @JsonProperty(value = "forceDelete", access = Access.WRITE_ONLY)
  private boolean forceDelete;

  public ApiInstrumentEntityInfo(InstrumentEntity instrumentEntity) {
    super(instrumentEntity);
    if (instrumentEntity.isInstrument()) {
      Instrument in = (Instrument) instrumentEntity;
      this.setTemplateId(in.getParentTemplateId());
      this.setTemplate(false);
      this.setTemplateVersion(in.getTemplateLinkedVersion());
    } else { // then it is an InstrumentTemplate
      this.setTemplate(true);
    }
    this.setInstrumentSource(instrumentEntity.getInstrumentSource());
    this.setVersion(instrumentEntity.getVersion());
  }

  protected boolean applyChangesToDatabaseInstrument(InstrumentEntity sample) {
    boolean contentChanged = super.applyChangesToDatabaseInventoryRecord(sample);

    if (instrumentSource != null && !instrumentSource.equals(sample.getInstrumentSource())) {
      sample.setInstrumentSource(instrumentSource);
      contentChanged = true;
    }
    return contentChanged;
  }

  @Override
  public void buildAndAddInventoryRecordLinks(UriComponentsBuilder baseUrlBuilder) {
    super.buildAndAddInventoryRecordLinks(baseUrlBuilder);

    if (getIconId() != null) {
      String iconPath =
          BaseApiInventoryController.SAMPLE_TEMPLATES_ENDPOINT
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
   * Boolean test for whether this sample has a non-default templateIcon id (non-null, > 0)
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
