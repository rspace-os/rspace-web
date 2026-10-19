package com.researchspace.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.researchspace.model.User;
import com.researchspace.model.dmps.DMPUser;
import com.researchspace.model.dmps.DmpDto;
import com.researchspace.testutils.SpringTransactionalTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class DMPDaoTest extends SpringTransactionalTest {

  private @Autowired DMPDao dmpDao;
  private User anyUser;

  @BeforeEach
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
    assertThat(dmpDao.findDMPsForUser(dmpUser.getUser())).hasSize(1);
    User another = createAndSaveUserIfNotExists("another");
    assertThat(dmpDao.findDMPsForUser(another)).isEmpty();
  }

  @Test
  public void findDMPByDmpId() {
    DmpDto dmpDto = createDMP();
    DMPUser dmpUser = saveDMPUser(dmpDto, anyUser);
    assertThat(dmpDao.findByDmpId(dmpDto.getDmpId(), anyUser)).isPresent();
    assertThat(dmpDao.findByDmpId("xxxx", anyUser)).isNotPresent();
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
