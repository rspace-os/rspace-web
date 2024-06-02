/** RSpace API Access your RSpace documents programmatically. */
package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.User;
import com.researchspace.model.record.StructuredDocument;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
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
      "signed",
      "tags",
      "tagMetaData",
      "form",
      "owner",
      "_links"
    })
public class ApiDocumentInfo extends IdentifiableNameableApiObject {

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

  @JsonProperty("signed")
  private Boolean signed = null;

  @JsonProperty("tags")
  @Size(max = 8000, message = "Document tags cannot be longer than 8000 characters")
  private String tags = null;

  @JsonProperty("tagMetaData")
  private String tagMetaData = null;

  @JsonProperty("form")
  private ApiFormInfo form = null;

  @JsonProperty("owner")
  private ApiUser owner = null;

  public ApiDocumentInfo(StructuredDocument record, User authorisedSubject) {
    super(record.getId(), record.getGlobalIdentifier(), record.getName());
    setCreatedMillis(record.getCreationDateMillis());
    setLastModifiedMillis(record.getModificationDateMillis());
    // set parent folder if user is owner of document else null
    if (authorisedSubject.equals(record.getOwner()) && record.hasParents()) {
      setParentFolderId(record.getOwnerParent().get().getId());
    }

    setSigned(record.isSigned());
    setTags(record.getDocTag());
    setTagMetaData(record.getTagMetaData());
    setForm(new ApiFormInfo(record.getForm()));
    setOwner(new ApiUser(record.getOwner()));
  }
}
