package com.researchspace.service.impl;

import com.researchspace.dao.DMPDao;
import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPSource;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.service.DMPManager;
import com.researchspace.service.dmp.DmpSourceDetector;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** For CRUD methods on saved DMP Plans */
@Service("dmpManager")
public class DMPManagerImpl extends GenericManagerImpl<DMPUser, Long> implements DMPManager {
  private DMPDao dmpDao;
  private DmpSourceDetector sourceDetector;
  private TransactionTemplate newTransaction;

  public DMPManagerImpl(@Autowired DMPDao dmpDao) {
    super(dmpDao);
    this.dmpDao = dmpDao;
  }

  @Autowired
  void setSourceDetector(DmpSourceDetector sourceDetector) {
    this.sourceDetector = sourceDetector;
  }

  @Autowired
  void setTransactionManager(PlatformTransactionManager transactionManager) {
    this.newTransaction = new TransactionTemplate(transactionManager);
    // its own transaction, so a failed save cannot fail the caller's read
    this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public List<DMPUser> findDMPsForUser(User subject) {
    List<DMPUser> plans = dmpDao.findDMPsForUser(subject);
    for (DMPUser plan : plans) {
      if (plan.getSource() == null || plan.getSource() == DMPSource.UNKNOWN) {
        adoptDetectedSource(plan);
      }
    }
    return plans;
  }

  private void adoptDetectedSource(DMPUser plan) {
    DMPSource detected = sourceDetector.detect(plan);
    if (detected == DMPSource.UNKNOWN) {
      return;
    }
    try {
      // the read's own transaction must not write the row as well
      dmpDao.detach(plan);
      plan.adoptSourceIfUnknown(detected);
      newTransaction.executeWithoutResult(
          status -> dmpDao.get(plan.getId()).adoptSourceIfUnknown(detected));
    } catch (RuntimeException e) {
      log.warn("Couldn't save the detected source {} of plan {}", detected, plan.getId(), e);
    }
  }

  @Override
  public Optional<DMPUser> findByDmpId(String dmpId, User user) {
    return dmpDao.findByDmpId(dmpId, user);
  }

  @Override
  public List<DMPUser> findDMPsByFile(User subject, Long ecatDocument_id) {
    return dmpDao.findDMPsByFile(subject, ecatDocument_id);
  }
}
