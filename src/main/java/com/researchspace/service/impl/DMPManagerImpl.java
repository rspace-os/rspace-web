package com.researchspace.service.impl;

import com.researchspace.dao.DMPDao;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.service.DMPManager;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** For CRUD methods on saved DMP Plans */
@Service("dmpManager")
public class DMPManagerImpl extends GenericManagerImpl<DMPUser, Long> implements DMPManager {
  private DMPDao dmpDao;

  public DMPManagerImpl(@Autowired DMPDao dmpDao) {
    super(dmpDao);
    this.dmpDao = dmpDao;
  }

  @Override
  public List<DMPUser> findDMPsForUser(User subject) {
    return dmpDao.findDMPsForUser(subject);
  }

  @Override
  public Optional<DMPUser> findByDmpId(String dmpId, User user) {
    return dmpDao.findByDmpId(dmpId, user);
  }

  @Override
  public List<DMPUser> findDMPsByPDF(User subject, Long ecatDocument_id) {
    return dmpDao.findDMPsByPDF(subject, ecatDocument_id);
  }
}
