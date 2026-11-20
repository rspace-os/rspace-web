package com.researchspace.service;

import com.researchspace.model.EcatMediaFile;
import com.researchspace.model.User;
import java.util.List;

public interface TemplateAndGalleryTransferService extends TransferService {

  /** Moves retained media to the new owner's Gallery under {@code Deleted Users/<username>}. */
  void transferGalleryItems(User originalOwner, User newOwner, List<EcatMediaFile> galleryItems);
}
