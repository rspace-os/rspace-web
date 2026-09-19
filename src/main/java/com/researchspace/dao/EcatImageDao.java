package com.researchspace.dao;

import com.researchspace.model.EcatImage;

public interface EcatImageDao extends GenericDao<EcatImage, Long> {

  /**
   * Loads an {@link EcatImage} by id with its {@code originalImage} association eagerly fetched in
   * the same query, so the proxy is initialised before the session closes.
   *
   * @param id the id of the EcatImage to load
   * @return the EcatImage with originalImage initialised, or {@code null} if not found
   */
  EcatImage getWithOriginalImage(Long id);
}
