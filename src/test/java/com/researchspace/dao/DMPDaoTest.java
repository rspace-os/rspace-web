package com.researchspace.dao;

import static org.junit.Assert.*;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMP;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DMPDaoTest extends SpringTransactionalTest {

  private @Autowired DMPDao dmpDao;
  private User anyUser;

  @Before
  public void before() throws Exception {
    super.setUp();
    anyUser = createInitAndLoginAnyUser();
  }

  @Test
  public void saveDMPUser() {
    DMP dmp = createDMP();
    DMPUser dmpUser = saveDMPUser(dmp, anyUser);
    assertNotNull(dmpUser.getTimestamp());
    assertNotNull(dmpUser.getId());
  }

  @Test
  public void findDMPsForUser() {
    DMP dmp = createDMP();
    DMPUser dmpUser = saveDMPUser(dmp, anyUser);
    assertEquals(1, dmpDao.findDMPsForUser(dmpUser.getUser()).size());
    User another = createAndSaveUserIfNotExists("another");
    assertEquals(0, dmpDao.findDMPsForUser(another).size());
  }

  @Test
  public void findDMPByDmpId() {
    DMP dmp = createDMP();
    DMPUser dmpUser = saveDMPUser(dmp, anyUser);
    assertTrue(dmpDao.findByDmpId(dmp.getDmpId(), anyUser).isPresent());
    assertFalse(dmpDao.findByDmpId("xxxx", anyUser).isPresent());
  }

  private DMPUser saveDMPUser(DMP dmp, User user) {
    DMPUser dmpUser = new DMPUser(user, dmp);
    dmpUser = dmpDao.save(dmpUser);
    return dmpUser;
  }

  private DMP createDMP() {
    DMP dmp = new DMP("id", "title");
    return dmp;
  }
}
