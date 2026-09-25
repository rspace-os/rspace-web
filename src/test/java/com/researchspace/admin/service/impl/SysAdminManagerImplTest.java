package com.researchspace.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.admin.service.UserUsageInfo;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.sort.UserSort;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * The usage listing dispatches on the {@code fileUsage} / {@code recordCount} sort keys, so these
 * tests pin that each key selects its aggregate query branch.
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
    pgCrit.setOrderBy(UserSort.FILE_USAGE.key());
    assertEquals(UserSort.FILE_USAGE.key(), pgCrit.getOrderBy());

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
    pgCrit.setOrderBy(UserSort.RECORD_COUNT.key());
    assertEquals(UserSort.RECORD_COUNT.key(), pgCrit.getOrderBy());

    when(recordDao.getTotalRecordsForUsers(pgCrit)).thenReturn(Map.of());
    when(recordDao.getCountOfUsersWithRecords()).thenReturn(0L);
    when(fileDao.getTotalFileUsageForUsers(any(), any())).thenReturn(Map.of());

    ISearchResults<UserUsageInfo> results = sysAdminManager.getUserUsageInfo(sysadmin, pgCrit);

    assertNotNull(results);
    verify(recordDao).getCountOfUsersWithRecords();
  }
}
