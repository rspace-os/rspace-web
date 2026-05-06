package com.researchspace.service;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.audit.AuditedEntity;
import com.researchspace.model.dtos.chemistry.ElementalAnalysisDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.record.Record;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;
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

  /**
   * Recreates a reaction-less stoichiometry (no parent {@link RSChemElement}) from an archive DTO.
   *
   * <p>Mirrors the DB shape produced by {@link #createEmpty(Record, User)} followed by user edits:
   * {@code parentReaction} is {@code null}; for each molecule a fresh {@link RSChemElement} is
   * created from the DTO's SMILES (the {@code rs_chem_id} column on {@link StoichiometryMolecule}
   * is NOT NULL). Used by the archive importer for stoichiometries with {@code parentReactionId ==
   * null}.
   *
   * <p>Inventory links on the DTO molecules are ignored (the exporter strips them).
   */
  Stoichiometry createReactionlessFromArchive(
      StoichiometryDTO stoichiometryDTO, Record record, User user);
}
