package com.researchspace.service;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.Stoichiometry;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import java.io.IOException;

/**
 * Manager interface for Stoichiometry entities. Provides methods for CRUD operations on
 * stoichiometry tables.
 */
public interface StoichiometryManager extends GenericManager<Stoichiometry, Long> {

  /**
   * Find a stoichiometry by parent reaction ID.
   *
   * @param parentReactionId the ID of the parent reaction
   * @return the stoichiometry for the parent reaction, or null if none exists
   */
  Stoichiometry findByParentReactionId(Long parentReactionId);

  /**
   * Create a new stoichiometry from an ElementalAnalysisDTO.
   *
   * @param analysisDTO the elemental analysis DTO containing stoichiometry information
   * @param parentReaction the parent reaction
   * @param user the user creating the stoichiometry
   * @return the created stoichiometry
   */
  Stoichiometry createFromAnalysis(
      ElementalAnalysisDTO analysisDTO, RSChemElement parentReaction, User user) throws IOException;

  /**
   * Update a stoichiometry from a StoichiometryDTO.
   *
   * @param stoichiometryId the ID of the stoichiometry to update
   * @param stoichiometryDTO the DTO containing the updated stoichiometry information
   * @param user the user updating the stoichiometry
   * @return the updated stoichiometry
   */
  Stoichiometry update(Long stoichiometryId, StoichiometryDTO stoichiometryDTO, User user);

  /**
   * Convert a Stoichiometry entity to a StoichiometryDTO.
   *
   * @param stoichiometry the stoichiometry entity to convert
   * @return the converted DTO
   */
  StoichiometryDTO toDTO(Stoichiometry stoichiometry);

  /**
   * Convert an ElementalAnalysisDTO to a StoichiometryDTO.
   *
   * @param analysisDTO the elemental analysis DTO to convert
   * @return the converted DTO
   */
  StoichiometryDTO fromAnalysisDTO(ElementalAnalysisDTO analysisDTO);
}
