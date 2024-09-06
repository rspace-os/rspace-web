/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.FileProperty;
import com.researchspace.model.Group;
import com.researchspace.model.User;
import com.researchspace.model.inventory.Barcode;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.DigitalObjectIdentifier;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryRecord;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.model.inventory.field.ExtraField;
import com.researchspace.service.impl.DocumentTagManagerImpl;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@NoArgsConstructor
public abstract class ApiInventoryRecordInfo extends IdentifiableNameableApiObject {
  @JsonProperty("description")
  private String description;

  @EqualsAndHashCode.Exclude
  @JsonProperty("created")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long createdMillis;

  @JsonProperty("createdBy")
  private String createdBy;

  @EqualsAndHashCode.Exclude
  @JsonProperty("lastModified")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long lastModifiedMillis;

  @JsonProperty("modifiedBy")
  private String modifiedBy;

  @JsonProperty("modifiedByFullName")
  private String modifiedByFullName;

  @JsonProperty("deleted")
  private boolean deleted;

  @EqualsAndHashCode.Exclude
  @JsonProperty("deletedDate")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long deletedDate;

  @JsonProperty("iconId")
  private Long iconId;

  @JsonProperty("quantity")
  private ApiQuantityInfo quantity;

  @JsonProperty("tags")
  private List<ApiTagInfo> tags = new ArrayList<>();

  @JsonProperty(value = "type")
  private ApiInventoryRecordType type;

  public enum ApiInventoryRecordType {
    SAMPLE,
    SUBSAMPLE,
    CONTAINER,
    SAMPLE_TEMPLATE
  }

  @JsonProperty(value = "attachments")
  private List<ApiInventoryFile> attachments = new ArrayList<>();

  @JsonProperty(value = "barcodes")
  private List<ApiBarcode> barcodes = new ArrayList<>();

  @JsonProperty(value = "identifiers")
  private List<ApiInventoryDOI> identifiers = new ArrayList<>();

  @JsonProperty("owner")
  private ApiUser owner;

  @JsonProperty(value = "permittedActions")
  private List<ApiInventoryRecordPermittedAction> permittedActions = new ArrayList<>();

  public enum ApiInventoryRecordPermittedAction {
    READ,
    LIMITED_READ,
    UPDATE,
    CHANGE_OWNER
  }

  @JsonProperty(value = "sharingMode")
  private ApiInventorySharingMode sharingMode;

  public enum ApiInventorySharingMode {
    OWNER_GROUPS,
    WHITELIST,
    OWNER_ONLY
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ApiGroupInfoWithSharedFlag {
    @JsonProperty(value = "group")
    private ApiGroupBasicInfo groupInfo;

    @JsonProperty(value = "shared")
    private boolean shared;

    @JsonProperty(value = "itemOwnerGroup")
    private boolean itemOwnerGroup;

    public static ApiGroupInfoWithSharedFlag forSharingWithGroup(Group group, User itemOwner) {
      boolean isItemOwnerGroup = group.getMembers().contains(itemOwner);
      return new ApiGroupInfoWithSharedFlag(new ApiGroupBasicInfo(group), true, isItemOwnerGroup);
    }
  }

  /* to use when generating image/thumbnail links on controller level, but not sent to front-end */
  @JsonIgnore private boolean customImage;

  @JsonIgnore private FileProperty imageFileProperty;

  @JsonIgnore private FileProperty thumbnailFileProperty;

  @Size(max = 10_000_000, message = "Image cannot be larger than 10MB")
  @JsonProperty(value = "newBase64Image", access = Access.WRITE_ONLY)
  private String newBase64Image;

  public static ApiInventoryRecordInfo fromInventoryRecord(InventoryRecord invRecord) {
    if (invRecord.isSample()) {
      Sample sample = (Sample) invRecord;
      return sample.isTemplate() ? new ApiSampleTemplateInfo(sample) : new ApiSampleInfo(sample);
    } else if (invRecord.isSubSample()) {
      return new ApiSubSampleInfoWithSampleInfo((SubSample) invRecord);
    } else if (invRecord.isContainer()) {
      return new ApiContainerInfo((Container) invRecord);
    } else {
      throw new IllegalArgumentException("unsupported type: " + invRecord);
    }
  }

  public static ApiInventoryRecordInfo fromInventoryRecordToFullApiRecord(
      InventoryRecord invRecord) {
    if (invRecord.isSample()) {
      Sample sample = (Sample) invRecord;
      return sample.isTemplate() ? new ApiSampleTemplate(sample) : new ApiSample(sample);
    } else if (invRecord.isSubSample()) {
      return new ApiSubSample((SubSample) invRecord);
    } else if (invRecord.isContainer()) {
      return new ApiContainer((Container) invRecord);
    } else {
      throw new IllegalArgumentException("unsupported type: " + invRecord);
    }
  }

  /* to call from subclass constructors */
  protected ApiInventoryRecordInfo(InventoryRecord invRecord) {
    super(
        invRecord.getId(),
        invRecord.getId() != null ? invRecord.getOid().toString() : null,
        invRecord.getName());

    setDescription(invRecord.getDescription());
    setCreatedMillis(invRecord.getCreationDate().getTime());
    setCreatedBy(invRecord.getCreatedBy());
    setLastModifiedMillis(invRecord.getModificationDate().getTime());
    setModifiedBy(invRecord.getModifiedBy());
    setOwner(new ApiUser(invRecord.getOwner()));
    setDeleted(invRecord.isDeleted());
    if (invRecord.getDeletedDate() != null) {
      setDeletedDate(invRecord.getDeletedDate().getTime());
    }
    setIconId(invRecord.getIconId());
    if (invRecord.getQuantityInfo() != null) {
      setQuantity(new ApiQuantityInfo(invRecord));
    }
    setApiTagInfo(invRecord.getTagMetaData());
    setType(ApiInventoryRecordType.valueOf(invRecord.getType().toString()));
    setSharingMode(ApiInventorySharingMode.valueOf(invRecord.getSharingMode().toString()));
    setCustomImage(invRecord.getImageFileProperty() != null);
    setImageFileProperty(invRecord.getImageFileProperty());
    setThumbnailFileProperty(invRecord.getThumbnailFileProperty());

    for (InventoryFile invFile : invRecord.getAttachedFiles()) {
      attachments.add(new ApiInventoryFile(invFile));
    }
    for (Barcode barcode : invRecord.getActiveBarcodes()) {
      barcodes.add(new ApiBarcode(barcode));
    }
    for (DigitalObjectIdentifier identifier : invRecord.getActiveIdentifiers()) {
      identifiers.add(new ApiInventoryDOI(identifier));
    }
  }

  public void setApiTagInfo(String tagMetaData) {
    List<ApiTagInfo> apiTagInfo = new ArrayList<>();
    if (StringUtils.hasText(tagMetaData)) {
      String[] tagsPlusMeta = tagMetaData.split(",");
      for (String tagPlusMeta : tagsPlusMeta) {
        ApiTagInfo tagInfo = new ApiTagInfo();
        String value = DocumentTagManagerImpl.getTagValueFromMeta(tagPlusMeta).trim();
        tagInfo.setValue(value);
        String uri = DocumentTagManagerImpl.getTagOntologyUriFromMeta(tagPlusMeta).trim();
        tagInfo.setUri(uri);
        String name = DocumentTagManagerImpl.getTagOntologyNameFromMeta(tagPlusMeta).trim();
        tagInfo.setOntologyName(name);
        String version = DocumentTagManagerImpl.getTagOntologyVersionFromMeta(tagPlusMeta).trim();
        tagInfo.setOntologyVersion(version);
        apiTagInfo.add(tagInfo);
      }
    }
    tags = apiTagInfo;
  }

  @SuppressFBWarnings
  @JsonIgnore
  public String getDBStringFromTags() {
    if (tags == null || tags.isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    tags.stream().forEach(aTag -> builder.append(aTag.toString() + ","));
    String tagStr = builder.toString();
    return tagStr.substring(0, tagStr.length() - 1);
  }

  public static boolean tagDifferenceExists(
      ApiInventoryRecordInfo original, ApiInventoryRecordInfo latest) {
    boolean originalHadTags = !original.getTags().isEmpty();
    boolean updatedHasTags = !latest.getTags().isEmpty();
    if ((originalHadTags != updatedHasTags) || (!original.getTags().equals(latest.getTags()))) {
      return true;
    }
    return false;
  }

  /**
   * Default implementation returns empty list - should be reimplemented by subclass.
   *
   * @return list of extra fields
   */
  @JsonIgnore
  public List<ApiExtraField> getExtraFields() {
    return new ArrayList<>();
  }

  /**
   * Default implementation returns null - should be reimplemented by subclass that can be placed in
   * parent containers.
   *
   * @return list of parent containers (ordered by most-direct parent) or null.
   */
  @JsonIgnore
  public List<ApiContainerInfo> getParentContainers() {
    return null;
  }

  /**
   * Default implementation returns null - should be reimplemented by subclass that can be placed in
   * parent container.
   *
   * @return parent location (or null)
   */
  @JsonIgnore
  public ApiContainerLocation getParentLocation() {
    return null;
  }

  /**
   * Default implementation returns false - should be reimplemented by subclass that can be moved
   * outside any container.
   */
  @JsonIgnore
  public boolean isRemoveFromParentContainerRequest() {
    return false;
  }

  /**
   * Default implementation does nothing - should be reimplemented by subclass that supports
   * revision history.
   */
  @JsonIgnore
  public void setRevisionId(Long revisionId) {
    ;
  }

  public void addPermittedAction(ApiInventoryRecordPermittedAction action) {
    if (!permittedActions.contains(action)) {
      permittedActions.add(action);
    }
  }

  @JsonIgnore
  public boolean isLimitedReadItem() {
    return permittedActions.size() == 1
        && ApiInventoryRecordPermittedAction.LIMITED_READ.equals(permittedActions.get(0));
  }

  @JsonIgnore
  public boolean isPublicReadItem() {
    return permittedActions.isEmpty();
  }

  /*
   * Populates list of parent containers by going up the container hierarchy.
   */
  protected void populateParentContainers(Container firstParent) {
    getParentContainers().clear();
    if (firstParent != null) {
      getParentContainers().add(new ApiContainerInfo(firstParent));
      Container currContainer = firstParent;
      while (currContainer.getParentContainer() != null) {
        currContainer = currContainer.getParentContainer();
        getParentContainers().add(new ApiContainerInfo(currContainer));
      }
    }
  }

  @JsonIgnore
  public ApiContainerInfo getParentContainer() {
    if (CollectionUtils.isEmpty(getParentContainers())) {
      return null;
    }
    return getParentContainers().get(0);
  }

  @JsonIgnore
  public void setParentContainer(ApiContainerInfo container) {
    if (getParentContainers() == null) {
      throw new UnsupportedOperationException(
          "trying to set parentContainer in object that doesn't store parents");
    }
    getParentContainers().clear();
    getParentContainers().add(container);
  }

  /** Return all attachments connected to record or its fields. */
  @JsonIgnore
  public List<ApiInventoryFile> getAllAttachments() {
    return getAttachments();
  }

  /**
   * Default implementation returns null - should be reimplemented by subclass that populates full
   * details of item including 'shared with' permissions.
   *
   * @return list of gropus that item is shared with, and sharing status
   */
  @JsonIgnore
  public List<ApiGroupInfoWithSharedFlag> getSharedWith() {
    return null;
  }

  public void setSharedWith(List<ApiGroupInfoWithSharedFlag> sharedWith) {
    /*  to be implemented by subclass populating full details of item including 'shared with' permissions */
  }

  /**
   * Applies modifications incoming with this ApiInventoryRecordInfo to corresponding database
   * entity.
   */
  protected boolean applyChangesToDatabaseInventoryRecord(InventoryRecord invRec) {
    boolean contentChanged = false;

    if (getName() != null && !getName().equals(invRec.getName())) {
      invRec.setName(getName());
      contentChanged = true;
    }
    if (description != null && !description.equals(invRec.getDescription())) {
      invRec.setDescription(description);
      contentChanged = true;
    }

    /* Careful - for fields/extra fieds/etc. empty incoming list will do nothing, but for tags it'll be treated
    as a request to clear the tags. this is for backward compatibility where tags were just a simple string.
    If you don't want to change the tags, the incoming list must be null, not empty. */
    String existingTags = invRec.getTags() == null ? "" : invRec.getTags();
    if (getTags() != null && !getDBStringFromTags().equals(existingTags)) {
      invRec.setTagMetaData(getDBStringFromTags());
      invRec.setTags(
          String.join(
              ",",
              DocumentTagManagerImpl.getAllTagValuesFromAllTagsPlusMeta(getDBStringFromTags())));
      contentChanged = true;
    }
    return contentChanged;
  }

  /**
   * Applies content changes to extra fields. Skips fields marked with newField/deleteFieldRequest
   * flags.
   *
   * @param apiExtraFields incoming extra field changes
   * @param dbExtraFields database extra field entities
   * @param user current user
   * @return true if any change was applied
   */
  protected boolean applyChangesToDatabaseExtraFields(
      List<ApiExtraField> apiExtraFields, List<ExtraField> dbExtraFields, User user) {
    boolean anyFieldChanged = false;
    if (apiExtraFields != null) {
      for (ApiExtraField field : apiExtraFields) {
        if (field.isNewFieldRequest() || field.isDeleteFieldRequest()) {
          continue; /* we only handle modifications of pre-existing fields, not adding/deletion */
        }
        if (field.getId() == null) {
          throw new IllegalArgumentException(
              "'id' property not provided for an extra field"
                  + " (and 'newFieldRequest' is: "
                  + field.isNewFieldRequest()
                  + ")");
        }
        Optional<ExtraField> dbFieldOpt =
            dbExtraFields.stream().filter(sf -> sf.getId().equals(field.getId())).findFirst();
        if (dbFieldOpt.isEmpty()) {
          throw new IllegalArgumentException(
              "Extra field id: "
                  + field.getId()
                  + " doesn't match id of any pre-existing extra field");
        }
        ExtraField dbField = dbFieldOpt.get();
        anyFieldChanged |= field.applyChangesToDatabaseExtraField(dbField, user);
      }
    }
    return anyFieldChanged;
  }

  /**
   * Applies content changes to barcodes. Skips barcodes marked with newField/deleteFieldRequest
   * flags.
   *
   * @param apiBarcodes incoming barcode changes
   * @param dbBarcodes database barcode entities
   * @return true if any change was applied
   */
  protected boolean applyChangesToDatabaseBarcodes(
      List<ApiBarcode> apiBarcodes, List<Barcode> dbBarcodes) {
    boolean anyBarcodeChanged = false;
    if (apiBarcodes != null) {
      for (ApiBarcode barcode : apiBarcodes) {
        if (barcode.isNewBarcodeRequest() || barcode.isDeleteBarcodeRequest()) {
          continue; /* here we only handle modifications of pre-existing barcodes, not adding/deletion */
        }
        if (barcode.getId() == null) {
          throw new IllegalArgumentException(
              "'id' property not provided for a barcode"
                  + " (and 'newBarcodeRequest' is: "
                  + barcode.isNewBarcodeRequest()
                  + ")");
        }
        Optional<Barcode> dbBarcodeOpt =
            dbBarcodes.stream().filter(sf -> sf.getId().equals(barcode.getId())).findFirst();
        if (dbBarcodeOpt.isEmpty()) {
          throw new IllegalArgumentException(
              "Barcode id: " + barcode.getId() + " doesn't match id of any pre-existing barcode");
        }
        Barcode dbBarcode = dbBarcodeOpt.get();
        anyBarcodeChanged |= barcode.applyChangesToDatabaseBarcode(dbBarcode);
      }
    }
    return anyBarcodeChanged;
  }

  /**
   * Applies content changes to identifiers.
   *
   * @param apiIdentifiers incoming identifiers changes
   * @param dbIdentifiers database identifiers of the record
   * @param user
   * @return true if any change was applied
   */
  protected boolean applyChangesToDatabaseIdentifiers(
      List<ApiInventoryDOI> apiIdentifiers,
      List<DigitalObjectIdentifier> dbIdentifiers,
      User user) {
    boolean anyIdentifierChanged = false;
    if (apiIdentifiers != null) {
      for (ApiInventoryDOI apiIdentifier : apiIdentifiers) {
        if (apiIdentifier.isRegisterIdentifierRequest()
            || apiIdentifier.isDeleteIdentifierRequest()) {
          continue; /* we only handle modifications of pre-existing identifier here */
        }
        if (apiIdentifier.getId() == null) {
          throw new IllegalArgumentException(
              "'id' property not provided for an identifier to update");
        }
        Optional<DigitalObjectIdentifier> dbIdentifierOpt =
            dbIdentifiers.stream()
                .filter(sf -> sf.getId().equals(apiIdentifier.getId()))
                .findFirst();
        if (dbIdentifierOpt.isEmpty()) {
          throw new IllegalArgumentException(
              "Identifier id: "
                  + apiIdentifier.getId()
                  + " doesn't match id of any pre-existing identifiers");
        }
        DigitalObjectIdentifier dbIdentifier = dbIdentifierOpt.get();
        anyIdentifierChanged |= apiIdentifier.applyChangesToDatabaseDOI(dbIdentifier);
      }
    }
    return anyIdentifierChanged;
  }

  protected boolean applyChangesToSharingMode(InventoryRecord invRec, User user) {
    ApiInventorySharingMode incomingSharingMode = getSharingMode();
    boolean permissionChanged = false;
    if (incomingSharingMode != null) {
      if (!invRec.getSharingMode().toString().equals(incomingSharingMode.toString())) {
        invRec.setSharingMode(
            InventoryRecord.InventorySharingMode.valueOf(incomingSharingMode.toString()));
        permissionChanged = true;
      }
    }
    return permissionChanged;
  }

  /**
   * Sets complete links_ array specific for that inventory record <br>
   * Can be extended by subclasses to add more links (subclass should still call this method)
   */
  public void buildAndAddInventoryRecordLinks(UriComponentsBuilder baseUrlBuilder) {
    if (isCustomImage()) {
      if (getImageFileProperty() != null) {
        buildAndAddInventoryImageLink(ApiLinkItem.IMAGE_REL, baseUrlBuilder);
      }
      if (getThumbnailFileProperty() != null) {
        buildAndAddInventoryImageLink(ApiLinkItem.THUMBNAIL_REL, baseUrlBuilder);
      }
    }
    buildAndAddSelfLink(getSelfLinkEndpoint(), "" + getId(), baseUrlBuilder);
  }

  protected abstract String getSelfLinkEndpoint();

  void buildAndAddInventoryImageLink(String imageType, UriComponentsBuilder baseUrlBuilder) {
    addLink(buildInventoryImageLink(imageType, baseUrlBuilder, getId(), getSelfLinkEndpoint()));
  }

  ApiLinkItem buildInventoryImageLink(
      String imageType, UriComponentsBuilder baseUrlBuilder, Long invRecId, String endpointName) {
    String imagePath =
        endpointName
            + "/"
            + imageType
            + "/"
            + (imageType.equals("image")
                ? getImageFileProperty().getFileName()
                : getThumbnailFileProperty().getFileName());
    String imageLink = buildLinkForForPath(baseUrlBuilder, imagePath);
    return ApiLinkItem.builder().link(imageLink).rel(imageType).build();
  }

  String buildLinkForForPath(UriComponentsBuilder baseUrlBuilder, String imagePath) {
    return baseUrlBuilder.cloneBuilder().path(imagePath).build().encode().toUriString();
  }

  @JsonIgnore
  public boolean isClearedForLimitedView() {
    return getCreatedMillis() == null;
  }

  @JsonIgnore
  public boolean isClearedForPublicView() {
    return getCreatedMillis() == null && getBarcodes() == null;
  }

  public void clearPropertiesForLimitedView() {
    BeanUtils.copyProperties(getLimitedViewCopy(), this, "parentContainer");
  }

  public void clearPropertiesForPublicView() {
    BeanUtils.copyProperties(getPublicViewCopy(), this, "parentContainer");
  }

  protected ApiInventoryRecordInfo getLimitedViewCopy() {
    ApiInventoryRecordInfo limitedViewCopy = getEmptyCopy();
    populateLimitedViewCopy(limitedViewCopy);
    nullifyListsForLimitedView(limitedViewCopy);
    return limitedViewCopy;
  }

  protected ApiInventoryRecordInfo getPublicViewCopy() {
    ApiInventoryRecordInfo publicViewCopy = getEmptyCopy();
    populatePublicViewCopy(publicViewCopy);
    nullifyListsForPublicView(publicViewCopy);
    return publicViewCopy;
  }

  @JsonIgnore
  public ApiInventoryRecordInfo getEmptyCopy() {
    ApiInventoryRecordInfo result;
    try {
      result = getClass().getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalStateException(
          "cannot create instance of class: " + getClass().toString(), e);
    }
    return result;
  }

  protected void populatePublicViewCopy(ApiInventoryRecordInfo publicViewCopy) {
    publicViewCopy.setId(getId());
    publicViewCopy.setGlobalId(getGlobalId());
    publicViewCopy.setType(getType());
    publicViewCopy.setName(getName());
    publicViewCopy.setOwner(getOwner());
    publicViewCopy.setPermittedActions(getPermittedActions());
    publicViewCopy.setLinks(getLinks());
  }

  protected void populateLimitedViewCopy(ApiInventoryRecordInfo limitedViewCopy) {
    populatePublicViewCopy(limitedViewCopy);
    limitedViewCopy.setBarcodes(getBarcodes());
    limitedViewCopy.setCustomImage(isCustomImage());
    limitedViewCopy.setIconId(getIconId());
    limitedViewCopy.setTags(getTags());
    limitedViewCopy.setDescription(getDescription());
  }

  protected void nullifyListsForLimitedView(ApiInventoryRecordInfo apiInvRec) {
    apiInvRec.setAttachments(null);
  }

  protected void nullifyListsForPublicView(ApiInventoryRecordInfo apiInvRec) {
    nullifyListsForLimitedView(apiInvRec);
    apiInvRec.setBarcodes(null);
  }

  /*
   * ==============
   *  for testing
   * ==============
   */

  /**
   * JsonCreator constructor. API endpoints often expect concrete subclass, but for bulk operations,
   * or in MVCIT tests (when mvcTestUtils.getFromJsonResponseBody is used) the string is mapped to
   * class with this constructor method.
   */
  @JsonCreator
  public static ApiInventoryRecordInfo fromJsonResponseBody(
      @JsonProperty("type") ApiInventoryRecordType type) {

    ApiInventoryRecordInfo result;
    if (ApiInventoryRecordType.SAMPLE.equals(type)) {
      result = new ApiSampleWithFullSubSamples();
    } else if (ApiInventoryRecordType.SAMPLE_TEMPLATE.equals(type)) {
      result = new ApiSampleTemplate();
    } else if (ApiInventoryRecordType.SUBSAMPLE.equals(type)) {
      result = new ApiSubSample();
    } else if (ApiInventoryRecordType.CONTAINER.equals(type)) {
      result = new ApiContainer();
    } else {
      throw new IllegalArgumentException(
          "unrecognized type in provided ApiInventoryRecordInfo: " + type);
    }
    return result;
  }
}
