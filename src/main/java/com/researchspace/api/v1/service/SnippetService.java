package com.researchspace.api.v1.service;

import com.researchspace.model.User;
import com.researchspace.model.record.Folder;
import com.researchspace.model.record.Snippet;
import java.util.Optional;

public interface SnippetService {

  Snippet getSnippet(long id, User user);

  /**
   * Finds the readable folder through which the user reaches the snippet in their Gallery.
   *
   * @return the owner's folder for the owner; otherwise a live parent within the user's Gallery,
   *     such as a group's shared snippets folder, falling back to the owner's folder; empty when
   *     the user cannot read the chosen folder
   */
  Optional<Folder> getGalleryParentFolder(Snippet snippet, User user);
}
