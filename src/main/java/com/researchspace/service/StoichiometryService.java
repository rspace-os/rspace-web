package com.researchspace.service;

import com.researchspace.model.RSChemElement;
import com.researchspace.model.User;
import com.researchspace.model.dtos.chemistry.StoichiometryDTO;
import com.researchspace.model.dtos.chemistry.StoichiometryUpdateDTO;
import com.researchspace.model.record.Record;
import com.researchspace.model.stoichiometry.Stoichiometry;
import com.researchspace.model.stoichiometry.StoichiometryMolecule;

public interface StoichiometryService {

  StoichiometryDTO getById(long stoichiometryId, Long revision, User user);

  Stoichiometry createFromReaction(long recordId, long chemId, User user);

  Stoichiometry createEmpty(long recordId, User user);

  Stoichiometry update(
      long stoichiometryId, StoichiometryUpdateDTO stoichiometryUpdateDTO, User user);

  void delete(long stoichiometryId, User user);

  StoichiometryMolecule getMoleculeInfo(String smiles);

  Stoichiometry createNewFromDataWithoutInventoryLinks(
      StoichiometryDTO stoichiometryDTO, RSChemElement chemElement, User user);

  /**
   * Recreates a reaction-less stoichiometry (no parent reaction) from an archive DTO.
   *
   * <p>Used only by the XML archive importer. Mirrors the DB shape user-created reaction-less
   * stoichiometries produce: {@code parentReaction = null}; each {@link StoichiometryMolecule} gets
   * a fresh {@link RSChemElement} created from the DTO's SMILES (the {@code rs_chem_id} column is
   * NOT NULL).
   *
   * <p>Intentionally skips the permission check applied by other entry points; the archive importer
   * is the authoritative authorisation gate for this code path.
   */
  Stoichiometry createReactionlessFromArchive(
      StoichiometryDTO stoichiometryDTO, Record record, User user);
}
