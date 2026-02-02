package com.researchspace.service;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.record.Record;
import com.researchspace.model.stoichiometry.Stoichiometry;
import java.io.IOException;
import java.util.Optional;

public interface StoichiometryManager extends GenericManager<Stoichiometry, Long> {

  Optional<Stoichiometry> findByParentReactionId(Long parentReactionId);

  Optional<Stoichiometry> findByRecordId(Long recordId);

  Stoichiometry createFromAnalysis(
      ElementalAnalysisDTO analysisDTO, RSChemElement parentReaction, Record record, User user)
      throws IOException;

  Stoichiometry createEmpty(Record record, User user);

  Stoichiometry update(StoichiometryUpdateDTO stoichiometryUpdateDTO, User user);

  Stoichiometry copy(Long sourceParentReactionId, RSChemElement newParentReaction, User user);

  AuditedEntity<Stoichiometry> getRevision(long id, Long revisionId, User user);

  Stoichiometry createNewFromDataWithoutInventoryLinks(
      StoichiometryDTO stoichiometryDTO, RSChemElement chemElement, User user);
}
