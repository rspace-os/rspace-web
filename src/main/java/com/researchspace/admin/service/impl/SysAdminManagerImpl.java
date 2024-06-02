package com.researchspace.admin.service.impl;

import com.researchspace.admin.service.IServerlogRetriever;
import com.researchspace.admin.service.SysAdminManager;
import com.researchspace.admin.service.UserPublicInfoForUsageInfo;
import com.researchspace.admin.service.UserUsageInfo;
import com.researchspace.core.util.ISearchResults;
import com.researchspace.core.util.SearchResultsImpl;
import com.researchspace.dao.*;
import com.researchspace.model.Community;
import com.researchspace.model.PaginationCriteria;
import com.researchspace.model.Role;
import com.researchspace.model.User;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service("sysAdminManager")
public class SysAdminManagerImpl extends AbstractSysadminMgr implements SysAdminManager {

  private @Autowired UserDao userDao;
  private @Autowired FileMetadataDao fileDao;
  private @Autowired IServerlogRetriever serverLogRetriever;
  private @Autowired CommunityDao commDao;
  private @Autowired RecordDao recordDao;

  @SuppressWarnings({"unchecked", "rawtypes"})
  @Override
  public ISearchResults<UserUsageInfo> getUserUsageInfo(
      User sysadmin, PaginationCriteria<User> pgCrit) {

    checkArgs(sysadmin, pgCrit);

    Community communityToLimitTo = null;
    ISearchResults<User> commUsers = null;
    if (!sysadmin.hasRole(Role.SYSTEM_ROLE)) {
      communityToLimitTo = getCommunityForAdmin(sysadmin, communityToLimitTo);
      if (communityToLimitTo == null) {
        return getEmptyUserUsageResults(pgCrit);
      }
      commUsers = getAllUsersInCommunity(communityToLimitTo);
      if (commUsers.getHits() == 0) {
        return getEmptyUserUsageResults(pgCrit);
      }
    }

    List<UserUsageInfo> uui = new ArrayList<>();
    if (pgCrit.isOrderByAnInstanceProperty()) {
      ISearchResults<User> userDBList;
      if (communityToLimitTo != null) {
        userDBList =
            userDao.listUsersInCommunity(communityToLimitTo.getId(), pgCrit); // RSPACE ADMIN
      } else {
        userDBList = userDao.searchUsers(pgCrit); // SYSADMIN
      }
      if (userDBList.getHits() > 0) {
        Map<String, DatabaseUsageByUserGroupByResult> fileUsage =
            fileDao.getTotalFileUsageForUsers(userDBList.getResults(), pgCrit);
        Map<String, DatabaseUsageByUserGroupByResult> recordCounts =
            recordDao.getTotalRecordsForUsers(userDBList.getResults(), pgCrit);
        for (User user : userDBList.getResults()) {
          UserUsageInfo info =
              setUserUsageInfoProperties(
                  user, fileUsage.get(user.getUsername()), recordCounts.get(user.getUsername()));
          uui.add(info);
        }
      }
      return createSearchResults(uui, userDBList);
    }

    if (SysAdminManager.ORDER_BY_FILE_USAGE.equals(pgCrit.getOrderBy())) {
      // we need to get ordered paginated list by fileusage, then get user information for each user
      Map<String, DatabaseUsageByUserGroupByResult> fileUsage;
      long totalUsersWithFiles;
      if (communityToLimitTo != null) {
        totalUsersWithFiles = commUsers.getTotalHits();
        fileUsage = fileDao.getTotalFileUsageForUsers(commUsers.getResults(), pgCrit);
      } else {
        totalUsersWithFiles = fileDao.getCountOfUsersWithFilesInFileSystem();
        fileUsage = fileDao.getTotalFileUsageForAllUsers(pgCrit);
      }

      Set<User> users = new HashSet<>();
      for (String uname : fileUsage.keySet()) {
        if (userDao.userExists(uname)) {
          users.add(userDao.getUserByUserName(uname));
        }
      }
      Map<String, DatabaseUsageByUserGroupByResult> recordCounts =
          recordDao.getTotalRecordsForUsers(users, pgCrit);
      for (Entry<String, DatabaseUsageByUserGroupByResult> entry : fileUsage.entrySet()) {
        if (userDao.userExists(entry.getKey())) {
          User user = userDao.getUserByUserName(entry.getKey());
          UserUsageInfo info =
              setUserUsageInfoProperties(
                  user, entry.getValue(), recordCounts.get(user.getUsername()));
          uui.add(info);
        }
      }
      return new SearchResultsImpl<>(
          uui, pgCrit.getPageNumber().intValue(), totalUsersWithFiles, pgCrit.getResultsPerPage());
    }

    if (SysAdminManager.ORDER_BY_RECORD_COUNT.equals(pgCrit.getOrderBy())) {
      Map<String, DatabaseUsageByUserGroupByResult> recordCount;
      long totalUsersWithRecords;
      if (communityToLimitTo != null) {
        recordCount = recordDao.getTotalRecordsForUsers(commUsers.getResults(), pgCrit);
        totalUsersWithRecords = commUsers.getTotalHits();
      } else {
        recordCount = recordDao.getTotalRecordsForUsers(pgCrit);
        totalUsersWithRecords = recordDao.getCountOfUsersWithRecords();
      }
      Map<String, User> users = new HashMap<>();
      for (String uname : recordCount.keySet()) {
        users.put(uname, userDao.getUserByUserName(uname));
      }
      Map<String, DatabaseUsageByUserGroupByResult> fileUsage =
          fileDao.getTotalFileUsageForUsers(users.values(), pgCrit);
      for (String uname : recordCount.keySet()) {
        User user = users.get(uname);
        UserUsageInfo info =
            setUserUsageInfoProperties(
                user, fileUsage.get(user.getUsername()), recordCount.get(uname));
        uui.add(info);
      }
      return new SearchResultsImpl<>(
          uui,
          pgCrit.getPageNumber().intValue(),
          totalUsersWithRecords,
          pgCrit.getResultsPerPage());
    }

    return null;
  }

  private ISearchResults<User> getAllUsersInCommunity(Community comm) {
    PaginationCriteria pgCritTemp = new PaginationCriteria();
    pgCritTemp.setGetAllResults();
    ISearchResults<User> commUsers = userDao.listUsersInCommunity(comm.getId(), pgCritTemp);
    return commUsers;
  }

  private SearchResultsImpl<UserUsageInfo> getEmptyUserUsageResults(
      PaginationCriteria<User> pgCrit) {
    return new SearchResultsImpl<>(Collections.emptyList(), pgCrit.getPageNumber().intValue(), 0L);
  }

  private UserUsageInfo setUserUsageInfoProperties(
      User user,
      DatabaseUsageByUserGroupByResult fileUsageCount,
      DatabaseUsageByUserGroupByResult recordCount) {
    return UserUsageInfo.builder()
        .creationDate(user.getCreationDate())
        .fileUsage(fileUsageCount != null ? fileUsageCount.getUsage() : 0)
        .recordCount(recordCount != null ? recordCount.getUsage() : 0)
        .userInfo(new UserPublicInfoForUsageInfo(user))
        .signupSource(user.getSignupSource().toString())
        .lastLogin(user.getLastLogin())
        .build();
  }

  private Community getCommunityForAdmin(User sysadmin, Community comm) {
    List<Community> comms = commDao.listCommunitiesForAdmin(sysadmin.getId());
    if (!comms.isEmpty()) {
      comm = comms.get(0);
    }
    return comm;
  }

  private SearchResultsImpl<UserUsageInfo> createSearchResults(
      List<UserUsageInfo> uui, ISearchResults<User> userDBList) {
    return new SearchResultsImpl<>(
        uui, userDBList.getPageNumber(), userDBList.getTotalHits(), userDBList.getHitsPerPage());
  }

  @Override
  public List<String> getLastNLinesLogs(int maxLines) throws IOException {
    return serverLogRetriever.retrieveLastNLogLines(maxLines);
  }
}
