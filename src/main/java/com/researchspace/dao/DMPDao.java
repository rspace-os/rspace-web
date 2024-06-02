package com.researchspace.dao;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import java.util.List;
import java.util.Optional;

/** Crud methods for persisted DMP plans */
public interface DMPDao extends GenericDao<DMPUser, Long> {

  List<DMPUser> findDMPsForUser(User subject);

  /**
   * Get DMPs by the PDF attachment
   *
   * @param subject
   * @param ecatDocument_id
   * @return
   */
  List<DMPUser> findDMPsByPDF(User subject, Long ecatDocument_id);

  Optional<DMPUser> findByDmpId(String dmpId, User user);
}
