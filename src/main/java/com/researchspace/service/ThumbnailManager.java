package com.researchspace.service;

import com.researchspace.model.EcatImage;
import com.researchspace.model.Thumbnail;
import com.researchspace.model.User;
import java.io.IOException;
import java.net.URISyntaxException;

public interface ThumbnailManager extends GenericManager<Thumbnail, Long> {

  /**
   * Returns a {@link Thumbnail} matching the properties of the example. If no such {@link
   * Thumbnail} exists, it creates one.
   *
   * @param example
   * @param subject current subject
   * @return a persisted thumbail with the properties of the argument.
   * @throws IllegalArgumentException
   * @throws IOException
   * @throws URISyntaxException
   */
  Thumbnail getThumbnail(Thumbnail example, User subject)
      throws IllegalArgumentException, IOException, URISyntaxException;

  /**
   * Gets byte [] image data of the given thumbnail.
   *
   * @param thumbnailId
   * @return
   */
  byte[] getThumbnailData(Long thumbnailId);

  /**
   * @param toSave
   * @param subject
   * @return
   */
  Thumbnail save(Thumbnail toSave, User subject);

  /**
   * Delete thumbnails created for a given ecat image.
   *
   * @param img
   * @param user
   */
  void deleteImageThumbnails(EcatImage img, User user);
}
