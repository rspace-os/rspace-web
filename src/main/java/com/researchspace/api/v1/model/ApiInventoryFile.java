/** RSpace Inventory API Access your RSpace Inventory programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.inventory.InventoryFile;
import com.researchspace.model.inventory.InventoryFile.InventoryFileType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * An image, attachment or linked resource object, but without its binary data.
 *
 * <p>It is an API representation of an EcatMediaFile entity.
 *
 * <p>
 *
 * @implementation ApiFile is cached. It is almost immutable apart from the <em>name</em> property.
 *     Renaming triggers eviction from the Spring cache using an event/listener mechanism. <br>
 *     If new mutable properties are added in future, a similar mechanism will need to be used to
 *     indicate that the cached ApiFile should be evicted.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "globalId",
      "name",
      "parentGlobalId",
      "mediaFileGlobalId",
      "type",
      "contentMimeType",
      "extension",
      "size",
      "created",
      "createdBy",
      "deleted",
      "_links"
    })
public class ApiInventoryFile extends IdentifiableNameableApiObject {

  @JsonProperty("parentGlobalId")
  private String parentGlobalId;

  @JsonProperty("mediaFileGlobalId")
  private String mediaFileGlobalId;

  @JsonProperty("type")
  private InventoryFileType type;

  @JsonProperty("contentMimeType")
  private String contentMimeType;

  @JsonProperty("extension")
  private String extension;

  @JsonProperty("size")
  private Long size;

  @EqualsAndHashCode.Exclude
  @JsonProperty("created")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long createdMillis;

  @JsonProperty("createdBy")
  private String createdBy;

  @JsonProperty("deleted")
  private Boolean deleted;

  public ApiInventoryFile(InventoryFile invFile) {
    setId(invFile.getId());
    setGlobalId(invFile.getOid().getIdString());
    setName(invFile.getFileName());
    setParentGlobalId(invFile.getConnectedRecordGlobalIdentifier());
    setMediaFileGlobalId(invFile.getMediaFileGlobalIdentifier());
    setType(invFile.getFileType());
    setContentMimeType(invFile.getContentMimeType());
    setExtension(invFile.getExtension());
    setSize(invFile.getSize());
    setCreatedMillis(invFile.getCreationDate().getTime());
    setCreatedBy(invFile.getCreatedBy());
    setDeleted(invFile.isDeleted());
  }
}
