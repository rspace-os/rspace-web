package com.researchspace.service;

import com.researchspace.model.ChemSearchedItem;
import com.researchspace.model.EcatChemistryFile;
import com.researchspace.model.FileProperty;
import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ChemElementDataDto;
import com.researchspace.model.dtos.chemistry.ChemicalDataDTO;
import com.researchspace.model.dtos.chemistry.ChemicalExportFormat;
import com.researchspace.model.dtos.chemistry.ChemicalImageDTO;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import javax.security.sasl.AuthenticationException;

public interface RSChemElementManager extends GenericManager<RSChemElement, Long> {

  /**
   * Gets this chem element by id or <code>null</code> if could not be found.
   *
   * @param id
   * @param user
   * @return
   * @throws {@link AuthenticationException} if resource access not authorized
   */
  RSChemElement get(long id, User user);

  RSChemElement getRevision(long id, Long revisionId, User user);

  /**
   * @param rsChemElement
   * @param user
   * @return the saved {@link RSChemElement}
   * @throws IOException, {@link AuthenticationException}
   * @throws {@link AuthenticationException} if resource access not authorized
   */
  RSChemElement save(RSChemElement rsChemElement, User user) throws IOException;

  void delete(long id, User user) throws Exception;

  List<ChemSearchedItem> search(String chemQuery, String searchType, User subject);

  /**
   * Gets chemical info about a particular RSChemElement
   *
   * @param chemElement
   * @return an ElementalAnalysis containing metadata about a molecule or reaction
   */
  Optional<ElementalAnalysisDTO> getInfo(RSChemElement chemElement);

  /**
   * Method used to save a Chem structured Element in the database.
   *
   * @param chemicalDataDTO chemicalData from UI
   * @return RSChemElement
   * @throws IOException
   * @throws AuthenticationException if no write permission on the document containing this field
   */
  RSChemElement saveChemElement(ChemicalDataDTO chemicalDataDTO, User subject) throws IOException;

  /**
   * Saves image preview of Chem element into filestore.
   *
   * @param chemicalImageDTO chemicalImage from UI
   * @throws IOException in case of problems with saving the file
   * @throws AuthenticationException if no write permission on the document containing this field
   */
  RSChemElement saveChemImage(ChemicalImageDTO chemicalImageDTO, User subject) throws IOException;

  /**
   * Saves a pgn representation of a chem element to a file property
   *
   * @param rsChemElement the rsChemElement to save
   * @param imageIS the input stream of the image to save to a file property
   * @param user the currently logged-in user
   * @return the updated chem element after image is saved
   * @throws IOException
   */
  RSChemElement saveChemImagePng(RSChemElement rsChemElement, InputStream imageIS, User user)
      throws IOException;

  /**
   * (TODO Method does not currently work)This method saves a new svg representation of the
   * RSChemElement to a file property
   *
   * @param rsChemElement the rsChemElement to generate the svg for
   * @param imageIS the input stream of the svg
   * @param user the currently logged-in user
   * @return the updated chem element after image is saved
   * @throws IOException
   */
  RSChemElement saveChemImageSvg(RSChemElement rsChemElement, InputStream imageIS, User user)
      throws IOException;

  /**
   * Method used to save a Chem structured Element in the database.
   *
   * @param chemElementDataDto chemistry data from UI
   * @return RSChemElement
   * @throws IOException, ChemistryModuleException
   * @throws AuthenticationException if no write permission on the document containing this field
   */
  RSChemElement createChemElement(ChemElementDataDto chemElementDataDto, User subject)
      throws IOException;

  /**
   * Generate an RSChemElement from an EcatChemistryFile
   *
   * @param chemistryFile the chemistry file
   * @param fieldId the field id
   * @param user the user
   * @return the generated RSChemElement
   */
  RSChemElement generateRsChemElementFromChemistryFile(
      EcatChemistryFile chemistryFile, long fieldId, User user) throws IOException;

  /**
   * Update {@link RSChemElement} linked the chemistry file being updated
   *
   * @param newChemistryFile the new version of the chemistry file
   * @param newFileProperty
   * @param user
   * @throws IOException
   */
  void updateAssociatedChemElements(
      EcatChemistryFile newChemistryFile, FileProperty newFileProperty, User user)
      throws IOException;

  /**
   * This method updates a chem element following a request to upload a new version of a chemistry
   * file
   *
   * @param newChemistryFile the updated chemistry file
   * @param toUpdate the {@link RSChemElement} to update
   * @param user the currently logged-in user
   * @return the updated RSChemElement
   * @throws IOException
   */
  RSChemElement updateChemElement(
      EcatChemistryFile newChemistryFile, RSChemElement toUpdate, User user) throws IOException;

  /**
   * Update the images of the {@link RSChemElement} after new version has been uploaded.
   *
   * @param ecatChemistryFile the new version of the chemistry file
   * @param rsChemElements the chemical elements to update
   * @param chemicalExportFormat format object representing the new image to produce
   * @param user the currently logged in user
   * @return the list of updated chemical elements with a new image.
   * @throws IOException
   */
  List<RSChemElement> updateChemImages(
      EcatChemistryFile ecatChemistryFile,
      List<RSChemElement> rsChemElements,
      ChemicalExportFormat chemicalExportFormat,
      User user)
      throws IOException;

  /**
   * Gets a list of {@link RSChemElement} which has a matching {@link EcatChemistryFile} id
   *
   * @param ecatChemFileId the {@link EcatChemistryFile} id to find matching {@link RSChemElement}s
   *     for
   * @return the list of associated {@link RSChemElement}
   */
  List<RSChemElement> getRSChemElementsLinkedToFile(Long ecatChemFileId, User user);

  /**
   * Gets a list of {@link RSChemElement} which has a matching {@link
   * com.researchspace.model.field.Field} id
   *
   * @param fieldId the field id to retireve the list of chem elements for
   * @return the list of associated {@link RSChemElement}
   */
  List<RSChemElement> getAllRSChemElementsByField(Long fieldId, User user);

  void generateRsChemElementForNewlyUploadedChemistryFile(EcatChemistryFile media, User subject)
      throws IOException;

  /**
   * Get a list of the file extensions supported by the chemistry provider
   *
   * @return a list of strings of the supported file extensions
   */
  List<String> getSupportedTypes();
}
