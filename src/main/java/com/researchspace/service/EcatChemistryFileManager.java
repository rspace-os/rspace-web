package com.researchspace.service;

import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.User;

public interface EcatChemistryFileManager extends GenericManager<EcatChemistryFile, Long> {

  /**
   * Get an {@link EcatChemistryFile} by its id
   *
   * @param id the id of the {@link EcatChemistryFile}
   * @param user the user in session
   * @return the {@link EcatChemistryFile}
   */
  EcatChemistryFile get(Long id, User user);

  /**
   * Save an {@link EcatChemistryFile}
   *
   * @param chemistryFile the chemistry file to save
   * @param user the user in session
   * @return the saved chemistry file
   */
  EcatChemistryFile save(EcatChemistryFile chemistryFile, User user);

  /**
   * Delete an EcatChemistryFile by its id
   *
   * @param id the id of the chemistry file to delete
   * @param user the user in session
   */
  void remove(Long id, User user);
}
