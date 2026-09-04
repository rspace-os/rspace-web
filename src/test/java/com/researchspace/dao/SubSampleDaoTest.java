package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.testutils.SpringTransactionalTest;
import org.hibernate.LockMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The operations endpoint serialises concurrent work on one origin by reading it through {@link
 * com.researchspace.dao.GenericDao#getForUpdate} (code review, finding 1). Two operations racing
 * the same subsample only queue behind each other if that read actually takes a row lock, so the
 * lock mode is asserted here for a subsample specifically; the shared behaviour of the inherited
 * implementation (re-reading under the lock, and not re-locking a row already held) is pinned once
 * in {@link SampleDaoTest}.
 */
public class SubSampleDaoTest extends SpringTransactionalTest {

  private @Autowired SubSampleDao subSampleDao;

  @Test
  public void getForUpdateHoldsAPessimisticWriteLock() {
    User user = createAndSaveRandomUser();
    Container workbench = containerDao.getWorkbenchForUser(user);
    Sample sample = recordFactory.createSample("subsample lock test", user);
    sample.getSubSamples().get(0).moveToNewParent(workbench);
    Long subSampleId = sampleDao.persistNewSample(sample).getSubSamples().get(0).getId();
    // flush the insert, then read it fresh, so the lock is the one this call takes rather than one
    // carried over from the session that created the row
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    SubSample locked = subSampleDao.getForUpdate(subSampleId);

    assertEquals(subSampleId, locked.getId());
    assertEquals(
        LockMode.PESSIMISTIC_WRITE, sessionFactory.getCurrentSession().getCurrentLockMode(locked));
  }
}
