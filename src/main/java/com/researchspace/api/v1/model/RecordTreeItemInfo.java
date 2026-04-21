/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Folder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Basic information about RSpace Document */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonPropertyOrder(
    value = {
      "id",
      "globalId",
      "name",
      "created",
      "lastModified",
      "parentFolderId",
      "type",
      "systemFolder",
      "sharedFolder",
      "_links"
    })
public class RecordTreeItemInfo extends IdentifiableNameableApiObject {

  @JsonProperty("created")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long createdMillis = null;

  @JsonProperty("lastModified")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long lastModifiedMillis = null;

  @JsonProperty("parentFolderId")
  private Long parentFolderId;

  @JsonProperty("owner")
  private ApiUser owner = null;

  @JsonProperty("type")
  private ApiRecordType type = null;

  @JsonProperty("systemFolder")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Getter(lombok.AccessLevel.NONE) // avoid default getSystemFolder
  private Boolean systemFolder;

  @JsonProperty("sharedFolder")
  @JsonInclude(JsonInclude.Include.NON_NULL)
  @Getter(lombok.AccessLevel.NONE) // avoid default getSharedFolder
  private Boolean sharedFolder;

  public RecordTreeItemInfo(BaseRecord record, Long parentFolderId) {
    super(record.getId(), record.getGlobalIdentifier(), record.getName());
    setCreatedMillis(record.getCreationDateMillis());
    setLastModifiedMillis(record.getModificationDateMillis());
    if (parentFolderId != null) {
      setParentFolderId(parentFolderId);
    }
    setOwner(new ApiUser(record.getOwner()));
    if (record.isNotebook()) {
      setType(ApiRecordType.NOTEBOOK);
    } else if (record.isFolder()) {
      setType(ApiRecordType.FOLDER);
      Folder folder = (Folder) record;
      setSystemFolder(folder.isSystemFolder());
      setSharedFolder(folder.isSharedFolder());
    } else if (record.isStructuredDocument()) {
      setType(ApiRecordType.DOCUMENT);
    } else if (record.isMediaRecord()) {
      setType(ApiRecordType.MEDIA);
    } else if (record.isSnippet()) {
      setType(ApiRecordType.SNIPPET);
    }
  }

  public Boolean isSystemFolder() {
    return systemFolder;
  }

  public Boolean isSharedFolder() {
    return sharedFolder;
  }
}
