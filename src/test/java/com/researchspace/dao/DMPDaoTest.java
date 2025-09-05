package com.researchspace.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
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
    DmpDto dmpDto = createDMP();
    DMPUser dmpUser = saveDMPUser(dmpDto, anyUser);
    assertNotNull(dmpUser.getTimestamp());
    assertNotNull(dmpUser.getId());
  }

  @Test
  public void findDMPsForUser() {
    DmpDto dmpDto = createDMP();
    DMPUser dmpUser = saveDMPUser(dmpDto, anyUser);
    assertEquals(1, dmpDao.findDMPsForUser(dmpUser.getUser()).size());
    User another = createAndSaveUserIfNotExists("another");
    assertEquals(0, dmpDao.findDMPsForUser(another).size());
  }

  @Test
  public void findDMPByDmpId() {
    DmpDto dmpDto = createDMP();
    DMPUser dmpUser = saveDMPUser(dmpDto, anyUser);
    assertTrue(dmpDao.findByDmpId(dmpDto.getDmpId(), anyUser).isPresent());
    assertFalse(dmpDao.findByDmpId("xxxx", anyUser).isPresent());
  }

  private DMPUser saveDMPUser(DmpDto dmpDto, User user) {
    DMPUser dmpUser = new DMPUser(user, dmpDto);
    dmpUser = dmpDao.save(dmpUser);
    return dmpUser;
  }

  private DmpDto createDMP() {
    DmpDto dmpDto = new DmpDto("id", "title");
    return dmpDto;
  }
}
