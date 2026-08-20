package com.researchspace.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.inventory.Container;
import com.researchspace.model.inventory.Sample;
import com.researchspace.model.inventory.SubSample;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Test;

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
}
