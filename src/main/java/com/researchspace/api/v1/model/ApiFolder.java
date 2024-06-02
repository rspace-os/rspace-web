package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import javax.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

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
      "isNotebook",
      "owner",
      "_links"
    })
public class ApiFolder extends IdentifiableNameableApiObject {

  @JsonProperty("created")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long createdMillis = null;

  @JsonProperty("lastModified")
  @JsonSerialize(using = ISO8601DateTimeSerialiser.class)
  @JsonDeserialize(using = ISO8601DateTimeDeserialiser.class)
  private Long lastModifiedMillis = null;

  @JsonProperty("parentFolderId")
  @Min(1)
  private Long parentFolderId;

  /**
   * Boolean indicating if this folder is a notebook (<code>true</code>) or a regular folder(<code>
   * false</code>)
   */
  private boolean notebook;

  /**
   * Constructor for converting {@link Folder} entity to an {@link ApiFolder}
   *
   * @param folderOrNotebook
   * @param authorisedSubject
   */
  public ApiFolder(Folder folderOrNotebook, User authorisedSubject) {
    super(
        folderOrNotebook.getId(),
        folderOrNotebook.getGlobalIdentifier(),
        folderOrNotebook.getName());
    setCreatedMillis(folderOrNotebook.getCreationDateMillis());
    setLastModifiedMillis(folderOrNotebook.getModificationDateMillis());
    setNotebook(folderOrNotebook.isNotebook());
    if (authorisedSubject.equals(folderOrNotebook.getOwner()) && folderOrNotebook.hasParents()) {
      if (folderOrNotebook.getOwnerParent().isPresent()) {
        setParentFolderId(folderOrNotebook.getOwnerParent().get().getId());
      } else if (folderOrNotebook.isSharedFolder() && folderOrNotebook.hasParents()) {
        setParentFolderId(folderOrNotebook.getParentFolders().iterator().next().getId());
      }
    }
  }
}
