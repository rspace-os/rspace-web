package com.researchspace.service;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.stoichiometry.Stoichiometry;
import java.io.IOException;
import java.util.Optional;

public interface StoichiometryManager extends GenericManager<Stoichiometry, Long> {

  Optional<Stoichiometry> findByParentReactionId(Long parentReactionId);

  Stoichiometry createFromAnalysis(
      ElementalAnalysisDTO analysisDTO, RSChemElement parentReaction, User user) throws IOException;

  Stoichiometry createEmpty(RSChemElement parentReaction, User user);

  Stoichiometry update(StoichiometryUpdateDTO stoichiometryUpdateDTO, User user);

  Stoichiometry copy(Long sourceParentReactionId, RSChemElement newParentReaction, User user);

  AuditedEntity<Stoichiometry> getRevision(long id, Long revisionId, User user);
}
