package com.researchspace.dao;

// import org.compass.core.CompassTemplate;
// import org.compass.gps.CompassGps;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.researchspace.model.User;
import com.researchspace.model.record.FormState;
import com.researchspace.model.record.FormUsage;
import com.researchspace.model.record.RSForm;
import com.researchspace.model.record.TestFactory;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class FormUsageDaoTest extends BaseDaoTestCase {

  private @Autowired FormDao dao;
  private @Autowired FormUsageDao formUsageDao;

  private RSForm[] forms = null;
  private User user;

  @Before
  public void setUp() {
    user = createAndSaveUserIfNotExists("formUsageUsr");
    setUpDBWith4Forms();
  }

  @Test
  public void testBasicPersistence() throws InterruptedException {
    int b4 = formUsageDao.getAll().size();
    FormUsage formUsage = new FormUsage(user, forms[0]);
    Thread.sleep(5);
    formUsageDao.save(formUsage);
    assertEquals(b4 + 1, formUsageDao.getAll().size());
    FormUsage formUsage2 = formUsageDao.get(formUsage.getId());
    assertNotNull(formUsage2.getFormStableID());
    assertNotNull(formUsage2.getUser());
  }

  @Test
  public void testGetMostRecentForm() throws Exception {
    FormUsage formUsage = new FormUsage(user, forms[0]);
    formUsageDao.save(formUsage);
    Thread.sleep(10);
    FormUsage formUsage2 = new FormUsage(user, forms[1]);

    formUsageDao.save(formUsage2);
    assertEquals(formUsage2, formUsageDao.getMostRecentlyUsedFormForUser(user).get());
  }

  void setUpDBWith4Forms() {
    forms = new RSForm[4];
    int indx = 0;
    for (String name : new String[] {"t0", "t1", "t2", "t3"}) {
      RSForm t = TestFactory.createAnyForm(name);
      t.setPublishingState(FormState.PUBLISHED);
      t.setOwner(user);
      dao.save(t);
      forms[indx++] = t;
    }
  }
}
