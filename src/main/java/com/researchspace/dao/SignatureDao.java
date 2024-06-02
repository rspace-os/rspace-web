package com.researchspace.dao;

import com.researchspace.model.Signature;
import com.researchspace.model.User;
import com.researchspace.model.Witness;
import java.util.List;

public interface SignatureDao extends GenericDao<Signature, Long> {

  /**
   * Gets a signature for a given record id, or null
   *
   * @param recordId
   * @return The {@link Signature} object for this record, or <code>null</code> if not signed.
   */
  Signature getSignatureByRecordId(Long recordId);

  /**
   * Gets a witness object for a specified record and subject, or null if such a {@link Witness}
   * object doesn't exist.
   *
   * @param recordId
   * @param subject
   * @return A {@link Witness} object or <code>null</code>
   */
  Witness getWitnessforRecord(Long recordId, User subject);

  /**
   * Updates a witness object
   *
   * @param witness A non-<code>null</code> Witness
   * @return The updated Witness
   */
  Witness saveOrUpdateWitness(Witness witness);

  /**
   * Boolean query as to whether document is signed.
   *
   * @param recordId
   * @return
   */
  Boolean isSigned(Long recordId);

  /**
   * Gets open witness objects that have not yet been witnessed.
   *
   * @param witness
   * @return
   */
  List<Witness> getOpenWitnessesByWitnessUser(User witness);
}
