package com.researchspace.service;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import java.util.List;
import java.util.Optional;

/** For CRUD methods on saved DMP Plans */
public interface DMPManager extends GenericManager<DMPUser, Long> {

  /**
   * Finds all associated DMPs for a given user. A plan with no recorded source has its stored file
   * read to work out its platform; a platform found this way is saved in a separate transaction.
   * Neither step throws.
   *
   * @param subject the plans' owner
   * @return the user's plans
   */
  List<DMPUser> findDMPsForUser(User subject);

  /**
   * Finds a DMPUsers for a given DMP ID aand user
   *
   * @param dmpId a DMP id (its external id, not RSpace internal database id)
   * @param user
   * @return
   */
  Optional<DMPUser> findByDmpId(String dmpId, User user);

  List<DMPUser> findDMPsByFile(User subject, Long ecatDocument_id);
}
