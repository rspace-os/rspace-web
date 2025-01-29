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
   * Constructor that looks into parent folders to set 'mediaType' property (in case of Gallery
   * Folders), and also populates the 'parents' list if 'includeParents' param is 'true'
   *
   * @param folderOrNotebook
   * @param authorisedSubject
   */
  public ApiFolder(
      Folder folderOrNotebook, Boolean includePathToRootFolder, User authorisedSubject) {
    this(folderOrNotebook, authorisedSubject);

    if (Boolean.TRUE.equals(includePathToRootFolder)
        && authorisedSubject.equals(folderOrNotebook.getOwner())) {
      pathToRootFolder = new ArrayList<>();
      Optional<Folder> currParentOpt = folderOrNotebook.getOwnerParent();
      while (currParentOpt.isPresent()) {
        Folder currParent = currParentOpt.get();
        pathToRootFolder.add(new ApiFolder(currParent, authorisedSubject));
        if (currParent.hasType(RecordType.ROOT_MEDIA)) {
          break; // for gallery subfolders, stop at Gallery level
        }
        currParentOpt = currParent.getOwnerParent();
      }
    }
  }
}
