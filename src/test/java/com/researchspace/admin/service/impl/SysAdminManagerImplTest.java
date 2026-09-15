package com.researchspace.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.admin.service.SysAdminManager;
import com.researchspace.admin.service.UserUsageInfo;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * The usage listing dispatches on the {@code fileUsage()} / {@code recordCount()} virtual sort
 * tokens, so these tests pin that the tokens survive {@code setOrderBy} and select their query
 * branch instead of falling through to the null return.
 */
public class SysAdminManagerImplTest {

  @Mock private UserDao userDao;
  @Mock private FileMetadataDao fileDao;
  @Mock private RecordDao recordDao;

  @InjectMocks private SysAdminManagerImpl sysAdminManager;

  private User sysadmin;

  @BeforeEach
  public void setUp() {
    MockitoAnnotations.openMocks(this);
    sysadmin = new User("sysadmin");
    sysadmin.addRole(Role.SYSTEM_ROLE);
  }

  @Test
  public void fileUsageOrderBySelectsFileUsageBranch() {
    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    pgCrit.setOrderBy(SysAdminManager.ORDER_BY_FILE_USAGE);
    assertEquals(SysAdminManager.ORDER_BY_FILE_USAGE, pgCrit.getOrderBy());

    when(fileDao.getCountOfUsersWithFilesInFileSystem()).thenReturn(0L);
    when(fileDao.getTotalFileUsageForAllUsers(pgCrit)).thenReturn(Map.of());
    when(recordDao.getTotalRecordsForUsers(any(), any())).thenReturn(Map.of());

    ISearchResults<UserUsageInfo> results = sysAdminManager.getUserUsageInfo(sysadmin, pgCrit);

    assertNotNull(results);
    verify(fileDao).getTotalFileUsageForAllUsers(pgCrit);
  }

  @Test
  public void recordCountOrderBySelectsRecordCountBranch() {
    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    pgCrit.setOrderBy(SysAdminManager.ORDER_BY_RECORD_COUNT);
    assertEquals(SysAdminManager.ORDER_BY_RECORD_COUNT, pgCrit.getOrderBy());

    when(recordDao.getTotalRecordsForUsers(pgCrit)).thenReturn(Map.of());
    when(recordDao.getCountOfUsersWithRecords()).thenReturn(0L);
    when(fileDao.getTotalFileUsageForUsers(any(), any())).thenReturn(Map.of());

    ISearchResults<UserUsageInfo> results = sysAdminManager.getUserUsageInfo(sysadmin, pgCrit);

    assertNotNull(results);
    verify(recordDao).getCountOfUsersWithRecords();
  }
}
