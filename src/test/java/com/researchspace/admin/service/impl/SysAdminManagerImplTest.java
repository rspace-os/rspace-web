package com.researchspace.admin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.researchspace.admin.service.UserUsageInfo;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.dao.DatabaseUsageByUserGroupByResult;
import com.researchspace.dao.FileMetadataDao;
import com.researchspace.dao.RecordDao;
import com.researchspace.dao.UserDao;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import com.researchspace.model.sort.UserSort;
import com.researchspace.testutils.TestFactory;
import java.util.LinkedHashMap;
import java.util.List;
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
  public void fileUsageSortKeepsOrderAndFiguresOfEachUser() {
    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    pgCrit.setOrderBy(UserSort.FILE_USAGE.key());
    Map<String, DatabaseUsageByUserGroupByResult> fileUsage =
        usageOf(Map.entry("B", 300), Map.entry("a", 200), Map.entry("ghost", 100));
    User a = TestFactory.createAnyUser("a");
    User b = TestFactory.createAnyUser("b");
    when(fileDao.getCountOfUsersWithFilesInFileSystem()).thenReturn(3L);
    when(fileDao.getTotalFileUsageForAllUsers(pgCrit)).thenReturn(fileUsage);
    when(userDao.getUsersByUsernames(fileUsage.keySet())).thenReturn(List.of(a, b));
    when(recordDao.getTotalRecordsForUsers(any(), any()))
        .thenReturn(usageOf(Map.entry("a", 2), Map.entry("b", 3)));

    List<UserUsageInfo> rows = sysAdminManager.getUserUsageInfo(sysadmin, pgCrit).getResults();

    assertEquals(List.of("b:300:3", "a:200:2"), rows.stream().map(this::describe).toList());
  }

  @Test
  public void recordCountSortKeepsOrderAndFiguresOfEachUser() {
    PaginationCriteria<User> pgCrit = PaginationCriteria.createDefaultForClass(User.class);
    pgCrit.setOrderBy(UserSort.RECORD_COUNT.key());
    Map<String, DatabaseUsageByUserGroupByResult> recordCount =
        usageOf(Map.entry("a", 3), Map.entry("ghost", 2), Map.entry("B", 1));
    User a = TestFactory.createAnyUser("a");
    User b = TestFactory.createAnyUser("b");
    when(recordDao.getCountOfUsersWithRecords()).thenReturn(3L);
    when(recordDao.getTotalRecordsForUsers(pgCrit)).thenReturn(recordCount);
    when(userDao.getUsersByUsernames(recordCount.keySet())).thenReturn(List.of(b, a));
    when(fileDao.getTotalFileUsageForUsers(any(), any()))
        .thenReturn(usageOf(Map.entry("a", 200), Map.entry("b", 300)));

    List<UserUsageInfo> rows = sysAdminManager.getUserUsageInfo(sysadmin, pgCrit).getResults();

    assertEquals(List.of("a:200:3", "b:300:1"), rows.stream().map(this::describe).toList());
  }

  @SafeVarargs
  private Map<String, DatabaseUsageByUserGroupByResult> usageOf(
      Map.Entry<String, Integer>... usages) {
    Map<String, DatabaseUsageByUserGroupByResult> byUsername = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> usage : usages) {
      byUsername.put(
          usage.getKey(), new DatabaseUsageByUserGroupByResult(usage.getKey(), usage.getValue()));
    }
    return byUsername;
  }

  private String describe(UserUsageInfo row) {
    return row.getUserInfo().getUsername() + ":" + row.getFileUsage() + ":" + row.getRecordCount();
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
