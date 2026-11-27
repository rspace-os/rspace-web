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
   * @return the owner's folder for the owner; otherwise the readable live parent within the user's
   *     Gallery with the lowest id, such as a group's shared snippets folder, falling back to the
   *     owner's folder if readable; otherwise empty
   */
  Optional<Folder> getGalleryParentFolder(Snippet snippet, User user);
}
