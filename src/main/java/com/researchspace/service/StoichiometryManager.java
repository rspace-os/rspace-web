package com.researchspace.service;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.Stoichiometry;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import java.io.IOException;

public interface StoichiometryManager extends GenericManager<Stoichiometry, Long> {

  Stoichiometry findByParentReactionId(Long parentReactionId);

  Stoichiometry createFromAnalysis(
      ElementalAnalysisDTO analysisDTO, RSChemElement parentReaction, User user) throws IOException;

  Stoichiometry update(StoichiometryUpdateDTO stoichiometryUpdateDTO, User user);

  StoichiometryDTO toDTO(Stoichiometry stoichiometry);

  StoichiometryDTO fromAnalysisDTO(ElementalAnalysisDTO analysisDTO);
}
