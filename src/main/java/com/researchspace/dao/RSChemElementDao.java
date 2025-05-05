package com.researchspace.dao;

import com.researchspace.model.ChemElementsFormat;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.dtos.chemistry.ChemicalSearchResultsDTO;
import java.util.List;

public interface RSChemElementDao extends GenericDao<RSChemElement, Long> {

  List<RSChemElement> getAllChemElementsFromField(Long fieldId);

  /**
   * Altered in 1.55 from String->integer
   * @param rawResults a <code>ChemSearchResults</code of cd_id FK references
   * @return
   */
  List<RSChemElement> getChemElementsForChemIds(ChemicalSearchResultsDTO rawResults);

  /**
   * Get the List of {@link RSChemElement} that matches the given EcatChemistryFile Id
   *
   * @param ecatChemFileId the ecat chemistry file id to match
   * @return the list of matching {@link RSChemElement}
   */
  List<RSChemElement> getChemElementsFromChemFileId(Long ecatChemFileId);

  /**
   * Gets an the RSChemElement of a chemistry file which hasnt been inserted into a document yet,
   * i.e has a null field id
   *
   * @param ecatChemFileId the id of the chemistry file
   * @return the RSChemElement with the chem file id and a null feild id
   */
  RSChemElement getChemElementFromChemistryGalleryFile(Long ecatChemFileId);

  /**
   * Get the list of id-smilesString pairs for all chemical items saved on database.
   *
   * @return list of 2-element arrays, each holding id of a chem element, and its smilesString
   */
  List<Object[]> getAllIdAndSmilesStringPairs();

  List<RSChemElement> getAllChemicalsWithFormat(ChemElementsFormat format);
}
