package com.researchspace.service;

import com.researchspace.model.FileProperty;
import com.researchspace.model.Signature;
import com.researchspace.model.User;
import com.researchspace.model.Witness;
import com.researchspace.model.dto.UserPublicInfo;
import com.researchspace.model.record.Record;
import com.researchspace.model.views.JournalEntry;
import com.researchspace.model.views.SigningResult;
import java.util.Optional;
import org.apache.shiro.authz.AuthorizationException;

public interface RecordSigningManager {
  /**
   * The publish event results in the generation of a record hash and the export of a PDF - for some
   * documents: eg ontology files, this uses far too much cpu to be allowed.
   *
   * @param recordId
   * @param signer
   * @param witnesses
   * @param statement
   * @return
   */
  SigningResult signRecordNoPublishEvent(
      Long recordId, User signer, String[] witnesses, String statement);

  /**
   * Main sign method: Set signed to the record and create/save Signature/Witness entities
   *
   * @param recordId
   * @param signer
   * @param witnesses
   * @param statement
   * @return
   */
  SigningResult signRecord(Long recordId, User signer, String[] witnesses, String statement);

  /**
   * Check if the record has been signed
   *
   * @param recordId
   * @return
   */
  boolean isSigned(Long recordId);

  /**
   * Check if the record has been witnessed
   *
   * @param recordId
   * @return
   */
  boolean isWitnessed(Long recordId);

  /**
   * Check if the user has permission.
   *
   * @param username
   * @param password
   * @return
   */
  boolean hasPermission(String username, String password);

  /**
   * Check if the user has permission.
   *
   * @param user
   * @param password
   * @return
   */
  boolean isReauthenticated(User user, String password);

  /**
   * Get a list of potential witnesses (PublicUserInfo)
   *
   * @param record
   * @param user
   * @return
   */
  UserPublicInfo[] getPotentialWitnesses(Record record, User user);

  /**
   * Get Signature entity related to record.
   *
   * @param recordId
   * @return
   */
  Signature getSignatureForRecord(Long recordId);

  /**
   * Update Witness entity after witnessing the record.
   *
   * @param recordId
   * @param witnessUser
   * @param option
   * @return
   */
  Witness updateWitness(Long recordId, User witnessUser, Boolean option, String declineMsg);

  /**
   * Check if a record can be signed.
   *
   * @param recordId
   * @param user
   * @return
   */
  boolean canSign(Long recordId, User user);

  /**
   * Check if a record can be witness.
   *
   * @param recordId
   * @param user
   * @return
   */
  boolean canWitness(Long recordId, User user);

  /**
   * @param entry
   * @param recordId
   * @param user
   */
  void updateSigningAttributes(JournalEntry entry, Long recordId, User user);

  /**
   * Gets a signed export file, if it exists
   *
   * @param signatureId
   * @param filePropertyId
   * @return An {@link Optional} {@link FileProperty} referring to an export. These FileProperties
   *     will not exist for documents signed prior to 1.41.5
   * @throws AuthorizationException if not permitted to view signed record.
   */
  Optional<FileProperty> getSignedExport(Long signatureId, User subject, Long filePropertyId);
}
