package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.testutils.SpringTransactionalTest;
import org.hibernate.LockMode;
import org.junit.jupiter.api.Test;

public class SampleDaoTest extends SpringTransactionalTest {

  @Test
  public void createReadUpdateDeleteNewSample() {
    int initialCount = sampleDao.getAllDistinct().size();

    User user = createAndSaveRandomUser();
    Container workbench = containerDao.getWorkbenchForUser(user);
    Sample sample = recordFactory.createSample("dao test sample", user);
    sample.getSubSamples().get(0).moveToNewParent(workbench);
    SubSample newSubSample = recordFactory.createSubSample("test subSample 2", user, sample);
    newSubSample.moveToNewParent(workbench);
    sample.getSubSamples().add(newSubSample);
    Sample createdSample = sampleDao.persistNewSample(sample);
    assertEquals(initialCount + 1, sampleDao.getAllDistinct().size());

    Sample retrievedSample = sampleDao.get(createdSample.getId());
    assertEquals(createdSample, retrievedSample);
    assertNotNull(retrievedSample.getSubSamples());
    assertEquals(2, retrievedSample.getSubSamples().size());

    retrievedSample.setDescription("updated");
    Sample updatedSample = sampleDao.save(retrievedSample);
    assertEquals(createdSample, updatedSample);

    assertEquals(initialCount + 1, sampleDao.getAllDistinct().size());
    sampleDao.remove(updatedSample.getId());
    assertEquals(initialCount, sampleDao.getAllDistinct().size());
  }

  @Test
  public void entityNameExistsForUserIgnoresDeletedSamples() {
    User user = createAndSaveRandomUser();
    Container workbench = containerDao.getWorkbenchForUser(user);
    String name = "unique name for deletion test";
    Sample sample = recordFactory.createSample(name, user);
    sample.getSubSamples().get(0).moveToNewParent(workbench);
    Sample created = sampleDao.persistNewSample(sample);

    // an active sample with that name is a conflict...
    assertTrue(sampleDao.entityNameExistsForUser(name, user));

    // ...but once it is (soft-)deleted, the name is free to reuse (no suffix should be appended)
    sampleApiMgr.markSampleAsDeleted(created.getId(), false, user);
    assertFalse(sampleDao.entityNameExistsForUser(name, user));
  }

  @Test
  public void getForUpdateHoldsAPessimisticWriteLock() {
    User user = createAndSaveRandomUser();
    Container workbench = containerDao.getWorkbenchForUser(user);
    Sample sample = recordFactory.createSample("sample lock test", user);
    sample.getSubSamples().get(0).moveToNewParent(workbench);
    Long sampleId = sampleDao.persistNewSample(sample).getId();
    // flush the insert, then read it fresh, so the lock is the one this call takes rather than one
    // carried over from the session that created the row
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    Sample locked = sampleDao.getForUpdate(sampleId);

    assertEquals(sampleId, locked.getId());
    assertEquals(
        LockMode.PESSIMISTIC_WRITE, sessionFactory.getCurrentSession().getCurrentLockMode(locked));
  }

  @Test
  public void getForUpdateReturnsNullForUnknownId() {
    // The caller turns this into a 404; a locking read that threw instead would surface as a 500.
    assertNull(sampleDao.getForUpdate(-1L));
  }

  @Test
  public void getForUpdateDoesNotRelockARowThisTransactionAlreadyHolds() {
    // Hibernate upgrades a lock by re-reading the row and comparing its stored version to the
    // in-memory one, so re-locking an entity with unflushed changes fails as a stale-object error.
    // Two decrements of one subsample in a transaction reach this, so the second ask is a no-op.
    User user = createAndSaveRandomUser();
    Container workbench = containerDao.getWorkbenchForUser(user);
    Sample sample = recordFactory.createSample("sample relock test", user);
    sample.getSubSamples().get(0).moveToNewParent(workbench);
    Long sampleId = sampleDao.persistNewSample(sample).getId();
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    Sample locked = sampleDao.getForUpdate(sampleId);
    locked.setDescription("edited under the lock, not yet flushed");

    assertEquals(locked, sampleDao.getForUpdate(sampleId));
  }
}
