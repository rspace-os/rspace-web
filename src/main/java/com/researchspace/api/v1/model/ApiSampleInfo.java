/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.api.v1.controller.BaseApiInventoryController;
import com.researchspace.core.util.jsonserialisers.LocalDateDeserialiser;
import com.researchspace.core.util.jsonserialisers.LocalDateSerialiser;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SampleSource;
import com.researchspace.model.units.ValidTemperature;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.web.util.UriComponentsBuilder;

/** API representation of an Inventory Sample */
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
  "templateId",
  "templateVersion",
  "template",
  "revisionId",
  "version",
  "historicalVersion",
  "subSampleAlias",
  "subSamplesCount",
  "storageTempMin",
  "storageTempMax",
  "sampleSource",
  "expiryDate",
  "_links"
})
public class ApiSampleInfo extends ApiInventoryRecordInfo {

  @JsonProperty("templateId")
  private Long templateId;

  @JsonProperty("templateVersion")
  private Long templateVersion;

  @JsonProperty("template")
  private boolean template;

  /* to use when generating image/thumbnail links on controller level, but not sent to front-end */
  @JsonIgnore private boolean templateImageAvailable;

  @JsonProperty("subSampleAlias")
  private ApiSubSampleAlias subSampleAlias;

  @JsonProperty("subSamplesCount")
  private Integer subSamplesCount;

  @JsonProperty("storageTempMin")
  @ValidTemperature
  private ApiQuantityInfo storageTempMin;

  @JsonProperty("storageTempMax")
  @ValidTemperature
  private ApiQuantityInfo storageTempMax;

  /**
   * If this is set to null or empty string, deserialiser will parse into a
   * LocalDateDeserialiser.NULL_DATE. If the incoming request body does not mention expiryDate at
   * all, then this value will be set as <code>null</code>. <br>
   * This is to distinguish an active request to set expiryDate as null, from an incoming patch-like
   * request where expiryDate may be undefined
   */
  @JsonSerialize(using = LocalDateSerialiser.class)
  @JsonDeserialize(using = LocalDateDeserialiser.class)
  private LocalDate expiryDate;

  @JsonProperty("sampleSource")
  private SampleSource sampleSource;

  @JsonProperty("revisionId")
  private Long revisionId;

  @JsonProperty("version")
  private Long version;

  @JsonProperty("historicalVersion")
  private boolean historicalVersion;

  @JsonProperty(value = "forceDelete", access = Access.WRITE_ONLY)
  private boolean forceDelete;

  /** default constructor used by jackson deserializer */
  public ApiSampleInfo() {
    setType(ApiInventoryRecordType.SAMPLE);
  }

  public ApiSampleInfo(Sample sample) {
    super(sample);

    // Sample will have null quantity without subsamples, but for API we should rather return zero
    if (getQuantity() == null) {
      setQuantity(new ApiQuantityInfo(BigDecimal.ZERO, sample.getDefaultUnitId()));
    }

    setSubSampleAlias(
        new ApiSubSampleAlias(sample.getSubSampleAlias(), sample.getSubSampleAliasPlural()));
    setSubSamplesCount(sample.getActiveSubSamplesCount());
    setTemplate(sample.isTemplate());
    // may be null if the sample isn't created from a template.
    if (sample.getSTemplate() != null) {
      setTemplateId(sample.getSTemplate().getId());
      setTemplateVersion(sample.getSTemplateLinkedVersion());
      setTemplateImageAvailable(sample.getSTemplate().getImageFileProperty() != null);
    }
    if (sample.getStorageTempMin() != null) {
      setStorageTempMin(new ApiQuantityInfo(sample.getStorageTempMin()));
    }
    if (sample.getStorageTempMax() != null) {
      setStorageTempMax(new ApiQuantityInfo(sample.getStorageTempMax()));
    }
    setSampleSource(sample.getSampleSource());
    setExpiryDate(sample.getExpiryDate());
    setVersion(sample.getVersion());
  }

  protected boolean applyChangesToDatabaseSample(Sample sample) {
    boolean contentChanged = super.applyChangesToDatabaseInventoryRecord(sample);

    if (storageTempMin != null
        && !storageTempMin.toQuantityInfo().equals(sample.getStorageTempMin())) {
      sample.setStorageTempMin(storageTempMin.toQuantityInfo());
      contentChanged = true;
    }
    if (storageTempMax != null
        && !storageTempMax.toQuantityInfo().equals(sample.getStorageTempMax())) {
      sample.setStorageTempMax(storageTempMax.toQuantityInfo());
      contentChanged = true;
    }

    if (sampleSource != null && !sampleSource.equals(sample.getSampleSource())) {
      sample.setSampleSource(sampleSource);
      contentChanged = true;
    }
    // set if is different. Nullify if is explicit null request
    if (expiryDate != null && !expiryDate.equals(sample.getExpiryDate())) {
      if (sample.getExpiryDate() != null || !LocalDateDeserialiser.NULL_DATE.equals(expiryDate)) {
        sample.setExpiryDate(
            LocalDateDeserialiser.NULL_DATE.equals(expiryDate) ? null : expiryDate);
        contentChanged = true;
      }
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
        if (isTemplateImageAvailable() && getImageFileProperty() != null && getThumbnailFileProperty() != null) {
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
      return BaseApiInventoryController.SAMPLE_TEMPLATES_ENDPOINT;
    }
    return BaseApiInventoryController.SAMPLES_ENDPOINT;
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
    ApiSampleInfo limitedViewCopy = (ApiSampleInfo) apiInvRecCopy;
    limitedViewCopy.setTemplate(isTemplate());
    limitedViewCopy.setTemplateId(getTemplateId());
    limitedViewCopy.setTemplateVersion(getTemplateVersion());
    limitedViewCopy.setTemplateImageAvailable(isTemplateImageAvailable());
    limitedViewCopy.setStorageTempMin(getStorageTempMin());
    limitedViewCopy.setStorageTempMax(getStorageTempMax());
    limitedViewCopy.setExpiryDate(getExpiryDate());
  }
}
