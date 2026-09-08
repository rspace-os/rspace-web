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

  // The lock statement's actual scope (one row, one table, and blocking a second connection) can
  // only be observed across transactions; that lives in GenericDaoLockScopeIT. These tests pin the
  // loading behaviour of lockRowForUpdate, which must be an ordinary entity load.

  @Test
  public void lockRowForUpdateReturnsTheOrdinarilyLoadedEntity() {
    User user = createAndSaveRandomUser();
    Container workbench = containerDao.getWorkbenchForUser(user);
    Sample sample = recordFactory.createSample("sample lock test", user);
    sample.getSubSamples().get(0).moveToNewParent(workbench);
    Long sampleId = sampleDao.persistNewSample(sample).getId();
    // flush the insert, then read it fresh, so the load below is this call's own
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    Sample locked = sampleDao.lockRowForUpdate(sampleId);

    assertEquals(sampleId, locked.getId());
    // the same session.get path as every other read: no separate loading behaviour for locked reads
    assertEquals(locked, sampleDao.get(sampleId));
  }

  @Test
  public void lockRowForUpdateReturnsNullForUnknownId() {
    // The caller turns this into a 404; a locking read that threw instead would surface as a 500.
    assertNull(sampleDao.lockRowForUpdate(-1L));
  }

  @Test
  public void lockRowForUpdateIsRepeatableAndKeepsUnflushedChanges() {
    // The lock is taken by a scalar id query, never by refreshing or upgrading the entity, so a
    // second ask in the same transaction is harmless and the caller's pending changes survive.
    // (The old refresh-based implementation discarded them, which was a landmine on a shared DAO
    // method.) Two decrements of one subsample in a transaction reach this path.
    User user = createAndSaveRandomUser();
    Container workbench = containerDao.getWorkbenchForUser(user);
    Sample sample = recordFactory.createSample("sample relock test", user);
    sample.getSubSamples().get(0).moveToNewParent(workbench);
    Long sampleId = sampleDao.persistNewSample(sample).getId();
    sessionFactory.getCurrentSession().flush();
    sessionFactory.getCurrentSession().clear();

    Sample locked = sampleDao.lockRowForUpdate(sampleId);
    locked.setDescription("edited under the lock, not yet flushed");

    Sample relocked = sampleDao.lockRowForUpdate(sampleId);
    assertEquals(locked, relocked);
    assertEquals("edited under the lock, not yet flushed", relocked.getDescription());
  }
}
