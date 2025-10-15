package com.researchspace.api.v1.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeDeserialiser;
import com.researchspace.core.util.jsonserialisers.ISO8601DateTimeSerialiser;
import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.RecordType;
import com.researchspace.model.record.Folder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
      "notebook",
      "mediaType",
      "pathToRootFolder",
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

  @JsonProperty("mediaType")
  private String mediaType;

  @JsonProperty("pathToRootFolder")
  private List<ApiFolder> pathToRootFolder;

  /**
   * Constructor for converting {@link Folder} entity to an {@link ApiFolder}. If the folder has a
   * parent the user has access to (either the user owns the parent or has access to a shared
   * parent), then parentFolderId will be set. If the folder is a Gallery Folder, then mediaType
   * will be set
   */
  public ApiFolder(Folder folderOrNotebook, User user) {
    super(
        folderOrNotebook.getId(),
        folderOrNotebook.getGlobalIdentifier(),
        folderOrNotebook.getName());
    setCreatedMillis(folderOrNotebook.getCreationDateMillis());
    setLastModifiedMillis(folderOrNotebook.getModificationDateMillis());
    setNotebook(folderOrNotebook.isNotebook());
    if (folderOrNotebook.hasParents()) {
      Optional<Folder> parentInUserContext = folderOrNotebook.getOwnerOrSharedParentForUser(user);
      parentInUserContext.ifPresent(parent -> setParentFolderId(parent.getId()));
    }

    if (GlobalIdPrefix.GF.equals(folderOrNotebook.getOid().getPrefix())) {
      setMediaType(findGalleryFolderMediaType(folderOrNotebook));
    }
  }

  private String findGalleryFolderMediaType(Folder galleryFolder) {
    Folder prevFolder = galleryFolder;
    while (prevFolder.getOwnerParent().isPresent()) {
      Folder currFolder = prevFolder.getOwnerParent().get();
      if (currFolder.hasType(RecordType.ROOT_MEDIA)) {
        return prevFolder.getName();
      }
      prevFolder = currFolder;
    }
    return null;
  }

  /**
   * Constructor that additionally populates the 'pathToRootFolder' list if
   * 'includePathToRootFolder' is true
   *
   * @param folderOrNotebook the folder or notebook to convert
   * @param includePathToRootFolder whether to include the path to the root folder
   * @param user the user making the request
   */
  public ApiFolder(Folder folderOrNotebook, boolean includePathToRootFolder, User user) {
    this(folderOrNotebook, user);

    if (includePathToRootFolder) {
      pathToRootFolder = new ArrayList<>();
      Optional<Folder> parent = folderOrNotebook.getOwnerOrSharedParentForUser(user);
      while (parent.isPresent()) {
        Folder currParent = parent.get();
        pathToRootFolder.add(new ApiFolder(currParent, user));
        if (currParent.hasType(RecordType.ROOT_MEDIA)) {
          break; // for gallery subfolders, stop at Gallery level
        }
        parent = currParent.getOwnerOrSharedParentForUser(user);
      }
    }
  }
}
